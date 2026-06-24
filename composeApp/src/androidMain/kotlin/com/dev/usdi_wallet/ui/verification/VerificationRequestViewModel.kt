package com.dev.usdi_wallet.ui.verification

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.domain.contact.Contact
import com.dev.usdi_wallet.domain.credential.Claim
import com.dev.usdi_wallet.domain.credential.ClaimType
import com.dev.usdi_wallet.domain.credential.Credential
import com.dev.usdi_wallet.domain.credential.Predicate
import com.dev.usdi_wallet.domain.credential.PredicateOperator
import com.dev.usdi_wallet.domain.credential.VerificationRequest
import com.dev.usdi_wallet.domain.credential.VerificationResult
import com.dev.usdi_wallet.hyperledger_identus.CloudAgentCredentialDefinition
import com.dev.usdi_wallet.hyperledger_identus.CloudAgentVerifierClient
import com.dev.usdi_wallet.hyperledger_identus.IdentusJWTProtocol
import com.dev.usdi_wallet.domain.protocol.Protocol
import com.dev.usdi_wallet.eudi.EudiProtocol
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
import java.text.SimpleDateFormat
import java.util.Locale
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

enum class ServerSchemaClaimValueType(val suffix: String, val label: String) {
    STRING("str", "string"),
    NUMBER("num", "number"),
    BOOLEAN("bool", "boolean"),
    DATE("date", "date"),
}

data class ServerSchemaClaimRow(
    val id: String = UUID.randomUUID().toString(),
    val attrName: String,
    val displayName: String,
    val valueType: ServerSchemaClaimValueType,
    val checked: Boolean = false,
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

    val serverBaseUrl: String = "",
    val serverApiKey: String = "",
    val serverConnectionLabel: String = "usdi-mobile-verifier",
    val serverConnectionId: String = "",
    val serverInvitationUrl: String = "",
    val serverConnectionState: String = "",
    val serverCredentialDefinitionId: String = "",
    val serverProofRequestName: String = "Mobile verifier proof",
    val serverCredentialDefinitions: List<CloudAgentCredentialDefinition> = emptyList(),
    val selectedServerCredentialDefinition: CloudAgentCredentialDefinition? = null,
    val serverSchemaClaimRows: List<ServerSchemaClaimRow> = emptyList(),
    val serverResult: String = "",
)

