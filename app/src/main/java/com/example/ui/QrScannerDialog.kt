package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberEmerald
import com.example.util.ParsedQrResult
import com.example.util.QrCodeDecoder
import com.example.util.QrCodeParser
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrScannerDialog(
    onDismiss: () -> Unit,
    onQrDetected: (host: String, port: Int, code: String) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var cameraInstance by remember { mutableStateOf<Camera?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var detectedSuccess by remember { mutableStateOf<ParsedQrResult?>(null) }
    var statusMessage by remember { mutableStateOf("Align QR code within the frame to pair") }
    var isProcessingGalleryImage by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    // Modern Zero-Permission Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isProcessingGalleryImage = true
            statusMessage = "Analyzing chosen image with ML Kit..."
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        val inputImage = InputImage.fromBitmap(bitmap, 0)
                        val scanner = BarcodeScanning.getClient(
                            BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                .build()
                        )
                        scanner.process(inputImage)
                            .addOnSuccessListener { barcodes ->
                                val found = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                                if (found?.rawValue != null) {
                                    triggerHaptic(context, view)
                                    val parsed = QrCodeParser.parse(found.rawValue!!)
                                    detectedSuccess = parsed
                                } else {
                                    // Fallback to ZXing reader if ML Kit didn't catch subtle bitmap
                                    val zxingResult = QrCodeDecoder.decodeBitmap(bitmap)
                                    if (zxingResult != null) {
                                        triggerHaptic(context, view)
                                        val parsed = QrCodeParser.parse(zxingResult)
                                        detectedSuccess = parsed
                                    } else {
                                        statusMessage = "No valid QR code found in selected image."
                                    }
                                }
                            }
                            .addOnFailureListener {
                                val zxingResult = QrCodeDecoder.decodeBitmap(bitmap)
                                if (zxingResult != null) {
                                    triggerHaptic(context, view)
                                    val parsed = QrCodeParser.parse(zxingResult)
                                    detectedSuccess = parsed
                                } else {
                                    statusMessage = "ML Kit scan failed. Try a clearer image."
                                }
                            }
                            .addOnCompleteListener {
                                isProcessingGalleryImage = false
                            }
                    } else {
                        isProcessingGalleryImage = false
                        statusMessage = "Unable to decode image bitmap."
                    }
                }
            } catch (e: Exception) {
                isProcessingGalleryImage = false
                statusMessage = "Error reading file: ${e.message}"
            }
        }
    }

    // Auto-dispatch on successful detection with brief visual confirmation
    LaunchedEffect(detectedSuccess) {
        val result = detectedSuccess
        if (result != null) {
            delay(500)
            onQrDetected(result.host, result.port, result.code)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(16.dp)
                .testTag("qr_scanner_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with icon, title, and close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Scan Pairing Code",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "CameraX + Google ML Kit Vision",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("qr_scanner_close_button")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close scanner",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Camera Viewport or Permission Request Box
                if (hasCameraPermission) {
                    Box(
                        modifier = Modifier
                            .size(280.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
                        val isScanComplete = remember { AtomicBoolean(false) }

                        DisposableEffect(Unit) {
                            onDispose {
                                cameraExecutor.shutdown()
                            }
                        }

                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }

                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    try {
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.surfaceProvider = previewView.surfaceProvider
                                        }

                                        val scannerOptions = BarcodeScannerOptions.Builder()
                                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                            .build()
                                        val barcodeScanner = BarcodeScanning.getClient(scannerOptions)

                                        val imageAnalysis = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()

                                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                            processImageProxyWithMlKit(
                                                imageProxy = imageProxy,
                                                scanner = barcodeScanner,
                                                isScanComplete = isScanComplete,
                                                onSuccess = { rawValue ->
                                                    triggerHaptic(context, view)
                                                    val parsed = QrCodeParser.parse(rawValue)
                                                    detectedSuccess = parsed
                                                }
                                            )
                                        }

                                        val selector = if (useFrontCamera) {
                                            CameraSelector.DEFAULT_FRONT_CAMERA
                                        } else {
                                            CameraSelector.DEFAULT_BACK_CAMERA
                                        }

                                        cameraProvider.unbindAll()
                                        val boundCamera = cameraProvider.bindToLifecycle(
                                            (ctx as LifecycleOwner),
                                            selector,
                                            preview,
                                            imageAnalysis
                                        )
                                        cameraInstance = boundCamera
                                    } catch (e: Exception) {
                                        statusMessage = "Camera initialization failed: ${e.message}"
                                    }
                                }, ContextCompat.getMainExecutor(ctx))

                                previewView
                            },
                            update = {
                                // Dynamic updates if camera or torch toggles
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Animated Scanning Laser & Viewfinder Reticle Overlay
                        QrScannerReticleOverlay(isSuccess = detectedSuccess != null)

                        // Top Controls Overlay (Torch & Camera Switch)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Torch / Flashlight Toggle
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.55f),
                                contentColor = Color.White
                            ) {
                                IconButton(
                                    onClick = {
                                        val nextState = !isTorchOn
                                        isTorchOn = nextState
                                        cameraInstance?.cameraControl?.enableTorch(nextState)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                        contentDescription = "Toggle flashlight",
                                        tint = if (isTorchOn) CyberCyan else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Camera Switch (Front/Back)
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.55f),
                                contentColor = Color.White
                            ) {
                                IconButton(
                                    onClick = {
                                        useFrontCamera = !useFrontCamera
                                        isTorchOn = false
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cameraswitch,
                                        contentDescription = "Switch camera",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Success Indicator Overlay
                        if (detectedSuccess != null) {
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .align(Alignment.Center)
                                    .background(CyberEmerald.copy(alpha = 0.85f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Scanned successfully",
                                    tint = Color.White,
                                    modifier = Modifier.size(54.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Fallback Card requesting camera permission
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .size(280.dp)
                            .padding(4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Camera Permission Required",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "To scan pairing QR codes instantly, RemoteLink needs access to your camera.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Grant Camera Access")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Status or Success Badge
                if (detectedSuccess != null) {
                    val result = detectedSuccess!!
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberEmerald.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = CyberEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Code: ${result.code}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = CyberEmerald
                                )
                                Text(
                                    text = "Server: ${result.host}:${result.port}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isProcessingGalleryImage) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = statusMessage,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action: Pick image from gallery / manual cancel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_qr_image_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick QR Image", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cancel_scanner_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * Custom Animated Reticle Viewfinder with cybertech corner brackets and scanning beam
 */
@Composable
private fun QrScannerReticleOverlay(isSuccess: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "LaserTransition")
    val laserPosition by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LaserPosition"
    )

    val reticleColor = if (isSuccess) CyberEmerald else CyberCyan

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val boxSize = width * 0.72f
        val left = (width - boxSize) / 2f
        val top = (height - boxSize) / 2f
        val right = left + boxSize
        val bottom = top + boxSize
        val cornerLen = 28.dp.toPx()
        val strokeWidth = 3.5.dp.toPx()

        // Dim surrounding dark vignette
        drawRect(
            color = Color.Black.copy(alpha = 0.45f),
            size = size
        )

        // Clear transparent center viewport
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(boxSize, boxSize),
            cornerRadius = CornerRadius(16.dp.toPx()),
            blendMode = androidx.compose.ui.graphics.BlendMode.Clear
        )

        // Viewfinder Borders (4 Corner Brackets)
        // Top-Left
        drawLine(reticleColor, Offset(left, top), Offset(left + cornerLen, top), strokeWidth)
        drawLine(reticleColor, Offset(left, top), Offset(left, top + cornerLen), strokeWidth)

        // Top-Right
        drawLine(reticleColor, Offset(right, top), Offset(right - cornerLen, top), strokeWidth)
        drawLine(reticleColor, Offset(right, top), Offset(right, top + cornerLen), strokeWidth)

        // Bottom-Left
        drawLine(reticleColor, Offset(left, bottom), Offset(left + cornerLen, bottom), strokeWidth)
        drawLine(reticleColor, Offset(left, bottom), Offset(left, bottom - cornerLen), strokeWidth)

        // Bottom-Right
        drawLine(reticleColor, Offset(right, bottom), Offset(right - cornerLen, bottom), strokeWidth)
        drawLine(reticleColor, Offset(right, bottom), Offset(right, bottom - cornerLen), strokeWidth)

        // Moving Laser Beam (only while scanning)
        if (!isSuccess) {
            val laserY = top + (boxSize * laserPosition)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        CyberCyan.copy(alpha = 0.85f),
                        CyberCyan,
                        CyberCyan.copy(alpha = 0.85f),
                        Color.Transparent
                    ),
                    startX = left,
                    endX = right
                ),
                topLeft = Offset(left + 8.dp.toPx(), laserY - 2.dp.toPx()),
                size = Size(boxSize - 16.dp.toPx(), 3.5.dp.toPx())
            )
        }
    }
}

/**
 * Process CameraX ImageProxy using Google ML Kit BarcodeScanner
 */
@OptIn(ExperimentalGetImage::class)
private fun processImageProxyWithMlKit(
    imageProxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    isScanComplete: AtomicBoolean,
    onSuccess: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null && !isScanComplete.get()) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val rawValue = barcode.rawValue
                    if (!rawValue.isNullOrBlank() && isScanComplete.compareAndSet(false, true)) {
                        onSuccess(rawValue)
                        break
                    }
                }
            }
            .addOnFailureListener {
                // Ignore transient frame analyzer error
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

/**
 * Triggers subtle tactile vibration feedback upon successful QR detection
 */
private fun triggerHaptic(context: Context, view: android.view.View) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(
                VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    } catch (_: Exception) {
        try {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } catch (_: Exception) {}
    }
}
