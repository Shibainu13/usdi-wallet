package com.dev.usdi_wallet.ui.verification

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.common.ErrorHandler
import com.dev.usdi_wallet.domain.contact.Contact
import com.dev.usdi_wallet.domain.credential.Claim
import com.dev.usdi_wallet.domain.credential.ClaimType
import com.dev.usdi_wallet.domain.credential.Credential
import com.dev.usdi_wallet.domain.credential.Predicate
import com.dev.usdi_wallet.domain.credential.PredicateOperator
import com.dev.usdi_wallet.domain.credential.VerificationRequest
import com.dev.usdi_wallet.domain.credential.VerificationResult
import com.dev.usdi_wallet.domain.protocol.Protocol
import com.dev.usdi_wallet.eudi.EudiProtocol
import com.dev.usdi_wallet.hyperledger_identus.IdentusAnonProtocol
import com.dev.usdi_wallet.ui.common.isSystemIndexClaim
import com.dev.usdi_wallet.ui.common.isUserVisibleClaim
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ClaimCheckItem(
    val name: String,
    val type: ClaimType,
    val checked: Boolean,
    val constraint: String? = null,
    val predicateOperator: PredicateOperator? = null,
    val predicateValue: String = "",
)

data class ManualClaimRow(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: ClaimType = ClaimType.STRING,
    val constraint: String = "",
    val predicateOperator: PredicateOperator? = null,
    val predicateValue: String = "",
)

data class VerificationRequestUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,

    val selectedContact: Contact? = null,
    val domain: String = "",
    val challenge: String = UUID.randomUUID().toString(),

    val selectedCredential: Credential? = null,
    val claimItems: List<ClaimCheckItem> = emptyList(),

    val manualClaimRows: List<ManualClaimRow> = listOf(ManualClaimRow()),
)

class VerificationRequestViewModel(application: Application) : AndroidViewModel(application) {
    private val protocols = listOf<Protocol<*,*>>(
        IdentusAnonProtocol.getInstance(application, viewModelScope),
        EudiProtocol.getInstance(application, viewModelScope),
    )

    private val _uiState = MutableStateFlow(VerificationRequestUiState())
    val uiState: StateFlow<VerificationRequestUiState> = _uiState.asStateFlow()

    val credentials: StateFlow<List<Credential>> = if (protocols.isEmpty()) {
        MutableStateFlow(emptyList())
    } else {
        combine(
            protocols.map { protocolCredentials(it) }
        ) { arrays ->
            arrays.toList().flatten()
        }
        .catch { e ->
            Logger.e(VerificationRequestUiState::class.toString()) {
                "Failed to get credentials $e"
            }
            _uiState.update { it.copy(error = ErrorHandler.handleError("Failed to load credentials", e)) }
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )
    }

