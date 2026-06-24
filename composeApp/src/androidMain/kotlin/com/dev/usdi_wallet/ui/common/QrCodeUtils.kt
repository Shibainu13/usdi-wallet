package com.dev.usdi_wallet.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader
import co.touchlab.kermit.Logger

object QrCodeUtils {
    fun createQrBitmap(content: String, size: Int = 800): Bitmap {
        val pixels = createQrPixels(content, size)
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, size, 0, 0, size, size)
        }
    }

    fun extractQrText(context: Context, uri: Uri): String {
        val bitmap = loadBitmap(context, uri) ?: error("Unable to read selected image")
        return extractQrText(bitmap)
    }

    fun extractQrText(bitmap: Bitmap): String =
        extractQrTextFromBitmaps(bitmap.variants())

    internal fun createQrPixels(content: String, size: Int = 800): IntArray {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 4,
        )
        val matrix = MultiFormatWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size,
            hints,
        )
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            val offset = y * size
            for (x in 0 until size) {
                pixels[offset + x] = if (matrix[x, y]) {
                    android.graphics.Color.BLACK
                } else {
                    android.graphics.Color.WHITE
                }
            }
        }
        return pixels
    }

    internal fun extractQrText(width: Int, height: Int, pixels: IntArray): String =
        extractQrTextFromSources(listOf(RGBLuminanceSource(width, height, pixels)))

    private fun extractQrTextFromBitmaps(bitmaps: List<Bitmap>): String =
        extractQrTextFromSources(bitmaps.map { it.toLuminanceSource() })

    private fun extractQrTextFromSources(sources: List<RGBLuminanceSource>): String {
        val hints = mapOf<DecodeHintType, Any>(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.CHARACTER_SET to "UTF-8",
            DecodeHintType.TRY_HARDER to true,
        )

        sources.forEach { source ->
            source.binaryVariants().forEach { binaryBitmap ->
                decodeSingle(binaryBitmap, hints)?.let {
                    Logger.d("QrCodeUtils") { "QR code found in single image variant" }
                    return it
                }
                decodeMultiple(binaryBitmap, hints)?.let {
                    Logger.d("QrCodeUtils") { "QR code found in multiple image variant" }
                    return it
                }
            }
        }

        error("No QR code found in selected image")
    }

    private fun decodeSingle(binaryBitmap: BinaryBitmap, hints: Map<DecodeHintType, Any>): String? =
        runCatching { MultiFormatReader().decode(binaryBitmap, hints).text }.getOrNull()

    private fun decodeMultiple(binaryBitmap: BinaryBitmap, hints: Map<DecodeHintType, Any>): String? =
        runCatching {
            GenericMultipleBarcodeReader(MultiFormatReader())
                .decodeMultiple(binaryBitmap, hints)
                .firstOrNull { it.barcodeFormat == BarcodeFormat.QR_CODE }
                ?.text
        }.getOrNull()

    private fun Bitmap.variants(): List<Bitmap> {
        val centerCrop = centerSquareCrop()
        return listOfNotNull(
            this,
            rotate(90f),
            rotate(180f),
            rotate(270f),
            centerCrop.takeIf { it.width != width || it.height != height },
        )
    }

    private fun RGBLuminanceSource.binaryVariants(): List<BinaryBitmap> =
        listOf(
            BinaryBitmap(HybridBinarizer(this)),
            BinaryBitmap(GlobalHistogramBinarizer(this)),
            BinaryBitmap(HybridBinarizer(invert())),
            BinaryBitmap(GlobalHistogramBinarizer(invert())),
        )

    private fun Bitmap.toLuminanceSource(): RGBLuminanceSource {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return RGBLuminanceSource(width, height, pixels)
    }

    private fun Bitmap.rotate(degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, false)
    }

    private fun Bitmap.centerSquareCrop(): Bitmap {
        val size = minOf(width, height)
        val left = (width - size) / 2
        val top = (height - size) / 2
        return Bitmap.createBitmap(this, left, top, size, size)
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }
    }
}
