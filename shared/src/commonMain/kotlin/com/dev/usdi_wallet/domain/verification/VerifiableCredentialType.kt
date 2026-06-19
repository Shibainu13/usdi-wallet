package com.dev.usdi_wallet.domain.verification

import com.dev.usdi_wallet.domain.credential.ClaimType

enum class VerificationProtocol { ANONCREDS, EUDI }
data class VerifiableCredentialType(
    val id: String,
    val label: String,
    val protocol: VerificationProtocol,
    val fields: List<VerifiableFieldSchema>,
    val metadata: Map<String, String> = emptyMap(),
)

data class VerifiableFieldSchema(
    val name: String,
    val label: String,
    val type: ClaimType
)