package com.dev.usdi_wallet.ui.contact

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.connection.ConnectionManager
import com.dev.usdi_wallet.connection.hyperledger_identus.connection.IdentusDIDCommConnectionManager
import com.dev.usdi_wallet.connection.hyperledger_identus.contact.IdentusDIDCommContactManager
import com.dev.usdi_wallet.contact.Contact
import com.dev.usdi_wallet.contact.ContactManager
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
    private val contactManagers: List<ContactManager> = listOf(
        IdentusDIDCommContactManager(viewModelScope),
    )
    private val connectionManagers: List<ConnectionManager> = listOf(
        IdentusDIDCommConnectionManager(viewModelScope, application),
    )
    val contacts: StateFlow<List<Contact>> = if (contactManagers.isEmpty()) {
        MutableStateFlow(emptyList())
    } else {
        combine(
            contactManagers.map { it.getContacts() }
        ) { contactArrays ->
            contactArrays.toList().flatten()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
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
        Logger.d(this::class.toString()) {"Received invitation: $invitation"}
        val trimmed = invitation.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(error = "Empty invitation") }
            return
        }

        _uiState.update { it.copy(isLoading = true, showInvitationDialog = false, error = null) }

        viewModelScope.launch {
            try {
                val contactManager = contactManagers.first { it.canHandle(trimmed) }
                Logger.d(this::class.toString()) {"The invitation will be handled by ${contactManager.id}"}
                contactManager.parseInvitation(trimmed)
                _uiState.update { it.copy(isLoading = false, snackbarMessage = "Invitation accepted") }
            } catch (e: Exception) {
                Logger.e(this::class.toString()) { "Invitation error: ${e.message}" }
                _uiState.update { it.copy(isLoading = false, error = "Failed to parse invitation: ${e.message}") }
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