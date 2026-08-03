package com.dev.usdi_wallet.ui.common

private val SYSTEM_CLAIMS = setOf("index", "credentialid")

fun isSystemIndexClaim(name: String): Boolean =
    claimNameWithoutKnownTypeSuffix(name)
        .filterNot { it == '_' || it == '-' }
        .lowercase() in SYSTEM_CLAIMS

fun isUserVisibleClaim(name: String): Boolean =
    !isSystemIndexClaim(name)
