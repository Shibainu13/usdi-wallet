package com.dev.usdi_wallet.protocol

import com.dev.usdi_wallet.message.Message

interface ProtocolHandler {
    suspend fun handleInbound(message: Message)
    suspend fun handleOutbound(message: Message)
}