class VerificationRequestViewModel(application: Application) : AndroidViewModel(application) {
    private val protocols = listOf<Protocol<*,*>>(
        IdentusJWTProtocol.getInstance(application, viewModelScope),
        EudiProtocol.getInstance(application, viewModelScope),
    )
    private val cloudAgentVerifierClient = CloudAgentVerifierClient()

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
                "VerificationRequestViewModel.kt.credentials: Failed to get credentials $e"
            }
            _uiState.update { it.copy(error = "Failed to load credentials: $e") }
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
            protocols.map { it.contactManager.getContacts() }
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
            protocols.map { it.credentialManager.getVerificationResults() }
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
            state.copy(manualClaimRows = if (updatedRows.isEmpty()) listOf(ManualClaimRow()) else updatedRows)
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

    fun onServerBaseUrlChanged(value: String) {
        _uiState.update { it.copy(serverBaseUrl = value) }
    }

    fun onServerApiKeyChanged(value: String) {
        _uiState.update { it.copy(serverApiKey = value) }
    }

    fun onServerConnectionLabelChanged(value: String) {
        _uiState.update { it.copy(serverConnectionLabel = value) }
    }

    fun onServerConnectionIdChanged(value: String) {
        _uiState.update { it.copy(serverConnectionId = value) }
    }

    fun onServerCredentialDefinitionIdChanged(value: String) {
        _uiState.update { it.copy(serverCredentialDefinitionId = value) }
    }

    fun onServerProofRequestNameChanged(value: String) {
        _uiState.update { it.copy(serverProofRequestName = value) }
    }

    fun onServerCredentialDefinitionSelected(credentialDefinition: CloudAgentCredentialDefinition) {
        _uiState.update {
            it.copy(
                selectedServerCredentialDefinition = credentialDefinition,
                serverSchemaClaimRows = emptyList(),
            )
        }
        loadCredentialDefinitionSchemaRows(credentialDefinition)
    }

    fun onServerSchemaRowConstraintChanged(id: String, constraint: String) {
        updateServerSchemaRow(id) { it.copy(constraint = constraint) }
    }

    fun onServerSchemaRowChecked(id: String, checked: Boolean) {
        updateServerSchemaRow(id) { it.copy(checked = checked) }
    }

    fun onServerSchemaRowPredicateOperatorChanged(id: String, operator: PredicateOperator?) {
        updateServerSchemaRow(id) { it.copy(predicateOperator = operator, predicateValue = if (operator == null) "" else it.predicateValue) }
    }

    fun onServerSchemaRowPredicateValueChanged(id: String, value: String) {
        updateServerSchemaRow(id) { it.copy(predicateValue = value) }
    }

    private fun updateRow(id: String, transform: (ManualClaimRow) -> ManualClaimRow) {
        _uiState.update { state ->
            state.copy(manualClaimRows = state.manualClaimRows.map {
                if (it.id == id) transform(it) else it
            })
        }
    }

    private fun updateServerSchemaRow(id: String, transform: (ServerSchemaClaimRow) -> ServerSchemaClaimRow) {
        _uiState.update { state ->
            state.copy(serverSchemaClaimRows = state.serverSchemaClaimRows.map {
                if (it.id == id) transform(it) else it
            })
        }
    }

    fun loadServerCredentialDefinitions() {
        val state = _uiState.value
        if (state.serverBaseUrl.isBlank()) {
            _uiState.update { it.copy(error = "Enter cloud agent URL first") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null, serverResult = "") }
        viewModelScope.launch {
            try {
                val credentialDefinitions = cloudAgentVerifierClient.getCredentialDefinitions(
                    baseUrl = state.serverBaseUrl,
                    apiKey = state.serverApiKey.ifBlank { null },
                )
                Logger.d("credential in verification: $credentialDefinitions")
                val selectedCredentialDefinition = credentialDefinitions.firstOrNull()
                val rows = selectedCredentialDefinition
                    ?.schemaClaimRows(state.serverBaseUrl, state.serverApiKey.ifBlank { null })
                    .orEmpty()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        serverCredentialDefinitions = credentialDefinitions,
                        selectedServerCredentialDefinition = selectedCredentialDefinition,
                        serverSchemaClaimRows = rows,
                        serverResult = "Loaded ${credentialDefinitions.size} credential definition(s)",
                    )
                }
            } catch (e: Exception) {
                Logger.e(VerificationRequestViewModel::class.toString()) {
                    "VerificationRequestViewModel.kt.loadServerCredentialDefinitions: Failed to load server credential definitions: ${e.message}"
                }
                _uiState.update { it.copy(isLoading = false, error = "Failed to load credential definitions: ${e.message}") }
            }
        }
    }

    private fun loadCredentialDefinitionSchemaRows(credentialDefinition: CloudAgentCredentialDefinition) {
        val state = _uiState.value
        if (state.serverBaseUrl.isBlank()) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val rows = credentialDefinition.schemaClaimRows(
                    baseUrl = state.serverBaseUrl,
                    apiKey = state.serverApiKey.ifBlank { null },
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        serverSchemaClaimRows = rows,
                    )
                }
            } catch (e: Exception) {
                Logger.e(VerificationRequestViewModel::class.toString()) {
                    "VerificationRequestViewModel.kt.loadCredentialDefinitionSchemaRows: Failed to load schema for credential definition: ${e.message}"
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        serverSchemaClaimRows = emptyList(),
                        error = "Failed to load credential definition schema: ${e.message}",
                    )
                }
            }
        }
    }

    fun sendFromCredential() {
        val contact = _uiState.value.selectedContact ?: run {
            _uiState.update { it.copy(error = "Select a contact first") }
            return
        }

        val checkedItems = _uiState.value.claimItems.filter { it.checked }
        if (checkedItems.isEmpty()) {
            _uiState.update { it.copy(error = "Select at least one claim") }
            return
        }
        send(
            buildRequestFromItems(contact, checkedItems),
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

    fun createServerConnectionInvitation() {
        val state = _uiState.value
        if (state.serverBaseUrl.isBlank()) {
            _uiState.update { it.copy(error = "Enter cloud agent URL first") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null, serverResult = "") }
        viewModelScope.launch {
            try {
                val invitation = cloudAgentVerifierClient.createConnectionInvitation(
                    baseUrl = state.serverBaseUrl,
                    apiKey = state.serverApiKey.ifBlank { null },
                    label = state.serverConnectionLabel,
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        serverConnectionId = invitation.connectionId,
                        serverInvitationUrl = invitation.invitationUrl,
                        serverConnectionState = invitation.state.orEmpty(),
                        serverResult = if (invitation.invitationUrl.isBlank()) {
                            "Connection invitation created, but the response did not include an invitation URL"
                        } else {
                            "Connection invitation created"
                        },
                    )
                }
            } catch (e: Exception) {
                Logger.e(VerificationRequestViewModel::class.toString()) {
                    "VerificationRequestViewModel.kt.createServerConnectionInvitation: Failed to create server connection invitation: ${e.message}"
                }
                _uiState.update { it.copy(isLoading = false, error = "Failed to create invitation: ${e.message}") }
            }
        }
    }

    fun checkServerConnection() {
        val state = _uiState.value
        if (state.serverBaseUrl.isBlank() || state.serverConnectionId.isBlank()) {
            _uiState.update { it.copy(error = "Enter cloud agent URL and connection ID first") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null, serverResult = "") }
        viewModelScope.launch {
            try {
                val connection = cloudAgentVerifierClient.getConnection(
                    baseUrl = state.serverBaseUrl,
                    apiKey = state.serverApiKey.ifBlank { null },
                    connectionId = state.serverConnectionId,
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        serverConnectionState = connection.state.orEmpty(),
                        serverInvitationUrl = connection.invitationUrl.ifBlank { it.serverInvitationUrl },
                        serverResult = "Connection state: ${connection.state.orEmpty()}",
                    )
                }
            } catch (e: Exception) {
                Logger.e(VerificationRequestViewModel::class.toString()) {
                    "VerificationRequestViewModel.kt.checkServerConnection: Failed to check server connection: ${e.message}"
                }
                _uiState.update { it.copy(isLoading = false, error = "Failed to check connection: ${e.message}") }
            }
        }
    }

    fun sendServerProofRequest() {
        val state = _uiState.value
        if (state.serverBaseUrl.isBlank()) {
            _uiState.update { it.copy(error = "Enter cloud agent URL first") }
            return
        }

        if (state.selectedServerCredentialDefinition == null) {
            _uiState.update { it.copy(error = "Load and select a credential definition first") }
            return
        }

        val selectedRows = state.serverSchemaClaimRows.filter { it.checked }
        val validationError = validateServerSchemaRows(selectedRows)
        if (validationError != null) {
            _uiState.update { it.copy(error = validationError) }
            return
        }

        if (selectedRows.isEmpty()) {
            _uiState.update { it.copy(error = "Add at least one proof claim") }
            return
        }

        val request = buildRequestFromServerSchemaRows(
            contact = Contact(holder = "proof-invitation", name = "Cloud agent", protocol = "HTTP"),
            rows = selectedRows,
        )

        _uiState.update { it.copy(isLoading = true, error = null, serverInvitationUrl = "", serverResult = "") }
        viewModelScope.launch {
            try {
                val result = cloudAgentVerifierClient.sendAnonCredProofRequest(
                    baseUrl = state.serverBaseUrl,
                    apiKey = state.serverApiKey.ifBlank { null },
                    claims = request.claims,
                    predicates = request.predicates,
                    credentialDefinitionId = state.serverCredentialDefinitionId
                        .ifBlank { state.selectedServerCredentialDefinition.credentialDefinitionId(state.serverBaseUrl) }
                        .ifBlank { null },
                    requestName = state.serverProofRequestName,
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        serverInvitationUrl = result.invitationUrl.orEmpty(),
                        serverResult = if (result.invitationUrl.isNullOrBlank()) {
                            "Proof invitation created, but the response did not include an invitation URL"
                        } else {
                            ""
                        },
                    )
                }
            } catch (e: Exception) {
                Logger.e(VerificationRequestViewModel::class.toString()) {
                    "VerificationRequestViewModel.kt.sendServerProofRequest: Failed to send server proof request: ${e.message}"
                }
                _uiState.update { it.copy(isLoading = false, error = "Failed to send proof request: ${e.message}") }
            }
        }
    }

    private fun send(
        request: VerificationRequest,
        domain: String,
        challenge: String
    ) {
        Logger.d("VerificationRequestViewModel.kt.send: Request: $request");
        Logger.d("VerificationRequestViewModel.kt.send: Domain: $domain");
        Logger.d("VerificationRequestViewModel.kt.send: Challenge: $challenge");
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val request= protocols.forEach { protocol ->
                    protocol.credentialManager.sendVerificationRequest(request, domain, challenge)
                }
                Logger.d("VerificationRequestViewModel.kt.send: Request result: $request");
                _uiState.update { it.copy(isLoading = false, success = true) }
            } catch (e: Exception) {
                Logger.e(VerificationRequestViewModel::class.toString()) {
                    "VerificationRequestViewModel.kt.send: Failed to send verification request: ${e.message}"
                }
                _uiState.update { it.copy(isLoading = false, error = "Failed to send: ${e.message}") }
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

    private fun buildRequestFromServerSchemaRows(
        contact: Contact,
        rows: List<ServerSchemaClaimRow>,
    ): VerificationRequest {
        val claims = rows
            .filter { !it.supportsPredicate() || it.predicateOperator == null }
            .map {
                Claim(
                    name = it.attrName,
                    type = it.valueType.toClaimType(),
                    pattern = it.constraint.ifBlank { null },
                )
            }

        val predicates = rows
            .filter { it.supportsPredicate() && it.predicateOperator != null }
            .mapNotNull { row ->
                val value = row.predicateIntValue() ?: return@mapNotNull null
                Predicate(
                    name = row.attrName,
                    operator = row.predicateOperator!!,
                    value = value,
                )
            }
        return VerificationRequest(contact.holder, claims, predicates)
    }

    private fun validateServerSchemaRows(rows: List<ServerSchemaClaimRow>): String? {
        rows.forEach { row ->
            if (row.supportsPredicate() && row.predicateOperator != null) {
                if (row.predicateIntValue() == null) return when (row.valueType) {
                    ServerSchemaClaimValueType.DATE -> "${row.displayName} predicate must be a date in yyyy-MM-dd format"
                    else -> "${row.displayName} must be an integer number"
                }
                return@forEach
            }

            val value = row.constraint.trim()
            if (value.isBlank()) return@forEach

            when (row.valueType) {
                ServerSchemaClaimValueType.STRING -> Unit
                ServerSchemaClaimValueType.NUMBER -> if (value.toDoubleOrNull() == null) {
                    return "${row.displayName} must be a number"
                }
                ServerSchemaClaimValueType.BOOLEAN -> if (!value.equals("true", ignoreCase = true) && !value.equals("false", ignoreCase = true)) {
                    return "${row.displayName} must be true or false"
                }
                ServerSchemaClaimValueType.DATE -> if (!isIsoDate(value)) {
                    return "${row.displayName} must be a date in yyyy-MM-dd format"
                }
            }
        }
        return null
    }

    private fun ServerSchemaClaimRow.supportsPredicate(): Boolean =
        valueType == ServerSchemaClaimValueType.NUMBER || valueType == ServerSchemaClaimValueType.DATE

    private fun ServerSchemaClaimRow.predicateIntValue(): Int? =
        when (valueType) {
            ServerSchemaClaimValueType.DATE -> predicateValue.trim().takeIf { isIsoDate(it) }
                ?.replace("-", "")
                ?.toIntOrNull()
            else -> predicateValue.toIntOrNull()
        }

    private fun String.toServerSchemaClaimRow(): ServerSchemaClaimRow {
        val suffix = substringAfterLast("_", missingDelimiterValue = "")
        val type = ServerSchemaClaimValueType.entries.firstOrNull { it.suffix == suffix }
            ?: ServerSchemaClaimValueType.STRING
        val displayName = if (suffix == type.suffix) substringBeforeLast("_") else this
        return ServerSchemaClaimRow(
            attrName = this,
            displayName = displayName.ifBlank { this },
            valueType = type,
        )
    }

    private fun ServerSchemaClaimValueType.toClaimType(): ClaimType =
        when (this) {
            ServerSchemaClaimValueType.STRING,
            ServerSchemaClaimValueType.DATE -> ClaimType.STRING
            ServerSchemaClaimValueType.NUMBER -> ClaimType.NUMBER
            ServerSchemaClaimValueType.BOOLEAN -> ClaimType.BOOLEAN
        }

    private fun isIsoDate(value: String): Boolean {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        format.isLenient = false
        return runCatching { format.parse(value) }.getOrNull() != null
    }

    private suspend fun CloudAgentCredentialDefinition.schemaClaimRows(
        baseUrl: String,
        apiKey: String?,
    ): List<ServerSchemaClaimRow> =
        cloudAgentVerifierClient.getAnonCredSchemaById(
            baseUrl = baseUrl,
            apiKey = apiKey,
            schemaId = schemaId,
        )?.attrNames
            ?.map { attrName -> attrName.toServerSchemaClaimRow() }
            .orEmpty()

    private fun CloudAgentCredentialDefinition?.credentialDefinitionId(baseUrl: String): String {
        val guid = this?.guid.orEmpty()
        if (guid.isBlank() || baseUrl.isBlank()) return ""
        return "${baseUrl.trimEnd('/')}/credential-definition-registry/definitions/$guid/definition"
    }

    fun onErrorShown() = _uiState.update { it.copy(error = null) }
    fun onSuccessHandled() = _uiState.update { it.copy(success = false) }
}
