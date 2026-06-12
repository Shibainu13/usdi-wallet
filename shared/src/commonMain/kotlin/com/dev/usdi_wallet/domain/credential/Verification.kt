package com.dev.usdi_wallet.domain.credential

import kotlin.time.Clock

data class VerificationRequest(
    val destination: String,
    val claims: List<Claim> = emptyList(),
    val predicates: List<Predicate> = emptyList(),
    val schema: String? = null,
    val issuer: String? = null,
)

data class VerificationResult(
    val messageId: String,
    val isValid: Boolean,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
)

data class ProofRequestDetails(
    val verifier: String,
    val name: String? = null,
    val requestedFields: List<ProofRequestField> = emptyList(),
)

data class ProofRequestField(
    val name: String,
    val requirement: String? = null,
)
