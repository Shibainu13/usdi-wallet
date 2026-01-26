package com.dev.usdi_wallet.connection

expect class ProtocolHandlerFactory {
    fun loadHandlers(): List<ProtocolHandler>
}
