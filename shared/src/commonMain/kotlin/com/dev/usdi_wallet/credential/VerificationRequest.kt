package com.dev.usdi_wallet.credential

data class VerificationRequest(
    val destination: String,
    val claims: List<Claim> = emptyList(),
    val predicates: List<Predicate> = emptyList(),
    val schema: String? = null,
    val issuer: String? = null,
)