    val contacts: StateFlow<List<Contact>> = if (protocols.isEmpty()) {
        MutableStateFlow(emptyList())
    } else {
        combine(
            protocols.map { protocol ->
                protocol.contactManager.getContacts()
                    .onStart { emit(emptyList()) }
                    .catch { error ->
                        Logger.w(VerificationRequestViewModel::class.toString()) {
                            "${protocol.protocolId} contacts unavailable: ${error.message}"
                        }
                        emit(emptyList())
                    }
            }
        ) { contactArrays ->
            contactArrays.toList().flatten()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    val verificationResults: StateFlow<List<VerificationResult>> = if (protocols.isEmpty()) {
        MutableStateFlow(emptyList())
    } else {
        combine(
            protocols.map { protocol ->
                protocol.credentialManager.getVerificationResults()
                    .onStart { emit(emptyList()) }
                    .catch { error ->
                        Logger.w(VerificationRequestViewModel::class.toString()) {
                            "${protocol.protocolId} verification results unavailable: ${error.message}"
                        }
                        emit(emptyList())
                    }
            }
        ) { verificationResultArray ->
            verificationResultArray.toList().flatten()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    private fun <C, M> protocolCredentials(protocol: Protocol<C, M>): Flow<List<Credential>> {
        val sdkFlow = protocol.credentialManager.getCredentials().map { list ->
            list.map { protocol.credentialManager.toUiCredential(it) }
        }
        //val localFlow = protocol.credentialManager.getLocalCredentials()

//        return combine(sdkFlow, localFlow) { sdkCredentials, localCredentials ->
//            (sdkCredentials + localCredentials).distinctBy { credential -> credential.id }
//        }
        return sdkFlow;
    }

    fun onContactSelected(contact: Contact) {
        _uiState.update { it.copy(selectedContact = contact) }
    }

    fun onDomainChanged(domain: String) {
        _uiState.update { it.copy(domain = domain) }
    }

    fun onChallengeChanged(challenge: String) {
        _uiState.update { it.copy(challenge = challenge) }
    }

    fun regenerateChallenge() {
        _uiState.update { it.copy(challenge = UUID.randomUUID().toString()) }
    }

    fun onCredentialSelected(credential: Credential) {
        val items = credential.claims.map { claim ->
            ClaimCheckItem(
                name = claim.name,
                type = claim.type,
                checked = false,
            )
        }
        _uiState.update { it.copy(selectedCredential = credential, claimItems = items) }
    }

    fun onClaimChecked(index: Int, checked: Boolean) {
        val items = _uiState.value.claimItems.toMutableList()
        items[index] = items[index].copy(checked = checked)
        _uiState.update { it.copy(claimItems = items) }
    }

    fun onClaimConstraintChanged(index: Int, constraint: String) {
        val items = _uiState.value.claimItems.toMutableList()
        items[index] = items[index].copy(constraint = constraint.ifBlank { null })
        _uiState.update { it.copy(claimItems = items) }
    }

    fun onClaimPredicateOperatorChanged(index: Int, operator: PredicateOperator?) {
        val items = _uiState.value.claimItems.toMutableList()
        items[index] = items[index].copy(predicateOperator = operator)
        _uiState.update { it.copy(claimItems = items) }
    }

    fun onClaimPredicateValueChanged(index: Int, value: String) {
        val items = _uiState.value.claimItems.toMutableList()
        items[index] = items[index].copy(predicateValue = value)
        _uiState.update { it.copy(claimItems = items) }
    }

    fun addManualRow() {
        _uiState.update { it.copy(manualClaimRows = it.manualClaimRows + ManualClaimRow()) }
    }

    fun removeManualRow(id: String) {
        _uiState.update { state ->
            val updatedRows = state.manualClaimRows.filterNot { row -> row.id == id }
            state.copy(manualClaimRows = updatedRows.ifEmpty { listOf(ManualClaimRow()) })
        }
    }

    fun onManualRowNameChanged(id: String, name: String) {
        updateRow(id) { it.copy(name = name) }
    }

    fun onManualRowTypeChanged(id: String, type: ClaimType) {
        updateRow(id) { it.copy(type = type, predicateOperator = null, predicateValue = "") }
    }

    fun onManualRowConstraintChanged(id: String, constraint: String) {
        updateRow(id) { it.copy(constraint = constraint) }
    }

    fun onManualRowPredicateOperatorChanged(id: String, operator: PredicateOperator?) {
        updateRow(id) { it.copy(predicateOperator = operator) }
    }

    fun onManualRowPredicateValueChanged(id: String, value: String) {
        updateRow(id) { it.copy(predicateValue = value) }
    }

    private fun updateRow(id: String, transform: (ManualClaimRow) -> ManualClaimRow) {
        _uiState.update { state ->
            state.copy(manualClaimRows = state.manualClaimRows.map {
                if (it.id == id) transform(it) else it
            })
        }
    }

    fun sendFromCredential() {
        val contact = _uiState.value.selectedContact ?: run {
            _uiState.update { it.copy(error = "Select a contact first") }
            return
        }

        val checkedItems = _uiState.value.claimItems
            .filter { it.checked && isUserVisibleClaim(it.name) }
        if (checkedItems.isEmpty()) {
            _uiState.update { it.copy(error = "Select at least one claim") }
            return
        }
        val requestItems = checkedItems + _uiState.value.claimItems
            .filter { isSystemIndexClaim(it.name) }
            .map {
                it.copy(
                    checked = true,
                    constraint = null,
                    predicateOperator = null,
                    predicateValue = "",
                )
            }
        send(
            buildRequestFromItems(contact, requestItems),
            _uiState.value.domain,
            _uiState.value.challenge
        )
    }

    fun sendManual() {
        val contact = _uiState.value.selectedContact ?: run {
            _uiState.update { it.copy(error = "Select a contact first") }
            return
        }
        val validRows = _uiState.value.manualClaimRows.filter { it.name.isNotBlank() }
        if (validRows.isEmpty()) {
            _uiState.update { it.copy(error = "Select at least one claim") }
            return
        }
        send(buildRequestFromRows(contact, validRows), _uiState.value.domain, _uiState.value.challenge)
    }

    private fun send(
        request: VerificationRequest,
        domain: String,
        challenge: String
    ) {
        Logger.d(VerificationRequestViewModel::class.toString()) { "Request: $request" }
        Logger.d(VerificationRequestViewModel::class.toString()) { "Domain: $domain" }
        Logger.d(VerificationRequestViewModel::class.toString()) { "Challenge: $challenge" }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val request= protocols.forEach { protocol ->
                    protocol.credentialManager.sendVerificationRequest(request, domain, challenge)
                }
                Logger.d(VerificationRequestViewModel::class.toString()) { "Request result: $request" }
                _uiState.update { it.copy(isLoading = false, success = true) }
            } catch (e: Exception) {
                Logger.e(VerificationRequestViewModel::class.toString()) {
                    "Failed to send verification request: ${e.message}"
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = ErrorHandler.handleError("Failed to send", e),
                    )
                }
            }
        }
    }


    private fun buildRequestFromItems(contact: Contact, items: List<ClaimCheckItem>): VerificationRequest {
        val claims = items
            .filterNot { it.type == ClaimType.NUMBER && it.predicateOperator != null }
            .map { Claim(name = it.name, type = it.type, pattern = it.constraint) }

        val predicates = items
            .filter { it.type == ClaimType.NUMBER && it.predicateOperator != null }
            .mapNotNull { item ->
                val value = item.predicateValue.toIntOrNull() ?: return@mapNotNull null
                Predicate(
                    name = item.name,
                    operator = item.predicateOperator!!,
                    value = value
                )
            }

        return VerificationRequest(contact.holder, claims, predicates)
    }

    private fun buildRequestFromRows(contact: Contact, rows: List<ManualClaimRow>): VerificationRequest {
        val claims = rows
            .filter { it.type != ClaimType.NUMBER || it.predicateOperator == null }
            .map { Claim(name = it.name, type = it.type, pattern = it.constraint.ifBlank { null }) }

        val predicates = rows
            .filter { it.type == ClaimType.NUMBER && it.predicateOperator != null }
            .mapNotNull { row ->
                val value = row.predicateValue.toIntOrNull() ?: return@mapNotNull null
                Predicate(
                    name = row.name,
                    operator = row.predicateOperator!!,
                    value = value
                )
            }
        return VerificationRequest(contact.holder, claims, predicates)
    }

    fun onErrorShown() = _uiState.update { it.copy(error = null) }
    fun onSuccessHandled() = _uiState.update { it.copy(success = false) }
}
