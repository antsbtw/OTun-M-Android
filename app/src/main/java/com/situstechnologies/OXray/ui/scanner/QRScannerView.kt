package com.situstechnologies.OXray.ui.scanner

import android.Manifest
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
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
import androidx.compose.ui.geometry.Size
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
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * QR Scanner Screen
 * Uses CameraX + ML Kit for barcode scanning
 *
 * Ported from iOS QRScannerView.swift
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QRScannerScreen(
    onQRCodeDetected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            cameraPermissionState.status.isGranted -> {
                // Camera permission granted, show scanner
                CameraPreview(
                    onQRCodeDetected = onQRCodeDetected,
                    onDismiss = onDismiss
                )
            }
            else -> {
                // Request camera permission
                PermissionRequestScreen(
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
                    onDismiss = onDismiss
                )
            }
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

    var scannerStatus by remember { mutableStateOf("Initializing...") }
    var scannedItems by remember { mutableStateOf(listOf<String>()) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val executor = ContextCompat.getMainExecutor(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    // Preview
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    // Image Analysis for QR detection
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(
                                Executors.newSingleThreadExecutor(),
                                QRCodeAnalyzer { qrCode ->
                                    Log.d("QRScanner", "QR code detected: $qrCode")

                                    // Add to history if not duplicate
                                    if (!scannedItems.contains(qrCode)) {
                                        scannedItems = scannedItems + qrCode
                                        scannerStatus = "QR Code Detected"
                                    }

                                    // Trigger callback
                                    onQRCodeDetected(qrCode)
                                }
                            )
                        }

                    // Camera selector
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )

                        scannerStatus = "Ready to scan"
                    } catch (e: Exception) {
                        Log.e("QRScanner", "Camera binding failed", e)
                        scannerStatus = "Camera error"
                    }

                }, executor)

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay UI
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
                // Frame outline
                drawRoundRect(
                    color = Color.Green,
                    size = size,
                    cornerRadius = CornerRadius(20f, 20f),
                    style = Stroke(width = 3f)
                )

                // Corner indicators
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
 * QR Code Analyzer using ML Kit
 */
class QRCodeAnalyzer(
    private val onQRCodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: androidx.camera.core.ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        barcode.rawValue?.let { qrCode ->
                            onQRCodeDetected(qrCode)
                        }
                    }
                }
                .addOnFailureListener {
                    Log.e("QRCodeAnalyzer", "Barcode scanning failed", it)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
