package com.dev.usdi_wallet.ui.credential

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.credential.Credential
import com.dev.usdi_wallet.hyperledger_identus.IdentusJWTProtocol
import com.dev.usdi_wallet.protocol.Protocol
import com.dev.usdi_wallet.contact.Contact
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
import kotlinx.coroutines.launch

data class CredentialUiState(
    val error: String? = null,
    val selectedCredential: Credential? = null,
    val showInvitationDialog: Boolean = false,
    val isLoading: Boolean = false,
    val snackbarMessage: String? = null,
)

class CredentialViewModel(application: Application) : AndroidViewModel(application) {
    private val protocols = listOf<Protocol<*,*>>(
        IdentusJWTProtocol.getInstance(application),
    )
    private val _uiState = MutableStateFlow(CredentialUiState())
    val uiState: StateFlow<CredentialUiState> = _uiState.asStateFlow()
    val contacts: StateFlow<List<Contact>> = if (protocols.isEmpty()) {
        MutableStateFlow(emptyList())
    } else {
        combine(
            protocols.map { it.contactManager.getContacts() }
        ) { arrays ->
            arrays.toList().flatten()
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )
    }
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

    private fun <C, M> protocolCredentials(protocol: Protocol<C, M>): Flow<List<Credential>>  {
        val sdkFlow = protocol.credentialManager.getCredentials().map { list ->
            list.map { protocol.credentialManager.toUiCredential(it) }
        }

        val localFlow = protocol.credentialManager.getLocalCredentials()

        return combine(sdkFlow, localFlow) { sdkList, localList ->
            (sdkList + localList)
                .distinctBy { it.id } // avoid duplicates
        }
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
    fun onAddContactClicked() {
        _uiState.update { it.copy(showInvitationDialog = true) }
    }

    fun onInvitationDialogDismissed() {
        _uiState.update { it.copy(showInvitationDialog = false) }
    }

    fun submitInvitation(invitation: String) {
        val trimmed = invitation.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(error = "Empty invitation") }
            return
        }

        _uiState.update { it.copy(isLoading = true, showInvitationDialog = false) }

        viewModelScope.launch {
            try {
                val protocol = protocols.first { it.contactManager.canHandle(trimmed) }
                protocol.contactManager.parseInvitation(trimmed)

                _uiState.update {
                    it.copy(isLoading = false, snackbarMessage = "Invitation accepted")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed: ${e.message}")
                }
            }
        }
    }

    fun onSnackbarShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}