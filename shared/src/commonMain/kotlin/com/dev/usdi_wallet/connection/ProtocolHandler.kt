package com.dev.usdi_wallet.connection

import kotlinx.coroutines.flow.Flow

interface ProtocolHandler {
    val protocolId: String

    fun canHandle(input: String): Boolean

    fun handle(input: String): Flow<ConnectionState>

    fun cleanUp(): Unit
}