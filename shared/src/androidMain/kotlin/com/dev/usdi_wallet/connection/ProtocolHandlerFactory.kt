package com.dev.usdi_wallet.connection

import android.content.Context
import com.dev.usdi_wallet.connection.hyperledger_identus.IdentusDIDCommHandler

actual class ProtocolHandlerFactory(
    private val context: Context
) {
    actual fun loadHandlers(): List<ProtocolHandler> {
        return listOf<ProtocolHandler>(
            IdentusDIDCommHandler(context),
        )
    }
}