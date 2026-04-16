package com.dev.usdi_wallet.credential

import com.dev.usdi_wallet.connection.ConnectionManager
import kotlinx.coroutines.flow.Flow

interface CredentialManager<CredentialType, MessageType> {
    fun getCredentials(): Flow<List<CredentialType>>
    fun getProofRequestsToProcess(): Flow<List<MessageType>>
    fun getVerificationResults(): Flow<List<VerificationResult>>
    suspend fun getCredential(id: String): Credential?
    suspend fun saveCredential(credential: Credential)
    suspend fun removeCredential(id: String)
    suspend fun handleInbound(
        message: MessageType,
        connectionManager: ConnectionManager<MessageType>,
    )
    suspend fun sendVerificationRequest(
        request: VerificationRequest,
        domain: String,
        challenge: String,
    )
    suspend fun preparePresentationProof(credential: CredentialType, message: MessageType)
    fun toUiCredential(sdkCredential: CredentialType): Credential
    suspend fun toSdkCredential(credential: Credential): CredentialType
    fun getLocalCredentials(): Flow<List<Credential>>
}