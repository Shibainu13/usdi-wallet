package com.dev.usdi_wallet.ui.contact

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.domain.contact.Contact
import com.dev.usdi_wallet.hyperledger_identus.IdentusJWTProtocol
import com.dev.usdi_wallet.domain.protocol.Protocol
import com.dev.usdi_wallet.eudi.EudiProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.collections.emptyList

class ContactViewModel(application: Application) : AndroidViewModel(application) {
    private val protocols = listOf<Protocol<*,*>>(
        IdentusJWTProtocol.getInstance(application, viewModelScope),
        EudiProtocol.getInstance(application, viewModelScope),
    )
    val contacts: StateFlow<List<Contact>> = if (protocols.isEmpty()) {
        MutableStateFlow(emptyList())
    } else {
        combine(
            protocols.map { it.contactManager.getContacts() }
        ) { contactArrays ->
            contactArrays.toList().flatten()
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )
    }

    private val _uiState = MutableStateFlow(ContactUiState())
    val uiState = _uiState.asStateFlow()

    fun onAddContactClicked() {
        _uiState.update { it.copy(showInvitationDialog = true) }
    }

    fun onInvitationDialogDismissed() {
        _uiState.update { it.copy(showInvitationDialog = false) }
    }

    fun submitInvitation(invitation: String) {
        Logger.d(ContactViewModel::class.toString()) {"Received invitation: $invitation"}
        val trimmed = invitation.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(error = "Empty invitation") }
            return
        }

        _uiState.update { it.copy(isLoading = true, showInvitationDialog = false, error = null) }

        viewModelScope.launch {
            try {
                val protocol = protocols.first { it.contactManager.canHandle(trimmed) }
                Logger.d(ContactViewModel::class.toString()) {
                    "The invitation will be handled by ${protocol.protocolId}"
                }
                protocol.contactManager.parseInvitation(trimmed)
                _uiState.update { it.copy(isLoading = false, snackbarMessage = "Invitation accepted") }
            } catch (e: Exception) {
                Logger.e(ContactViewModel::class.toString()) { "Invitation error: ${e.message}" }
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to parse invitation: ${e.message}")
                }
            }
        }
    }

    fun onSendMessageDialogDismissed() {
        _uiState.update { it.copy(showInvitationDialog = false, selectedContact = null) }
    }

    fun onSnackbarShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(error = null)}
    }
}

data class ContactUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val showInvitationDialog: Boolean = false,
    val showSendMessageDialog: Boolean = false,
    val selectedContact: Contact? = null,
    val snackbarMessage: String? = null,
)