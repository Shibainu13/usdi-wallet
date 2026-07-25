package com.dev.usdi_wallet.ui.common

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dev.usdi_wallet.ui.theme.WalletColors
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch

@Composable
fun QrScannerScreen(
    onResult: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var hasScanned by remember { mutableStateOf(false) }
    var isProcessingGallery by remember { mutableStateOf(false) }
    var galleryError by remember { mutableStateOf<String?>(null) }
    var showManualInput by remember { mutableStateOf(false) }
    var manualInput by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isProcessingGallery = true
            galleryError = null
            val result = runCatching {
                QrCodeUtils.extractQrText(context, uri)
            }.getOrNull()

            isProcessingGallery = false
            if (result != null) {
                hasScanned = true
                onResult(result)
            } else {
                galleryError = "No QR code found in this image"
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val scanner = BarcodeScanning.getClient()

                    scope.launch {
                        val cameraProvider = ProcessCameraProvider.awaitInstance(ctx)
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { imageAnalysis ->
                                imageAnalysis.setAnalyzer(
                                    ContextCompat.getMainExecutor(ctx),
                                ) { imageProxy ->
                                    if (!hasScanned && !isProcessingGallery) {
                                        processImageFrame(imageProxy, scanner) { qrContent ->
                                            hasScanned = true
                                            onResult(qrContent)
                                        }
                                    } else {
                                        imageProxy.close()
                                    }
                                }
                            }

                        runCatching {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                            )
                        }
                    }

                    previewView
                },
            )

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .background(Color.Transparent),
                ) {
                    ScanFrameCorners()
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White,
                    modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera permission is required to scan QR codes",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant permission", color = WalletColors.Primary)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .align(Alignment.TopStart),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.background(
                    Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50)
                ),
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Close", tint = Color.White)
            }

            Text(
                text = "Scan QR Code",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )

            // Placeholder to balance the row
            Spacer(modifier = Modifier.size(48.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(vertical = 16.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Gallery error feedback
            galleryError?.let { error ->
                Text(
                    text = error,
                    color = WalletColors.Danger,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // Gallery loading indicator
            if (isProcessingGallery) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                    Text("Reading QR from image...", color = Color.White,
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            if (showManualInput) {
                OutlinedTextField(
                    value = manualInput,
                    onValueChange = { manualInput = it },
                    label = { Text("Paste invitation URL", color = Color.White) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = WalletColors.Primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                    ),
                    singleLine = false,
                    maxLines = 4,
                    trailingIcon = {
                        if (manualInput.isNotBlank()) {
                            IconButton(onClick = {
                                hasScanned = true
                                onResult(manualInput.trim())
                            }) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Submit",
                                    tint = WalletColors.Primary,
                                )
                            }
                        }
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TextButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    enabled = !isProcessingGallery,
                ) {
                    Icon(Icons.Default.Photo, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Gallery", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }

                // Temporary testing button
                TextButton(onClick = { showManualInput = !showManualInput }) {
                    Icon(
                        if (showManualInput) Icons.Default.QrCode else Icons.Default.Edit,
                        null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        if (showManualInput) "Scan instead" else "Paste URL",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanFrameCorners() {
    val cornerColor = Color.White
    val cornerLength = 24.dp
    val cornerThickness = 3.dp
    val cornerRadius = 4.dp

    Box(modifier = Modifier.fillMaxSize()) {
        // Top-left
        ScanCorner(
            modifier = Modifier.align(Alignment.TopStart),
            color = cornerColor,
            length = cornerLength,
            thickness = cornerThickness,
            radius = cornerRadius,
            flipH = false, flipV = false,
        )
        // Top-right
        ScanCorner(
            modifier = Modifier.align(Alignment.TopEnd),
            color = cornerColor,
            length = cornerLength,
            thickness = cornerThickness,
            radius = cornerRadius,
            flipH = true, flipV = false,
        )
        // Bottom-left
        ScanCorner(
            modifier = Modifier.align(Alignment.BottomStart),
            color = cornerColor,
            length = cornerLength,
            thickness = cornerThickness,
            radius = cornerRadius,
            flipH = false, flipV = true,
        )
        // Bottom-right
        ScanCorner(
            modifier = Modifier.align(Alignment.BottomEnd),
            color = cornerColor,
            length = cornerLength,
            thickness = cornerThickness,
            radius = cornerRadius,
            flipH = true, flipV = true,
        )
    }
}

@Composable
private fun ScanCorner(
    modifier: Modifier,
    color: Color,
    length: Dp,
    thickness: Dp,
    radius: Dp,
    flipH: Boolean,
    flipV: Boolean,
) {
    Canvas(
        modifier = modifier.size(length),
    ) {
        val strokeWidth = thickness.toPx()
        val cornerLen = length.toPx()
        val r = radius.toPx()

        val left = if (flipH) size.width - strokeWidth / 2 else strokeWidth / 2
        val right = if (flipH) strokeWidth / 2 else size.width - strokeWidth / 2
        val top = if (flipV) size.height - strokeWidth / 2 else strokeWidth / 2
        val bottom = if (flipV) strokeWidth / 2 else size.height - strokeWidth / 2

        val paint = Paint().apply {
            this.color = color
            this.style = PaintingStyle.Stroke
            this.strokeWidth = strokeWidth
        }

        drawContext.canvas.apply {
            drawLine(
                Offset(left, top),
                Offset(right, top),
                paint.apply { this.strokeWidth = strokeWidth },
            )
            drawLine(
                Offset(left, top),
                Offset(left, bottom),
                paint,
            )
        }
    }
}

@OptIn(ExperimentalGetImage::class)
private fun processImageFrame(
    imageProxy: ImageProxy,
    scanner: BarcodeScanner,
    onQrFound: (String) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                ?.rawValue
                ?.let(onQrFound)
        }
        .addOnCompleteListener { imageProxy.close() }
}
