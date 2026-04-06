package com.dev.usdi_wallet.credential

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