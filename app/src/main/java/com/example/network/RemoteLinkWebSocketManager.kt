package com.example.network

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.database.RemoteLinkDatabase
import com.example.database.RemoteLinkRepository
import com.example.model.ConnectionStatus
import com.example.model.FileTransferItem
import com.example.model.IncomingFileOffer
import com.example.model.MirroredNotificationItem
import com.example.model.ServerConfig
import com.example.model.TransferStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

object RemoteLinkWebSocketManager {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var currentWebSocket: WebSocket? = null
    private var activeConfig: ServerConfig? = null
    private var repository: RemoteLinkRepository? = null

    // State flows
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _activeServerUrl = MutableStateFlow("")
    val activeServerUrl: StateFlow<String> = _activeServerUrl.asStateFlow()

    private val _activePairingCode = MutableStateFlow("")
    val activePairingCode: StateFlow<String> = _activePairingCode.asStateFlow()

    private val _incomingOffer = MutableStateFlow<IncomingFileOffer?>(null)
    val incomingOffer: StateFlow<IncomingFileOffer?> = _incomingOffer.asStateFlow()

    private val _transfers = MutableStateFlow<List<FileTransferItem>>(emptyList())
    val transfers: StateFlow<List<FileTransferItem>> = _transfers.asStateFlow()

    private val _mirroredNotifications = MutableStateFlow<List<MirroredNotificationItem>>(emptyList())
    val mirroredNotifications: StateFlow<List<MirroredNotificationItem>> = _mirroredNotifications.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    // Active incoming receiving state
    private var currentIncomingTransfer: FileTransferItem? = null
    private var currentIncomingStream: OutputStream? = null
    private var currentIncomingUri: Uri? = null
    private var currentIncomingFile: File? = null

    private const val CHUNK_SIZE = 64 * 1024

    fun initDatabase(context: Context) {
        if (repository == null) {
            val db = RemoteLinkDatabase.getDatabase(context)
            val repo = RemoteLinkRepository(db.fileTransferDao(), db.notificationLogDao())
            repository = repo

            // Load previously saved transfers from local Room database
            scope.launch {
                repo.allTransfers.collect { storedList ->
                    if (storedList.isNotEmpty()) {
                        _transfers.value = storedList
                    }
                }
            }

            // Load previously saved notification logs from local Room database
            scope.launch {
                repo.allNotifications.collect { storedList ->
                    if (storedList.isNotEmpty()) {
                        _mirroredNotifications.value = storedList
                    }
                }
            }
        }
    }

    fun log(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val formatted = "[$timestamp] $message"
        _logs.value = listOf(formatted) + _logs.value.take(49)
    }

