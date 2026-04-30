package com.dev.usdi_wallet.domain.credential

import com.dev.usdi_wallet.domain.connection.ConnectionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface CredentialManager<CredentialType, MessageType> {
    fun getCredentials(): Flow<List<CredentialType>>
    fun getProofRequestsToProcess(): Flow<List<MessageType>>
    fun getVerificationResults(): Flow<List<VerificationResult>>
    suspend fun getCredential(id: String): Credential?
    suspend fun saveCredential(credential: Credential)
    suspend fun removeCredential(id: String)
    suspend fun handleInbound(
        message: MessageType,
        connectionManager: ConnectionManager<MessageType>?,
    )
    suspend fun sendVerificationRequest(
        request: VerificationRequest,
        domain: String,
        challenge: String,
    )
    suspend fun preparePresentationProof(credential: CredentialType, message: MessageType)
    suspend fun getRevokedCredential(): StateFlow<List<CredentialType>>
    fun toUiCredential(sdkCredential: CredentialType): Credential
    suspend fun toSdkCredential(credential: Credential): CredentialType
    // fun getLocalCredentials(): Flow<List<Credential>>
}