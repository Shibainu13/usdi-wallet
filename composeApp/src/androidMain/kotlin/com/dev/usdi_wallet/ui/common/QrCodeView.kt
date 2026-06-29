package com.dev.usdi_wallet.ui.common

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.core.graphics.set
import androidx.core.graphics.createBitmap

@Composable
fun QrCodeView(content: String, size: Int = 240, renderResolution: Int = 800) {
    val bitmap = remember(content, renderResolution) {
        QrCodeUtils.createQrBitmap(content, renderResolution)
    }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "QR Code",
        modifier = Modifier.size(size.dp)
    )
}