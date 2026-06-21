package com.dev.usdi_wallet.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

class QrCodeUtilsTest {
    @Test
    fun extractQrTextReturnsContentFromCreatedQrCode() {
        val invitationUrl =
            "https://wallet.example.com/proof?oob=eyJpZCI6IjEyMyIsInR5cGUiOiJwcm9vZi1yZXF1ZXN0In0"
        val size = 320

        val pixels = QrCodeUtils.createQrPixels(invitationUrl, size)
        val extractedText = QrCodeUtils.extractQrText(size, size, pixels)

        assertEquals(invitationUrl, extractedText)
    }
}
