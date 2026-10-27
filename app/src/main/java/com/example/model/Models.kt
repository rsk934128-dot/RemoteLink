package com.example.model

import android.net.Uri

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    WAITING_CONSENT,
    CONNECTED_AND_APPROVED,
    ERROR
}

enum class TransferStatus {
    WAITING_CONFIRMATION,
    IN_PROGRESS,
    COMPLETED,
    REJECTED,
    FAILED,
    CANCELLED
}

data class FileTransferItem(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val transferredBytes: Long = 0L,
    val isIncoming: Boolean,
    val status: TransferStatus = TransferStatus.IN_PROGRESS,
    val progress: Float = 0f,
    val speedBps: Long = 0L,
    val localUri: Uri? = null,
    val localFilePath: String? = null,
    val errorMsg: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class IncomingFileOffer(
    val offerId: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String = "application/octet-stream"
)

data class MirroredNotificationItem(
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isOngoing: Boolean = false
)

data class ServerConfig(
    val host: String = "192.168.1.5",
    val port: Int = 8080,
    val code: String = "",
    val isSecure: Boolean = false
) {
    fun toWebSocketUrl(): String {
        val scheme = if (isSecure) "wss" else "ws"
        val cleanHost = host.trim()
            .removePrefix("ws://")
            .removePrefix("wss://")
            .removePrefix("http://")
            .removePrefix("https://")
        val cleanCode = code.trim().uppercase()
        return if (cleanHost.contains(":")) {
            "$scheme://$cleanHost/ws?role=device&code=$cleanCode"
        } else {
            "$scheme://$cleanHost:$port/ws?role=device&code=$cleanCode"
        }
    }
}
