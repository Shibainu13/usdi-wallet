package com.dev.usdi_wallet.domain.contact

import kotlin.time.Clock
import kotlin.time.Instant

data class Contact(
    val holder: String,
    val name: String,
    val protocol: String,
    val addedAt: Instant = Clock.System.now(),
)
