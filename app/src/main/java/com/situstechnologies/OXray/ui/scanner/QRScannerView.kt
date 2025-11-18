package com.situstechnologies.OXray.ui.scanner

import android.Manifest
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * QR Scanner Screen - ML Kit Bundled Implementation
 * Uses CameraX + ML Kit (bundled) for reliable barcode scanning
 *
 * Optimized for long-content QR codes (e.g., encrypted VPN configs)
 * ML Kit bundled version does NOT require Google Play Services
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QRScannerScreen(
    onQRCodeDetected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    Log.d(
        "QRScanner",
        "[INFO] QRScannerScreen composed, permission granted: ${cameraPermissionState.status.isGranted}"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (cameraPermissionState.status.isGranted) {
            CameraPreview(
                onQRCodeDetected = onQRCodeDetected,
                onDismiss = onDismiss
            )
        } else {
            Log.w("QRScanner", "[WARNING] Camera permission not granted")
            PermissionRequestScreen(
                onRequestPermission = {
                    Log.d("QRScanner", "[INFO] Requesting camera permission")
                    cameraPermissionState.launchPermissionRequest()
                },
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
fun CameraPreview(
    onQRCodeDetected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    var scannerStatus by remember { mutableStateOf("Initializing camera...") }
    var scannedItems by remember { mutableStateOf(listOf<String>()) }
    var hasScanned by remember { mutableStateOf(false) } // 防止重复扫描

    Log.d("QRScanner", "[INFO] CameraPreview composed")

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                Log.d("QRScanner", "[DEBUG] Creating PreviewView")
                val previewView = PreviewView(ctx)
                val executor = ContextCompat.getMainExecutor(ctx)

                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        Log.d("QRScanner", "[INFO] CameraProvider obtained")

                        val preview = Preview.Builder()
                            .build()
                            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                        Log.d("QRScanner", "[DEBUG] Preview built")

                        // 单线程分析器执行器（记住：不要每次重建）
                        val analysisExecutor = Executors.newSingleThreadExecutor()

                        // ML Kit Image Analysis (optimized for dense QR codes)
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                            .setTargetResolution(android.util.Size(1920, 1080))   // 1080p
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        // 设置分析器（注意：只设置一次）
                        imageAnalysis.setAnalyzer(
                            analysisExecutor,
                            MLKitQRCodeAnalyzer { qrCode ->
                                // 这里是“外层的防重” + “停止分析器”逻辑
                                if (!hasScanned) {
                                    hasScanned = true

                                    // ✅ 立刻停止后续分析，防止 MLKit 多帧回调 & Camera 死锁
                                    imageAnalysis.clearAnalyzer()

                                    Log.d(
                                        "QRScanner",
                                        "[INFO] ==================== QR Code Detected ===================="
                                    )
                                    Log.d(
                                        "QRScanner",
                                        "[INFO] QR Code length: ${qrCode.length} characters"
                                    )

                                    // 分段打印完整内容
                                    val chunkSize = 4000
                                    val chunks = (qrCode.length + chunkSize - 1) / chunkSize
                                    for (i in 0 until chunks) {
                                        val start = i * chunkSize
                                        val end = minOf(start + chunkSize, qrCode.length)
                                        Log.d(
                                            "QRScanner",
                                            "[INFO] QR Code Part ${i + 1}/$chunks: ${
                                                qrCode.substring(
                                                    start,
                                                    end
                                                )
                                            }"
                                        )
                                    }
                                    Log.d(
                                        "QRScanner",
                                        "[INFO] ==================== End of QR Code ===================="
                                    )

                                    scannedItems = listOf(qrCode)
                                    scannerStatus = "✓ Scanned Successfully!"
                                    Log.d(
                                        "QRScanner",
                                        "[INFO] QR code scanned successfully"
                                    )

                                    // 震动反馈（可选）
                                    try {
                                        val vibrator =
                                            ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                        if (vibrator != null) {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                vibrator.vibrate(
                                                    android.os.VibrationEffect.createOneShot(
                                                        200,
                                                        android.os.VibrationEffect.DEFAULT_AMPLITUDE
                                                    )
                                                )
                                            } else {
                                                @Suppress("DEPRECATION")
                                                vibrator.vibrate(200)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.w(
                                            "QRScanner",
                                            "[WARNING] Vibration failed: ${e.message}"
                                        )
                                    }

                                    // ⭐ 关键：稍微延迟一下再调用回调，避免和 CameraX 解绑/重组“撞车”
                                    android.os.Handler(android.os.Looper.getMainLooper())
                                        .postDelayed({
                                            Log.d(
                                                "QRScanner",
                                                "[INFO] Triggering onQRCodeDetected callback"
                                            )
                                            onQRCodeDetected(qrCode)
                                        }, 300)
                                }
                            }
                        )

                        Log.d("QRScanner", "[DEBUG] ImageAnalysis configured with ML Kit")

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )

                            // Enable auto-focus on center
                            try {
                                val cameraControl = camera.cameraControl
                                val cameraInfo = camera.cameraInfo

                                val point = previewView.meteringPointFactory.createPoint(0.5f, 0.5f)
                                val action = FocusMeteringAction.Builder(point)
                                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                    .build()

                                if (cameraInfo.isFocusMeteringSupported(action)) {
                                    cameraControl.startFocusAndMetering(action)
                                    Log.d(
                                        "QRScanner",
                                        "[DEBUG] Auto-focus enabled on center"
                                    )
                                }
                            } catch (e: Exception) {
                                Log.w(
                                    "QRScanner",
                                    "[WARNING] Auto-focus setup failed: ${e.message}"
                                )
                            }

                            scannerStatus = "Ready to scan"
                            Log.d(
                                "QRScanner",
                                "[INFO] Camera bound to lifecycle successfully"
                            )
                        } catch (e: Exception) {
                            Log.e("QRScanner", "[ERROR] Camera binding failed", e)
                            scannerStatus = "Camera error: ${e.message}"
                        }
                    } catch (e: Exception) {
                        Log.e("QRScanner", "[ERROR] Failed to initialize camera", e)
                        scannerStatus = "Initialization error: ${e.message}"
                    }
                }, executor)

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        ScannerOverlay(
            scannerStatus = scannerStatus,
            scannedItems = scannedItems,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun ScannerOverlay(
    scannerStatus: String,
    scannedItems: List<String>,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.Black.copy(alpha = 0.6f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black.copy(alpha = 0.6f)
                    )
                ) {
                    Text("Cancel", color = Color.White)
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        scannerStatus,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Scan Frame
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(250.dp)) {
                drawRoundRect(
                    color = Color.Green,
                    size = size,
                    cornerRadius = CornerRadius(20f, 20f),
                    style = Stroke(width = 3f)
                )

                val cornerSize = 20f

                // Top-left
                drawLine(
                    color = Color.Green,
                    start = Offset(0f, cornerSize),
                    end = Offset(0f, 0f),
                    strokeWidth = 3f
                )
                drawLine(
                    color = Color.Green,
                    start = Offset(0f, 0f),
                    end = Offset(cornerSize, 0f),
                    strokeWidth = 3f
                )

                // Top-right
                drawLine(
                    color = Color.Green,
                    start = Offset(size.width - cornerSize, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 3f
                )
                drawLine(
                    color = Color.Green,
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, cornerSize),
                    strokeWidth = 3f
                )

                // Bottom-left
                drawLine(
                    color = Color.Green,
                    start = Offset(0f, size.height - cornerSize),
                    end = Offset(0f, size.height),
                    strokeWidth = 3f
                )
                drawLine(
                    color = Color.Green,
                    start = Offset(0f, size.height),
                    end = Offset(cornerSize, size.height),
                    strokeWidth = 3f
                )

                // Bottom-right
                drawLine(
                    color = Color.Green,
                    start = Offset(size.width - cornerSize, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 3f
                )
                drawLine(
                    color = Color.Green,
                    start = Offset(size.width, size.height - cornerSize),
                    end = Offset(size.width, size.height),
                    strokeWidth = 3f
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Scan History
        if (scannedItems.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                color = Color.Black.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Scan History",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )

                    scannedItems.takeLast(3).forEachIndexed { index, item ->
                        Text(
                            "${index + 1}. ${item.take(50)}...",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Instructions
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 30.dp),
            color = Color.Black.copy(alpha = 0.6f),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                "Position QR code within the frame",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun PermissionRequestScreen(
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Text(
                "Camera Permission Required",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                "OTun-M needs camera access to scan QR codes. Please enable camera permission.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )

            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Permission")
            }

            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    }
}


/**
 * ML Kit-based QR Code Analyzer
 * Optimized for long-content, high-density QR codes with low error correction
 * Uses bundled ML Kit (no Google Play Services required)
 */
class MLKitQRCodeAnalyzer(
    private val onQRCodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAllPotentialBarcodes()  // Enable detection of all potential barcodes
            .build()
    )

    @Volatile
    private var handled: Boolean = false  // 内部再做一层防重

    private var frameCount = 0
    private var lastLogTime = System.currentTimeMillis()

    init {
        Log.d("MLKitAnalyzer", "[INFO] ML Kit QR Code Analyzer initialized (bundled version)")
    }

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        Log.e("MLKitAnalyzer", "🔥 analyze() running")
        if (handled) {
            imageProxy.close()
            return
        }

        if (frameCount == 0) {
            Log.d("MLKitAnalyzer", "[FIRST] First analyze() call - analyzer is working!")
        }

        frameCount++

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastLogTime > 2000) {
            Log.d("MLKitAnalyzer", "[DEBUG] Processing frames... ($frameCount frames so far)")
            lastLogTime = currentTime
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            Log.w("MLKitAnalyzer", "[WARNING] MediaImage is null at frame #$frameCount")
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (handled) {
                    imageProxy.close()
                    return@addOnSuccessListener
                }

                if (barcodes.isNotEmpty()) {
                    Log.d(
                        "MLKitAnalyzer",
                        "[DEBUG] ✓ Found ${barcodes.size} barcode(s) at frame #$frameCount"
                    )
                }

                for (barcode in barcodes) {
                    Log.d(
                        "MLKitAnalyzer",
                        "[DEBUG] Barcode format: ${barcode.format}, valueType: ${barcode.valueType}"
                    )
                    Log.d(
                        "MLKitAnalyzer",
                        "[DEBUG] Barcode rawBytes: ${barcode.rawBytes?.size ?: 0} bytes"
                    )

                    val value = barcode.rawValue ?: barcode.displayValue

                    if (!value.isNullOrEmpty()) {
                        handled = true  // ✅ 标记为已处理，后续帧直接丢弃

                        Log.d("MLKitAnalyzer", "[INFO] ✓ QR Code detected")
                        Log.d("MLKitAnalyzer", "[INFO] Length: ${value.length}")
                        Log.d(
                            "MLKitAnalyzer",
                            "[INFO] First 100 chars: ${value.take(100)}"
                        )

                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            Log.d(
                                "MLKitAnalyzer",
                                "[DEBUG] Triggering callback on main thread with content length: ${value.length}"
                            )
                            onQRCodeDetected(value)
                        }

                        break
                    } else {
                        Log.w("MLKitAnalyzer", "[WARNING] Barcode detected but value is NULL/empty")
                    }
                }
            }
            .addOnFailureListener { exception ->
                Log.e(
                    "MLKitAnalyzer",
                    "[ERROR] Barcode scanning failed at frame #$frameCount: ${exception.message}",
                    exception
                )
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
