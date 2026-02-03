package com.dev.usdi_wallet.connection

import kotlinx.coroutines.flow.Flow

interface ProtocolHandler {
    val protocolId: String
    fun detectOperation(input: String): ProtocolOperation?
    fun executeOperation(operation: ProtocolOperation): Flow<OperationState>
    fun receiveCredential(input: Any): Flow<OperationState>
    fun presentProof(input: Any): Flow<OperationState>
    fun verifyProof(input: Any): Flow<OperationState>
    fun cleanUp()
}

interface PersistConnectionCapable {
    fun establishConnection(input: String): Flow<OperationState>
}

interface MessageCapable {
    fun sendMessage(message: String): Flow<OperationState>
    fun receiveMessage(): Flow<OperationState>
}