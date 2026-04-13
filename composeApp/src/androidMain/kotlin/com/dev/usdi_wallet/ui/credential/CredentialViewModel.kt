package com.dev.usdi_wallet.ui.credential

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.domain.credential.Credential
import com.dev.usdi_wallet.hyperledger_identus.IdentusJWTProtocol
import com.dev.usdi_wallet.domain.protocol.Protocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class CredentialUiState(
    val error: String? = null,
    val selectedCredential: Credential? = null,
)

class CredentialViewModel(application: Application) : AndroidViewModel(application) {
    private val protocols = listOf<Protocol<*,*>>(
        IdentusJWTProtocol.getInstance(application),
    )
    private val _uiState = MutableStateFlow(CredentialUiState())
    val uiState: StateFlow<CredentialUiState> = _uiState.asStateFlow()

    val credentials: StateFlow<List<Credential>> = if (protocols.isEmpty()) {
        MutableStateFlow(emptyList())
    } else {
        combine(
            protocols.map { protocolCredentials(it) }
        ) { arrays ->
            arrays.toList().flatten()
        }
        .catch { e ->
            Logger.e(CredentialViewModel::class.toString()) {
                "Failed to get credentials $e"
            }
            _uiState.update { it.copy(error = "Failed to load credentials: $e") }
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    private fun <C, M> protocolCredentials(protocol: Protocol<C, M>): Flow<List<Credential>> =
        protocol.credentialManager.getCredentials().map { list ->
            list.map { protocol.credentialManager.toUiCredential(it) }
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