package com.dev.usdi_wallet.credential

import com.dev.usdi_wallet.connection.ConnectionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface CredentialManager<CredentialType, MessageType> {
    val proofRequestToProcess: StateFlow<List<MessageType>>
    fun getCredentials(): Flow<List<CredentialType>>
    suspend fun getCredential(id: String): Credential?
    suspend fun saveCredential(credential: Credential)
    suspend fun removeCredential(id: String)
    suspend fun handleInbound(
        message: MessageType,
        connectionManager: ConnectionManager<MessageType>,
    )
    suspend fun handleVerification(message: MessageType): Boolean
    suspend fun sendVerificationRequest(
        request: VerificationRequest,
        domain: String,
        challenge: String,
    )
    suspend fun preparePresentationProof(credential: CredentialType, message: MessageType)
    fun toUiCredential(sdkCredential: CredentialType): Credential
    suspend fun toSdkCredential(credential: Credential): CredentialType
}