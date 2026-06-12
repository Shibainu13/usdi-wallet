package com.dev.usdi_wallet.domain.auth

interface WalletAuthManager {
    suspend fun requestAuth(): Boolean
}