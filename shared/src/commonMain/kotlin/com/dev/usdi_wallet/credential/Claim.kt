package com.dev.usdi_wallet.credential

data class Claim(
    val name: String,
    val type: String,
    val pattern: String? = null,
    val enum: List<Any>? = null,
    val const: List<Any>? = null,
    val value: Any? = null,
)