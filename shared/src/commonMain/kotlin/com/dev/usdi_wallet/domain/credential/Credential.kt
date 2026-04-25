package com.dev.usdi_wallet.domain.credential

import kotlin.time.Clock
import kotlin.time.Instant

data class Credential(
    val id: String,
    val issuer: String,
    val subject: String?,
    val claims: List<Claim> = emptyList(),
    val protocol: String,
    val issuedAt: Instant = Clock.System.now(),
    val expiresAt: Instant? = null,
    var revoked: Boolean = false,
)