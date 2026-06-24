package com.dev.usdi_wallet.ui.onboarding

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dev.usdi_wallet.domain.auth.AndroidWalletAuthManager
import com.dev.usdi_wallet.domain.backup.UnifiedBackupService
import com.dev.usdi_wallet.domain.protocol.Protocol
import com.dev.usdi_wallet.eudi.EudiProtocol
import com.dev.usdi_wallet.hyperledger_identus.IdentusAnonProtocol
import com.dev.usdi_wallet.preferences.WalletPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class OnboardingStep {
    object Welcome : OnboardingStep()
    object CreatePassphrase : OnboardingStep()
    object RestoreWallet : OnboardingStep()
    object BiometricSetup : OnboardingStep()
    object Complete : OnboardingStep()
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val passphrase: String = "",
    val passphraseConfirm: String = "",
    val passphraseError: String? = null,
    val restoreError: String? = null,
    val biometricSuccess: Boolean = false,
    val isLoading: Boolean = false
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = WalletPreferences.getInstance(application)
    private val authManager = AndroidWalletAuthManager.getInstance()
    private val protocols = listOf<Protocol<*,*>>(
        IdentusAnonProtocol.getInstance(application, viewModelScope),
        EudiProtocol.getInstance(application, viewModelScope),
    )
    private val backupService = UnifiedBackupService.getInstance(protocols)
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onCreateNewWallet() {
        _uiState.value = _uiState.value.copy(step = OnboardingStep.CreatePassphrase)
    }

    fun onRestoreWallet() {
        _uiState.value = _uiState.value.copy(step = OnboardingStep.RestoreWallet)
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

    fun onRestoreConfirmed(fileUri: Uri?, passphrase: String) {
        if (fileUri == null) {
            _uiState.value = _uiState.value.copy(restoreError = "Please select a backup file")
            return
        }
        if (passphrase.isBlank()) {
            _uiState.value = _uiState.value.copy(restoreError = "Please enter your passphrase")
            return
        }

        viewModelScope.launch {
            _uiState.value = uiState.value.copy(isLoading = true, restoreError = null)

            val encrypted = runCatching {
                getApplication<Application>().contentResolver
                    .openInputStream(fileUri)
                    ?.bufferedReader()
                    ?.readText()
            }.getOrNull()

            if (encrypted == null) {
                _uiState.value = uiState.value.copy(
                    isLoading = false,
                    restoreError = "Could not read backup file"
                )
                return@launch
            }

            val result = backupService.restoreEncrypted(encrypted, passphrase)

            if (result.error != null) {
                _uiState.value = _uiState.value.copy(isLoading = false, restoreError = result.error)
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    passphrase = passphrase,
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