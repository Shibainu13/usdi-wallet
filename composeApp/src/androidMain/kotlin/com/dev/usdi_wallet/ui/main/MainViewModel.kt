package com.dev.usdi_wallet.ui.main

import android.app.Application
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.eudi.EudiProtocol
import com.dev.usdi_wallet.domain.connection.ConnectionState
import com.dev.usdi_wallet.domain.credential.Credential
import com.dev.usdi_wallet.hyperledger_identus.IdentusJWTProtocol
import com.dev.usdi_wallet.domain.protocol.Protocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WalletTab(
    val title: String,
    val rootRoute: String,
    val icon: ImageVector
) {
    AUTH("Authenticate", "authenticate_root", Icons.Default.Lock),
    CONTACTS("Contacts", "contacts_root", Icons.Default.People),
    CREDENTIALS("Credentials", "credentials_root", Icons.Default.Badge),
    VERIFY("Verify", "verify_root", Icons.Default.CheckCircle),
}

data class PendingProofRequest(
    val id: String,
    val protocolId: String,
    val credentials: List<Credential>,
    val onCredentialSelected: suspend (Credential, List<String>) -> Unit,
)

data class RevokedCredentialAlert(
    val id: String,
    val subject: String,
)

data class MainUiState(
    val isReady: Boolean = false,
    val pendingProofRequests: List<PendingProofRequest> = emptyList(),
    val revokedCredentialAlerts: List<RevokedCredentialAlert> = emptyList(),
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val protocols = listOf<Protocol<*, *>>(
        IdentusJWTProtocol.getInstance(application,viewModelScope),
        EudiProtocol.getInstance(application, viewModelScope),
    )

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val areAgentsRunning: StateFlow<Boolean> =
        combine(protocols.map { it.connectionManager.state }) { states ->
            states.all { it == ConnectionState.RUNNING }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    init {
        startAgents()
        observeProofRequests()
        observeRevokedCredentials()
        viewModelScope.launch {
            areAgentsRunning.collect { running ->
                if (!running) {
                    Logger.w(MainViewModel::class.toString()) {
                        "At least one of the protocols is not running"
                    }
                }
                _uiState.update { it.copy(isReady = running) }
            }
        }
    }


    fun dismissProofRequest() {
        _uiState.update { state ->
            state.copy(pendingProofRequests = state.pendingProofRequests.drop(1))
        }
    }

    fun dismissRevokedCredentialAlert() {
        _uiState.update { state ->
            state.copy(revokedCredentialAlerts = state.revokedCredentialAlerts.drop(1))
        }
    }

    private fun startAgents() {
        viewModelScope.launch {
            protocols.forEach { protocol ->
                launch { protocol.startConnection() }
            }
        }
    }

    private fun observeProofRequests() {
        protocols.forEach { protocol -> observeProtocolProofRequests(protocol) }
    }

    private fun <C, M> observeProtocolProofRequests(protocol: Protocol<C, M>) {
        viewModelScope.launch {
            protocol.credentialManager.getProofRequestsToProcess().collect { requests ->
                requests.forEachIndexed { index, request ->
                    val credentials = protocol.credentialManager.getCredentials().first().map {
                        protocol.credentialManager.toUiCredential(it)
                    }

                    _uiState.update { state ->
                        if (state.pendingProofRequests.isNotEmpty()) {
                            state
                        } else {
                            state.copy(
                                pendingProofRequests = listOf(
                                    PendingProofRequest(
                                        id = "${protocol.protocolId}-$index",
                                        protocolId = protocol.protocolId,
                                        credentials = credentials,
                                        onCredentialSelected = { credential, disclosedClaimLabels ->
                                            protocol.credentialManager.preparePresentationProof(
                                                protocol.credentialManager.toSdkCredential(credential),
                                                request,
                                                disclosedClaimLabels,
                                            )
                                            dismissProofRequest()
                                        },
                                    ),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun <C, M> observeProtocolRevokedCredentials(protocol: Protocol<C, M>) {
        viewModelScope.launch {
            protocol.credentialManager.getRevokedCredential().collect { credentials ->
                val revokedCredentialAlerts = credentials.map { credential ->
                    val uiCredential = protocol.credentialManager.toUiCredential(credential)

                    RevokedCredentialAlert(
                        id = "${protocol.protocolId}-${uiCredential.id}",
                        subject = uiCredential.subject ?: uiCredential.id,
                    )
                }

                if (revokedCredentialAlerts.isEmpty()) {
                    return@collect
                }

                _uiState.update { state ->
                    val existingAlertIds = state.revokedCredentialAlerts
                        .mapTo(mutableSetOf()) { alert -> alert.id }
                    val newAlerts = revokedCredentialAlerts.filterNot { alert ->
                        alert.id in existingAlertIds
                    }

                    if (newAlerts.isEmpty()) {
                        state
                    } else {
                        state.copy(
                            revokedCredentialAlerts = state.revokedCredentialAlerts + newAlerts,
                        )
                    }
                }
            }
        }
    }

    private fun observeRevokedCredentials() {
        protocols.forEach { protocol -> observeProtocolRevokedCredentials(protocol) }
    }
}
