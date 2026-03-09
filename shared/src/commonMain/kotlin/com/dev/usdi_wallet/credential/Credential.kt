package com.dev.usdi_wallet.credential

data class Credential(
    val id: String,
    val issuer: String,
    val subject: String?,
    val claims: String?,
    val raw: String,
    val protocol: String,
)
