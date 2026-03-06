package com.dev.usdi_wallet.connection

import com.dev.usdi_wallet.message.Message
import kotlinx.coroutines.flow.Flow

interface ConnectionManager {
    val protocolId: String
    val state: Flow<ConnectionState>

    suspend fun start(config: ConnectionStartupConfig)
    suspend fun sendMessage(message: Message)
    suspend fun stop()
}