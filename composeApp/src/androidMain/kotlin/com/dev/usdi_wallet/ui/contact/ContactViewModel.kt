package com.dev.usdi_wallet.ui.contact

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.common.ErrorHandler
import com.dev.usdi_wallet.domain.contact.Contact
import com.dev.usdi_wallet.hyperledger_identus.IdentusAnonProtocol
import com.dev.usdi_wallet.domain.protocol.Protocol
import com.dev.usdi_wallet.eudi.EudiProtocol
import com.dev.usdi_wallet.ui.common.QrCodeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.collections.emptyList

class ContactViewModel(application: Application) : AndroidViewModel(application) {
    private val protocols = listOf(
        IdentusAnonProtocol.getInstance(),
        EudiProtocol.getInstance(),
    )
    val contacts: StateFlow<List<Contact>> = if (protocols.isEmpty()) {
        MutableStateFlow(emptyList())
    } else {
        combine(
            protocols.map { protocol ->
                protocol.contactManager.getContacts()
                    .onStart { emit(emptyList()) }
                    .catch { error ->
                        Logger.w(ContactViewModel::class.toString()) {
                            "${protocol.protocolId} contacts unavailable: ${error.message}"
                        }
                        emit(emptyList())
                    }
            }
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

    fun onCameraPermissionDenied() {
        _uiState.update { it.copy(error = "Camera permission denied") }
    }

    fun onCameraUnavailable() {
        _uiState.update { it.copy(error = "Unable to open camera") }
    }

    fun extractInvitationFromQr(uri: Uri?) {
        if (uri == null) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val invitation = QrCodeUtils.extractQrText(getApplication(), uri).trim()
                if (invitation.isBlank()) {
                    _uiState.update { it.copy(isLoading = false, error = "QR code did not contain an invitation") }
                    return@launch
                }
                acceptInvitation(invitation)
            } catch (e: Exception) {
                Logger.e(ContactViewModel::class.toString()) {
                    "QR extraction error: ${e.message}"
                }
                _uiState.update {
                    it.copy(isLoading = false, error = ErrorHandler.handleError("Failed to extract QR invitation", e))
                }
            }
        }
    }

    fun submitInvitation(invitation: String) {
        Logger.d(ContactViewModel::class.toString()) {
            "Received invitation: $invitation"
        }
        val trimmed = invitation.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(error = "Empty invitation") }
            return
        }

        _uiState.update { it.copy(isLoading = true, showInvitationDialog = false, error = null) }

        viewModelScope.launch {
            acceptInvitation(trimmed)
        }
    }

    private suspend fun acceptInvitation(invitation: String) {
        try {
            _uiState.update { it.copy(isLoading = true, showInvitationDialog = false, error = null) }
            val protocol = protocols.firstOrNull { it.contactManager.canHandle(invitation) }
                ?: error("Unsupported invitation format")
            Logger.d(ContactViewModel::class.toString()) {
                "The invitation will be handled by ${protocol.protocolId}"
            }
            protocol.contactManager.parseInvitation(invitation)
            _uiState.update { it.copy(isLoading = false, snackbarMessage = "Invitation accepted") }
        } catch (e: Exception) {
            Logger.e(ContactViewModel::class.toString()) {
                "Invitation error: ${e.message}"
            }
            _uiState.update {
                it.copy(isLoading = false, error = ErrorHandler.handleError("Failed to parse invitation", e))
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

    fun onScanError(message: String) {
        _uiState.update { it.copy(error = message) }
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