    fun connect(config: ServerConfig) {
        disconnect()
        activeConfig = config
        val url = config.toWebSocketUrl()
        _activeServerUrl.value = url
        _activePairingCode.value = config.code
        _connectionStatus.value = ConnectionStatus.CONNECTING
        log("Connecting to $url")

        val request = try {
            Request.Builder().url(url).build()
        } catch (e: Exception) {
            log("Invalid URL: ${e.message}")
            _connectionStatus.value = ConnectionStatus.ERROR
            return
        }

        currentWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                log("Connected to relay. Prompting consent...")
                _connectionStatus.value = ConnectionStatus.WAITING_CONSENT
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleTextMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleBinaryMessage(bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                log("Server closing ($code): $reason")
                handleDisconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                log("Disconnected ($code): $reason")
                handleDisconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                log("Connection error: ${t.localizedMessage ?: "Unknown failure"}")
                handleDisconnect(isError = true)
            }
        })
    }

    fun disconnect() {
        try {
            currentWebSocket?.send(JSONObject().apply {
                put("type", "consent")
                put("granted", false)
            }.toString())
            currentWebSocket?.close(1000, "User disconnected")
        } catch (_: Exception) {}
        currentWebSocket = null
        handleDisconnect()
    }

    private fun handleDisconnect(isError: Boolean = false) {
        _connectionStatus.value = if (isError) ConnectionStatus.ERROR else ConnectionStatus.DISCONNECTED
        _incomingOffer.value = null
        cleanupFailedIncoming()
    }

    fun grantConsent(granted: Boolean) {
        if (_connectionStatus.value != ConnectionStatus.WAITING_CONSENT &&
            _connectionStatus.value != ConnectionStatus.CONNECTED_AND_APPROVED) return

        val msg = JSONObject().apply {
            put("type", "consent")
            put("granted", granted)
        }
        val sent = currentWebSocket?.send(msg.toString()) ?: false
        if (granted && sent) {
            _connectionStatus.value = ConnectionStatus.CONNECTED_AND_APPROVED
            log("Consent GRANTED. Relay session active.")
        } else {
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            log("Consent DENIED. Session terminated.")
            disconnect()
        }
    }

    private fun handleTextMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type")
            log("Received signal: $type")

            when (type) {
                "consent" -> {
                    val granted = json.optBoolean("granted", false)
                    if (!granted) {
                        _connectionStatus.value = ConnectionStatus.DISCONNECTED
                        log("Session revoked by peer.")
                    }
                }
                "peer_left" -> {
                    log("Panel disconnected.")
                    _incomingOffer.value = null
                    cleanupFailedIncoming()
                }
                "file_start" -> {
                    val rawName = json.optString("name", "received_file")
                    val safeName = sanitizeFilename(rawName)
                    val size = json.optLong("size", 0L)
                    val mime = json.optString("mime", "application/octet-stream")
                    val offer = IncomingFileOffer(
                        offerId = UUID.randomUUID().toString(),
                        name = safeName,
                        sizeBytes = size,
                        mimeType = mime
                    )
                    _incomingOffer.value = offer
                    log("File offer received: $safeName (${formatFileSize(size)})")
                }
                "file_end" -> {
                    completeIncomingFile()
                }
                "file_reject", "file_cancel" -> {
                    val name = json.optString("name")
                    log("Transfer canceled/rejected: $name")
                    cleanupFailedIncoming()
                }
            }
        } catch (e: Exception) {
            log("Error parsing message: ${e.message}")
        }
    }

    private fun handleBinaryMessage(bytes: ByteArray) {
        val transfer = currentIncomingTransfer ?: return
        val stream = currentIncomingStream ?: return

        try {
            val newTransferred = transfer.transferredBytes + bytes.size
            if (transfer.sizeBytes > 0 && newTransferred > transfer.sizeBytes) {
                log("Security warning: incoming file exceeded declared size! Canceling.")
                rejectIncomingFile(IncomingFileOffer(transfer.id, transfer.name, transfer.sizeBytes))
                return
            }

            stream.write(bytes)
            val progress = if (transfer.sizeBytes > 0) {
                (newTransferred.toFloat() / transfer.sizeBytes.toFloat()).coerceIn(0f, 1f)
            } else 0f

            val updated = transfer.copy(
                transferredBytes = newTransferred,
                progress = progress
            )
            currentIncomingTransfer = updated
            updateTransferItem(updated)
        } catch (e: Exception) {
            log("Write error: ${e.message}")
            cleanupFailedIncoming()
        }
    }

    fun acceptIncomingFile(offer: IncomingFileOffer, context: Context) {
        _incomingOffer.value = null
        log("Accepting file: ${offer.name}")

        try {
            val outputStream: OutputStream
            val targetUri: Uri?
            var targetFile: File? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, offer.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, offer.mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Cannot create MediaStore download entry")
                targetUri = uri
                outputStream = context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Cannot open output stream")
            } else {
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.filesDir
                if (!downloadDir.exists()) downloadDir.mkdirs()
                val file = File(downloadDir, offer.name)
                targetFile = file
                targetUri = Uri.fromFile(file)
                outputStream = FileOutputStream(file)
            }

            currentIncomingStream = outputStream
            currentIncomingUri = targetUri
            currentIncomingFile = targetFile

            val item = FileTransferItem(
                id = offer.offerId,
                name = offer.name,
                sizeBytes = offer.sizeBytes,
                transferredBytes = 0L,
                isIncoming = true,
                status = TransferStatus.IN_PROGRESS,
                localUri = targetUri,
                localFilePath = targetFile?.absolutePath
            )
            currentIncomingTransfer = item
            addTransferItem(item)

            currentWebSocket?.send(JSONObject().apply {
                put("type", "file_accept")
                put("name", offer.name)
            }.toString())
        } catch (e: Exception) {
            log("Failed to accept file: ${e.message}")
            cleanupFailedIncoming()
        }
    }

    fun rejectIncomingFile(offer: IncomingFileOffer) {
        _incomingOffer.value = null
        log("Rejected file: ${offer.name}")
        try {
            currentWebSocket?.send(JSONObject().apply {
                put("type", "file_reject")
                put("name", offer.name)
            }.toString())
        } catch (_: Exception) {}
        cleanupFailedIncoming()
    }

    private fun completeIncomingFile() {
        val transfer = currentIncomingTransfer ?: return
        try {
            currentIncomingStream?.flush()
            currentIncomingStream?.close()
        } catch (_: Exception) {}

        log("Successfully received: ${transfer.name}")
        val completed = transfer.copy(
            transferredBytes = transfer.sizeBytes,
            progress = 1f,
            status = TransferStatus.COMPLETED
        )
        updateTransferItem(completed)

        currentIncomingTransfer = null
        currentIncomingStream = null
        currentIncomingUri = null
        currentIncomingFile = null
    }

    private fun cleanupFailedIncoming() {
        try {
            currentIncomingStream?.close()
        } catch (_: Exception) {}
        currentIncomingStream = null

        currentIncomingFile?.let {
            if (it.exists()) it.delete()
        }
        currentIncomingFile = null

        currentIncomingTransfer?.let {
            val failed = it.copy(status = TransferStatus.FAILED)
            updateTransferItem(failed)
        }
        currentIncomingTransfer = null
    }

    fun sendFile(uri: Uri, context: Context) {
        val ws = currentWebSocket
        if (_connectionStatus.value != ConnectionStatus.CONNECTED_AND_APPROVED || ws == null) {
            log("Cannot send: phone not linked or consent not granted.")
            return
        }

        scope.launch {
            var inputStream: InputStream? = null
            try {
                val resolver = context.contentResolver
                var fileName = "document_${System.currentTimeMillis()}"
                var fileSize = 0L
                val mimeType = resolver.getType(uri) ?: "application/octet-stream"

                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                        if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                    }
                }

                fileName = sanitizeFilename(fileName)
                inputStream = resolver.openInputStream(uri)
                    ?: throw IllegalStateException("Could not open file")

                val transferId = UUID.randomUUID().toString()
                val transferItem = FileTransferItem(
                    id = transferId,
                    name = fileName,
                    sizeBytes = fileSize,
                    transferredBytes = 0L,
                    isIncoming = false,
                    status = TransferStatus.IN_PROGRESS,
                    localUri = uri
                )
                addTransferItem(transferItem)
                log("Sending: $fileName (${formatFileSize(fileSize)})")

                // Send start metadata
                ws.send(JSONObject().apply {
                    put("type", "file_start")
                    put("name", fileName)
                    put("size", fileSize)
                    put("mime", mimeType)
                }.toString())

                val buffer = ByteArray(CHUNK_SIZE)
                var bytesRead: Int
                var totalSent = 0L
                val startTime = System.currentTimeMillis()

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    val chunk = if (bytesRead == CHUNK_SIZE) buffer else buffer.copyOf(bytesRead)
                    ws.send(ByteString.of(*chunk))
                    totalSent += bytesRead

                    val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
                    val speed = if (elapsedSec > 0) (totalSent / elapsedSec).toLong() else 0L
                    val progress = if (fileSize > 0) (totalSent.toFloat() / fileSize.toFloat()).coerceIn(0f, 1f) else 0f

                    val updated = transferItem.copy(
                        transferredBytes = totalSent,
                        progress = progress,
                        speedBps = speed
                    )
                    updateTransferItem(updated)
                    delay(10) // Smooth flow control
                }

                // Send end signal
                ws.send(JSONObject().apply {
                    put("type", "file_end")
                    put("name", fileName)
                }.toString())

                val finished = transferItem.copy(
                    transferredBytes = totalSent,
                    progress = 1f,
                    status = TransferStatus.COMPLETED
                )
                updateTransferItem(finished)
                log("Finished sending: $fileName")
            } catch (e: Exception) {
                log("Send failed: ${e.message}")
            } finally {
                try { inputStream?.close() } catch (_: Exception) {}
            }
        }
    }

    fun sendNotification(appName: String, title: String, text: String, isOngoing: Boolean = false) {
        val item = MirroredNotificationItem(
            id = UUID.randomUUID().toString(),
            packageName = "notification",
            appName = appName,
            title = title,
            text = text,
            isOngoing = isOngoing
        )
        _mirroredNotifications.value = listOf(item) + _mirroredNotifications.value.take(49)

        val isConnected = _connectionStatus.value == ConnectionStatus.CONNECTED_AND_APPROVED

        // Persist notification to Room database locally on device
        scope.launch {
            repository?.saveNotification(item, isMirrored = isConnected, isPending = !isConnected)
        }

        if (isConnected) {
            try {
                currentWebSocket?.send(JSONObject().apply {
                    put("type", "notification")
                    put("appName", appName)
                    put("title", title)
                    put("text", text)
                    put("timestamp", item.timestamp)
                }.toString())
                log("Mirrored notification from $appName: $title")

                scope.launch {
                    repository?.markNotificationMirrored(item.id)
                }
            } catch (e: Exception) {
                log("Failed to mirror notification: ${e.message}")
            }
        }
    }

    // Interactive Loopback / Simulator Mode for instant on-device testing
    fun startSimulatedDemo(context: Context) {
        disconnect()
        _connectionStatus.value = ConnectionStatus.WAITING_CONSENT
        _activeServerUrl.value = "simulated://local-test-panel:8080"
        _activePairingCode.value = "DEMO88"
        log("Started Local Loopback Demo mode.")
    }

    fun simulateIncomingOfferFromPanel() {
        val sampleNames = listOf("Quarterly_Report_2026.pdf", "Project_Blueprint.png", "Meeting_Audio_Memo.m4a")
        val sampleSizes = listOf(450 * 1024L, 1200 * 1024L, 2500 * 1024L)
        val idx = (0..2).random()
        val offer = IncomingFileOffer(
            offerId = UUID.randomUUID().toString(),
            name = sampleNames[idx],
            sizeBytes = sampleSizes[idx]
        )
        _incomingOffer.value = offer
        log("Simulated panel offered file: ${offer.name}")
    }

    private fun addTransferItem(item: FileTransferItem) {
        _transfers.value = listOf(item) + _transfers.value
        scope.launch {
            repository?.saveTransfer(item)
        }
    }

    private fun updateTransferItem(item: FileTransferItem) {
        _transfers.value = _transfers.value.map { if (it.id == item.id) item else it }
        scope.launch {
            repository?.saveTransfer(item)
        }
    }

    fun clearHistory() {
        _transfers.value = emptyList()
        _mirroredNotifications.value = emptyList()
        scope.launch {
            repository?.clearTransfers()
            repository?.clearNotifications()
        }
        log("Cleared local history from Room database.")
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace("/", "_")
            .replace("\\", "_")
            .replace("..", "_")
            .trim()
            .ifEmpty { "download_${System.currentTimeMillis()}" }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
