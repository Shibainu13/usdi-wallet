package com.dev.usdi_wallet.ui.onboarding

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dev.usdi_wallet.common.ErrorHandler
import com.dev.usdi_wallet.domain.auth.AndroidWalletAuthManager
import com.dev.usdi_wallet.domain.backup.UnifiedBackupService
import com.dev.usdi_wallet.eudi.EudiProtocol
import com.dev.usdi_wallet.hyperledger_identus.IdentusAnonProtocol
import com.dev.usdi_wallet.preferences.AndroidWalletPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class OnboardingStep {
    object Welcome : OnboardingStep()
    object RestoreWallet : OnboardingStep()
    object BiometricSetup : OnboardingStep()
    object Complete : OnboardingStep()
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val passphrase: String = "",
    val restoreError: String? = null,
    val biometricSuccess: Boolean = false,
    val restoreSkippedProtocols: List<String> = emptyList(),
    val isLoading: Boolean = false
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = AndroidWalletPreferences.getInstance(application)
    private val authManager = AndroidWalletAuthManager.getInstance()
    private val protocols = listOf(
        IdentusAnonProtocol.getInstance(),
        EudiProtocol.getInstance(),
    )
    private val backupService = UnifiedBackupService.getInstance(protocols)
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onCreateNewWallet() {
        _uiState.value = _uiState.value.copy(step = OnboardingStep.BiometricSetup)
    }

    fun onRestoreWallet() {
        _uiState.value = _uiState.value.copy(step = OnboardingStep.RestoreWallet)
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
            }.getOrElse { error ->
                _uiState.value = uiState.value.copy(
                    isLoading = false,
                    restoreError = ErrorHandler.handleError("Could not read backup file", error)
                )
                return@launch
            }

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
                    restoreSkippedProtocols = result.skipped,
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

    private suspend fun completeOnboarding() {
        preferences.setOnboardingComplete()
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            biometricSuccess = true,
            step = OnboardingStep.Complete,
        )
    }
}
