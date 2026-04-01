package com.dev.usdi_wallet.credential

enum class ClaimType {
    STRING, NUMBER, BOOLEAN, BYTEARRAY, NULL;

    override fun toString(): String = name.lowercase()
}

data class Claim(
    val name: String,
    val type: ClaimType,
    val pattern: String? = null,
    val enum: List<Any>? = null,
    val const: List<Any>? = null,
    val value: Any? = null,
)