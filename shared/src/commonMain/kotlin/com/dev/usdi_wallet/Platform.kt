package com.dev.usdi_wallet

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform