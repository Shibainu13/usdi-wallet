package com.dev.usdi_wallet.domain.preferences

import kotlinx.coroutines.flow.Flow

interface WalletPreferences {
    val isOnboardingComplete: Flow<Boolean>
    suspend fun setOnboardingComplete()
}