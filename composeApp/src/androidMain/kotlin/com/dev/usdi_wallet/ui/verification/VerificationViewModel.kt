package com.dev.usdi_wallet.ui.verification

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.domain.credential.PredicateOperator
import com.dev.usdi_wallet.domain.protocol.Protocol
import com.dev.usdi_wallet.domain.verification.RequestedField
import com.dev.usdi_wallet.domain.verification.VerifiableCredentialType
import com.dev.usdi_wallet.domain.verification.VerifiableFieldSchema
import com.dev.usdi_wallet.domain.verification.VerificationManager
import com.dev.usdi_wallet.domain.verification.VerificationPollResult
import com.dev.usdi_wallet.domain.verification.VerificationSession
import com.dev.usdi_wallet.eudi.EudiProtocol
import com.dev.usdi_wallet.hyperledger_identus.IdentusAnonProtocol
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.collections.emptyList

sealed class VerificationStep {
    data object SelectCredentialType : VerificationStep()
    data object SelectFields : VerificationStep()
    data object ShowQrWaiting : VerificationStep()
    data object Result : VerificationStep()
}

data class FieldSelection(
    val schema: VerifiableFieldSchema,
    val checked: Boolean = false,
    val predicateOperator: PredicateOperator? = null,
    val predicateValue: String = ""
)

data class VerificationUiState(
    val step: VerificationStep = VerificationStep.SelectCredentialType,
    val availableCredentialTypes: List<VerifiableCredentialType> = emptyList(),
    val selectedCredentialType: VerifiableCredentialType? = null,
    val fieldSelections: List<FieldSelection> = emptyList(),
    val qrContent: String? = null,
    val pollResult: VerificationPollResult = VerificationPollResult.Pending,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class VerificationViewModel(application: Application) : AndroidViewModel(application) {
    private val protocols = listOf(
        IdentusAnonProtocol.getInstance(),
        EudiProtocol.getInstance(),
    )
    private val _uiState = MutableStateFlow(VerificationUiState())
    val uiState = _uiState.asStateFlow()
    private var activeSession: VerificationSession? = null
    private var pollJob: Job? = null
    private var credentialTypeToManager: Map<VerifiableCredentialType, VerificationManager?> = emptyMap()

    init {
        loadCredentialTypes()
    }

    private fun loadCredentialTypes() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val pairs = protocols.flatMap { protocol ->
                protocol.verificationManager?.getSupportedCredentialTypes().orEmpty().map { credentialType ->
                    credentialType to protocol.verificationManager
                }
            }
            credentialTypeToManager = pairs.toMap()
            _uiState.update { it.copy(isLoading = false, availableCredentialTypes = pairs.map { pair -> pair.first }) }
        }
    }

    fun onCredentialTypeSelected(credentialType: VerifiableCredentialType) {
        _uiState.value = _uiState.value.copy(
            selectedCredentialType = credentialType,
            fieldSelections = credentialType.fields.map { schema -> FieldSelection(schema) },
            step = VerificationStep.SelectFields,
        )
    }

    fun onFieldChecked(fieldName: String, checked: Boolean) {
        updateField(fieldName) { it.copy(checked = checked) }
    }

    fun onFieldPredicateOperatorChanged(fieldName: String, operator: PredicateOperator?) {
        updateField(fieldName) { it.copy(predicateOperator = operator) }
    }

    fun onFieldPredicateValueChanged(fieldName: String, value: String) {
        updateField(fieldName) { it.copy(predicateValue = value) }
    }

    private fun updateField(fieldName: String, transform: (FieldSelection) -> FieldSelection) {
        _uiState.update { state ->
            state.copy(
                fieldSelections = state.fieldSelections.map {
                    if (it.schema.name == fieldName) transform(it) else it
                },
            )
        }
    }

    fun onBackToCredentialTypes() {
        _uiState.value = _uiState.value.copy(
            step = VerificationStep.SelectCredentialType,
            selectedCredentialType = null,
            fieldSelections = emptyList(),
        )
    }

    fun onStartVerification() {
        val state = _uiState.value
        val credentialType = state.selectedCredentialType ?: run {
            _uiState.value = _uiState.value.copy(
                error = "Select a credential type first"
            )
            return
        }
        val selectedFields = state.fieldSelections.filter { it.checked }
        if (selectedFields.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                error = "Select at least one field"
            )
            return
        }
        val verificationManager = credentialTypeToManager[credentialType] ?: run {
            _uiState.value = _uiState.value.copy(error = "No verifier available for this credential type")
            return
        }
        val requestedFields = selectedFields.map {
            RequestedField(
                field = it.schema,
                predicateOperator = it.predicateOperator,
                predicateValue = it.predicateValue.ifBlank { null },
            )
        }
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val session = verificationManager.startVerification(credentialType, requestedFields)
                activeSession = session
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    qrContent = session.qrContent,
                    step = VerificationStep.ShowQrWaiting,
                    pollResult = VerificationPollResult.Pending,
                )
                pollJob = launch {
                    session.results.collect { result ->
                        _uiState.value = _uiState.value.copy(pollResult = result)
                        if (result !is VerificationPollResult.Pending) {
                            _uiState.value = _uiState.value.copy(step = VerificationStep.Result)
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e(VerificationViewModel::class.toString()) {
                    "Failed to start verification: $e"
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to start verification: ${e.message}"
                )
            }
        }
    }

    fun onCancelVerification() {
        pollJob?.cancel()
        viewModelScope.launch {
            activeSession?.let { session ->
                val verificationManager = credentialTypeToManager[_uiState.value.selectedCredentialType] ?: run {
                    _uiState.value = _uiState.value.copy(error = "No verifier available for this credential type")
                    return@launch
                }
                verificationManager.cancelVerification(session)
            }
        }
        resetToStart()
    }

    fun onStartNewVerification() {
        pollJob?.cancel()
        resetToStart()
    }

    private fun resetToStart() {
        activeSession = null
        _uiState.update {
            VerificationUiState(availableCredentialTypes = it.availableCredentialTypes)
        }
    }

    fun onErrorShown() = _uiState.update { it.copy(error = null) }

    fun onScanError(message: String) {
        _uiState.update { it.copy(error = message) }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }
}
