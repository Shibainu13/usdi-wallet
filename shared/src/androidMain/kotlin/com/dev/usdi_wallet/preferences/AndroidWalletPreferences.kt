package com.dev.usdi_wallet.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.dev.usdi_wallet.domain.preferences.WalletPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.datastore: DataStore<Preferences> by preferencesDataStore(
    name = "wallet_preferences"
)

class AndroidWalletPreferences(
    private val context: Context
) : WalletPreferences {
    private val isOnboardingCompleteKey = booleanPreferencesKey("is_onboarding_complete")

    override val isOnboardingComplete: Flow<Boolean> = context.datastore.data.map { it[isOnboardingCompleteKey] ?: false }

    override suspend fun setOnboardingComplete() {
        context.datastore.edit { it[isOnboardingCompleteKey] = true }
    }

    companion object {
        private var _instance: AndroidWalletPreferences? = null

        fun getInstance(context: Context): AndroidWalletPreferences {
            return _instance ?: AndroidWalletPreferences(context.applicationContext).also { _instance = it }
        }
    }
}