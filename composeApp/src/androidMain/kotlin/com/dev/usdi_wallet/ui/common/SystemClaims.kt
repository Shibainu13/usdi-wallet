package com.dev.usdi_wallet.ui.common

private val SYSTEM_CLAIMS = setOf("index", "credentialid")
private val KNOWN_TYPE_SUFFIXES = setOf("str", "num", "bool", "date")

fun isSystemIndexClaim(name: String): Boolean =
    name.trim()
        .withoutKnownTypeSuffix()
        .filterNot { it == '_' || it == '-' }
        .lowercase() in SYSTEM_CLAIMS

fun isUserVisibleClaim(name: String): Boolean =
    !isSystemIndexClaim(name)

private fun String.withoutKnownTypeSuffix(): String {
    val suffix = substringAfterLast("_", missingDelimiterValue = "")
    return if (suffix.lowercase() in KNOWN_TYPE_SUFFIXES) {
        substringBeforeLast("_")
    } else {
        this
    }
}
