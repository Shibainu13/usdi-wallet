package com.dev.usdi_wallet.domain.connection

import kotlinx.coroutines.flow.Flow

interface ConnectionManager<SdkMessage> {
    val state: Flow<ConnectionState>

    suspend fun start()
    suspend fun sendMessage(message: SdkMessage)
    suspend fun receiveMessage(msgHandler: suspend (message: SdkMessage) -> Unit)
    suspend fun stop()
}