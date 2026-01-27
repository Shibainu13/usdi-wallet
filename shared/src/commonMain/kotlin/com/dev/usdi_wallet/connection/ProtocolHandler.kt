package com.dev.usdi_wallet.connection

import kotlinx.coroutines.flow.Flow

interface ProtocolHandler {
    val protocolId: String
    fun detectOperation(input: String): ProtocolOperation?
    fun executeOperation(operation: ProtocolOperation): Flow<OperationState>
    fun receiveCredential(input: String): Flow<OperationState>
    fun presentProof(input: String): Flow<OperationState>
    fun verifyProof(input: String): Flow<OperationState>
    fun cleanUp()
}

interface PersistConnectionCapable {
    fun establishConnection(input: String): Flow<OperationState>
}

interface MessageCapable {
    fun sendMessage(message: String): Flow<OperationState>
    fun receiveMessage(): Flow<OperationState>
}