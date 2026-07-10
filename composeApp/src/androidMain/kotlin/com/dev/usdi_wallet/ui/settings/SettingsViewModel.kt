package com.dev.usdi_wallet.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dev.usdi_wallet.domain.backup.UnifiedBackupService
import com.dev.usdi_wallet.eudi.EudiProtocol
import com.dev.usdi_wallet.hyperledger_identus.IdentusAnonProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class SettingsScreenState {
    data object Main : SettingsScreenState()
    data object Backup : SettingsScreenState()
    data object Restore : SettingsScreenState()
    data object About : SettingsScreenState()
}

data class SettingsUiState(
    val screen: SettingsScreenState = SettingsScreenState.Main,

    // Backup
    val backupPassphrase: String = "",
    val backupPassphraseConfirm: String = "",
    val backupError: String? = null,
    val backupEncryptedPayload: String? = null,
    val backupComplete: Boolean = false,

    // Restore
    val restorePassphrase: String = "",
    val restoreError: String? = null,
    val restoreSucceeded: List<String> = emptyList(),
    val restoreFailed: List<String> = emptyList(),
    val restoreSkipped: List<String> = emptyList(),
    val restoreComplete: Boolean = false,
    val restoreConfirmPending: Boolean = false,
    val pendingRestoreUri: Uri? = null,

    val isLoading: Boolean = false,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val protocols = listOf(
        IdentusAnonProtocol.getInstance(),
        EudiProtocol.getInstance(),
    )
    private val backupService = UnifiedBackupService.getInstance(protocols)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    fun onBackupClicked() {
        _uiState.value = _uiState.value.copy(screen = SettingsScreenState.Backup)
    }

    fun onRestoreClicked() {
        _uiState.value = _uiState.value.copy(screen = SettingsScreenState.Restore)
    }

    fun onAboutClicked() {
        _uiState.value = _uiState.value.copy(screen = SettingsScreenState.About)
    }

    fun onBackToMain() {
        _uiState.value = _uiState.value.copy(screen = SettingsScreenState.Main)
    }

    fun onBackupPassphraseChanged(value: String) {
        _uiState.value = _uiState.value.copy(backupPassphrase = value, backupError = null)
    }

    fun onBackupPassphraseConfirmChanged(value: String) {
        _uiState.value = _uiState.value.copy(backupPassphraseConfirm = value, backupError = null)
    }

    fun onCreateBackup() {
        val state = _uiState.value
        when {
            state.backupPassphrase.length < 8 -> {
                _uiState.value = _uiState.value.copy(backupError = "Passphrase must be at least 8 characters")
                return
            }
            state.backupPassphrase != state.backupPassphraseConfirm -> {
                _uiState.value = _uiState.value.copy(backupError = "Passphrases do not match")
            }
        }

        _uiState.value = _uiState.value.copy(isLoading = true, backupError = null)
        viewModelScope.launch {
            val encrypted = runCatching { backupService.exportEncrypted(state.backupPassphrase) }
                .getOrElse {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            backupError = "Failed to create backup: $it"
                        )
                    }
                    return@launch
                }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                backupEncryptedPayload = encrypted,
            )
        }
    }

    fun onBackupSaved() {
        _uiState.value = _uiState.value.copy(backupComplete = true)
    }

    fun onRestorePassphraseChanged(value: String) {
        _uiState.value = _uiState.value.copy(restorePassphrase = value, restoreError = null)
    }

    fun onRestoreFileSelected(uri: Uri?) {
        if (uri == null) return
        _uiState.value = _uiState.value.copy(pendingRestoreUri = uri, restoreConfirmPending = true)
    }

    fun onRestoreConfirmed() {
        val state = _uiState.value
        val uri = state.pendingRestoreUri ?: return
        if (state.restorePassphrase.isBlank()) {
            _uiState.value = _uiState.value.copy(
                restoreConfirmPending = false,
                restoreError = "Enter your passphrase"
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            restoreError = null,
            restoreConfirmPending = false,
        )
        viewModelScope.launch {
            val encrypted = runCatching {
                getApplication<Application>().contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.readText()
            }.getOrNull()

            if (encrypted == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    restoreError = "Could not read backup file",
                )
                return@launch
            }

            val result = backupService.restoreEncrypted(encrypted, state.restorePassphrase)

            if (result.error != null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    restoreError = result.error,
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    restoreSucceeded = result.succeeded,
                    restoreFailed = result.failed,
                    restoreSkipped = result.skipped,
                    restoreComplete = true,
                )
            }
        }
    }

    fun onRestoreCancelled() {
        _uiState.value = _uiState.value.copy(
            restoreConfirmPending = false,
            pendingRestoreUri = null
        )
    }

    fun onErrorShown() {
        _uiState.value = _uiState.value.copy(
            backupError = null,
            restoreError = null,
        )
    }
}