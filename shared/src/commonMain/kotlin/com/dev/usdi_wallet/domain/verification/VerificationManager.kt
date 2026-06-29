package com.dev.usdi_wallet.domain.verification

import com.dev.usdi_wallet.domain.credential.ClaimType
import com.dev.usdi_wallet.domain.credential.PredicateOperator
import kotlinx.coroutines.flow.Flow

interface VerificationManager {
    suspend fun getSupportedCredentialTypes(): List<VerifiableCredentialType>
    suspend fun startVerification(credentialType: VerifiableCredentialType, requestedFields: List<RequestedField>): VerificationSession
    suspend fun cancelVerification(session: VerificationSession)
}

data class RequestedField(
    val field: VerifiableFieldSchema,
    val predicateOperator: PredicateOperator? = null,
    val predicateValue: String? = null,
)

data class VerificationSession(
    val sessionId: String,
    val qrContent: String,
    val results: Flow<VerificationPollResult>
)

sealed class VerificationPollResult {
    object Pending : VerificationPollResult()
    data class Success(val claims: Map<String, Any?>) : VerificationPollResult()
    data class Failed(val reason: String?) : VerificationPollResult()
}