package com.dev.usdi_wallet.ui.credential

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.credential.Credential
import com.dev.usdi_wallet.credential.CredentialManager
import com.dev.usdi_wallet.hyperledger_identus.IdentusJWTCredentialManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class CredentialUiState(
    val error: String? = null,
    val selectedCredential: Credential? = null,
)

class CredentialViewModel(application: Application) : AndroidViewModel(application) {
    private val credentialManagers: List<CredentialManager> = listOf(
        IdentusJWTCredentialManager(viewModelScope),
    )
    private val _uiState = MutableStateFlow(CredentialUiState())
    val uiState: StateFlow<CredentialUiState> = _uiState.asStateFlow()

    val credentials: StateFlow<List<Credential>> = if (credentialManagers.isEmpty()) {
        MutableStateFlow(emptyList())
    } else {
        combine(
            credentialManagers.map { it.getCredentials() }
        ) { arrays ->
            arrays.toList().flatten()
        }
        .catch { e ->
            Logger.e(this::class.toString()) { "Failed to get credentials $e" }
            _uiState.update { it.copy(error = "Failed to load credentials: $e") }
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun onCredentialClicked(credential: Credential) {
        _uiState.update { it.copy(selectedCredential = credential) }
    }

    fun onDetailDismissed() {
        _uiState.update { it.copy(selectedCredential = null) }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(error = null) }
    }
}