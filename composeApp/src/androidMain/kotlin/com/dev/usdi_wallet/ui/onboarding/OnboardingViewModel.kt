package com.dev.usdi_wallet.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dev.usdi_wallet.domain.auth.AndroidWalletAuthManager
import com.dev.usdi_wallet.preferences.WalletPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class OnboardingStep {
    object Welcome : OnboardingStep()
    object CreatePassphrase : OnboardingStep()
    object BiometricSetup : OnboardingStep()
    object Complete : OnboardingStep()
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val passphrase: String = "",
    val passphraseConfirm: String = "",
    val passphraseError: String? = null,
    val biometricSuccess: Boolean = false,
    val isLoading: Boolean = false
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = WalletPreferences.getInstance(application)
    private val authManager = AndroidWalletAuthManager.getInstance()

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onGetStarted() {
        _uiState.value = _uiState.value.copy(step = OnboardingStep.CreatePassphrase)
    }

    fun onPassphraseConfirmed() {
        val state = _uiState.value
        when {
            state.passphrase.length < 8 -> _uiState.value = state.copy(
                passphraseError = "Passphrase must be at least 8 characters"
            )
            state.passphrase != state.passphraseConfirm -> _uiState.value = state.copy(
                passphraseError = "Passphrases do not match"
            )
            else -> {
                _uiState.value = state.copy(
                    passphraseError = null,
                    step = OnboardingStep.BiometricSetup,
                )
            }
        }
    }

    fun onBiometricSetup() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val success = authManager.requestAuth()
            if (success) {
                completeOnboarding()
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun onPassPhraseChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            passphrase = value,
            passphraseError = null,
        )
    }

    fun onPassphraseConfirmChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            passphraseConfirm = value,
            passphraseError = null,
        )
    }

    private suspend fun completeOnboarding() {
        preferences.setOnboardingComplete()
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            biometricSuccess = true,
            step = OnboardingStep.Complete,
        )
    }
}