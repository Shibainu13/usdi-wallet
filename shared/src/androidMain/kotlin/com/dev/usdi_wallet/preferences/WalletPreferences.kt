package com.dev.usdi_wallet.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.datastore: DataStore<Preferences> by preferencesDataStore(
    name = "wallet_preferences"
)

class WalletPreferences(private val context: Context) {
    private val isOnboardingCompleteKey = booleanPreferencesKey("is_onboarding_complete")

    val isOnBoardingComplete: Flow<Boolean> = context.datastore.data.map { it[isOnboardingCompleteKey] ?: false }

    suspend fun setOnboardingComplete() {
        context.datastore.edit { it[isOnboardingCompleteKey] = true }
    }

    companion object {
        private var _instance: WalletPreferences? = null

        fun getInstance(context: Context): WalletPreferences {
            return _instance ?: WalletPreferences(context.applicationContext).also { _instance = it }
        }
    }
}