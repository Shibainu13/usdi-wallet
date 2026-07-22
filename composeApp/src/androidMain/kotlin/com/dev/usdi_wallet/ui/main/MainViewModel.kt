package com.dev.usdi_wallet.ui.main

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.domain.connection.ConnectionState
import com.dev.usdi_wallet.domain.credential.Credential
import com.dev.usdi_wallet.domain.credential.ProofRequestDetails
import com.dev.usdi_wallet.domain.protocol.Protocol
import com.dev.usdi_wallet.eudi.EudiProtocol
import com.dev.usdi_wallet.hyperledger_identus.IdentusAnonProtocol
import com.dev.usdi_wallet.preferences.AndroidWalletPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WalletTab(
    val title: String,
    val rootRoute: String,
    val icon: ImageVector
) {
    CONTACTS("Contacts", "contacts_root", Icons.Default.People),
    CREDENTIALS("Credentials", "credentials_root", Icons.Default.Badge),
    VERIFY("Verify", "verify_root", Icons.Default.CheckCircle),
    SETTINGS("Settings", "settings_root", Icons.Default.Settings)
}

data class PendingProofRequest(
    val id: String,
    val protocolId: String,
    val details: ProofRequestDetails,
    val credentials: List<Credential>,
    val onCredentialSelected: suspend (Credential, List<String>) -> Unit,
    val onDenied: suspend () -> Unit,
)

data class RevokedCredentialAlert(
    val id: String,
    val subject: String,
)

data class MainUiState(
    val isReady: Boolean = false,
    val serviceNotice: String? = null,
    val pendingProofRequests: List<PendingProofRequest> = emptyList(),
    val revokedCredentialAlerts: List<RevokedCredentialAlert> = emptyList(),
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val protocols = listOf<Protocol<*, *>>(
        IdentusAnonProtocol.getInstance(application, viewModelScope),
        EudiProtocol.getInstance(application, viewModelScope),
    )

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private val startupTimedOut = MutableStateFlow(false)

    private val preferences = AndroidWalletPreferences.getInstance(application)
    val isOnboardingComplete: StateFlow<Boolean?> = preferences.isOnboardingComplete
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val protocolConnectionStates: StateFlow<List<ConnectionState>> =
        combine(protocols.map { it.connectionManager.state }) { states ->
            states.toList()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = List(protocols.size) { ConnectionState.IDLE },
        )

    val areAgentsRunning: StateFlow<Boolean> =
        combine(protocolConnectionStates, startupTimedOut) { states, timedOut ->
            states.isStartupSettled() || timedOut
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
            kotlinx.coroutines.delay(STARTUP_NOTICE_TIMEOUT_MS)
            startupTimedOut.value = true
        }
        viewModelScope.launch {
            combine(protocolConnectionStates, startupTimedOut) { states, timedOut ->
                val isReady = states.isStartupSettled() || timedOut
                val notice = when {
                    states.any { it == ConnectionState.ERROR } ->
                        "Some wallet services are offline. Local data is still available."
                    timedOut && !states.isStartupSettled() ->
                        "Server is not responding. You can keep using the wallet."
                    !isReady ->
                        "Connecting to wallet services..."
                    else -> null
                }
                isReady to notice
            }.collect { (isReady, notice) ->
                if (!isReady) {
                    Logger.w(MainViewModel::class.toString()) {
                        "Wallet protocols are still starting"
                    }
                }
                _uiState.update { it.copy(isReady = isReady, serviceNotice = notice) }
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
                launch {
                    runCatching { protocol.startConnection() }
                        .onFailure { error ->
                            Logger.w(MainViewModel::class.toString()) {
                                "${protocol.protocolId} startup failed: ${error.message}"
                            }
                        }
                }
            }
        }
    }

    private fun observeProofRequests() {
        protocols.forEach { protocol -> observeProtocolProofRequests(protocol) }
    }

    private fun <C, M> observeProtocolProofRequests(protocol: Protocol<C, M>) {
        viewModelScope.launch {
            protocol.credentialManager.getProofRequestsToProcess()
                .catch { error ->
                    Logger.w(MainViewModel::class.toString()) {
                        "${protocol.protocolId} proof request stream failed: ${error.message}"
                    }
                    emit(emptyList())
                }
                .collect { requests ->
                requests.forEachIndexed { index, request ->
                    val credentials = runCatching {
                        protocol.credentialManager.findMatchingCredentials(request).map {
                            protocol.credentialManager.toUiCredential(it)
                        }
                    }.getOrElse { error ->
                        Logger.w(MainViewModel::class.toString()) {
                            "Failed to find matching credentials for ${protocol.protocolId}: ${error.message}"
                        }
                        emptyList()
                    }
                    val details = runCatching {
                        protocol.credentialManager.getProofRequestDetails(request)
                    }.getOrElse { error ->
                        Logger.e(MainViewModel::class.toString()) {
                            "Failed to read proof request details: ${error.message}"
                        }
                        ProofRequestDetails(verifier = "Unknown verifier")
                    }
                    Logger.d(MainViewModel::class.toString()) {
                        "Found ${credentials.size} matching credentials for request $request"
                    }
                    Logger.d(MainViewModel::class.toString()) {
                        "Credentials: $credentials"
                    }
                    _uiState.update { state ->
                        if (state.pendingProofRequests.isNotEmpty()) {
                            state
                        } else {

                            Logger.d(MainViewModel::class.toString()) {
                                "State before copy: $state"
                            }
                            val newState = state.copy(
                                pendingProofRequests = listOf(
                                    PendingProofRequest(
                                        id = "${protocol.protocolId}-$index",
                                        protocolId = protocol.protocolId,
                                        details = details,
                                        credentials = credentials,
                                        onCredentialSelected = { credential, disclosedClaimLabels ->
                                            protocol.credentialManager.preparePresentationProof(
                                                protocol.credentialManager.toSdkCredential(credential),
                                                request,
                                                disclosedClaimLabels,
                                            )
                                            dismissProofRequest()
                                        },
                                        onDenied = {
                                            protocol.credentialManager.denyProofRequest(request)
                                            dismissProofRequest()
                                        },
                                    ),
                                ),
                            )
                            Logger.d(MainViewModel::class.toString()) {
                                "State after copy: $newState"
                            }
                            newState

                        }
                    }
                }
            }
        }
    }

    private fun <C, M> observeProtocolRevokedCredentials(protocol: Protocol<C, M>) {
        viewModelScope.launch {
            val revokedCredentials = runCatching {
                protocol.credentialManager.getRevokedCredential()
            }.getOrElse { error ->
                Logger.w(MainViewModel::class.toString()) {
                    "${protocol.protocolId} revoked credential stream unavailable: ${error.message}"
                }
                return@launch
            }

            revokedCredentials.catch { error ->
                Logger.w(MainViewModel::class.toString()) {
                    "${protocol.protocolId} revoked credential stream failed: ${error.message}"
                }
                emit(emptyList())
            }.collect { credentials ->
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

    private fun List<ConnectionState>.isStartupSettled(): Boolean =
        isNotEmpty() && all { state ->
            state == ConnectionState.RUNNING ||
                state == ConnectionState.ERROR ||
                state == ConnectionState.STOPPED
        }

    private companion object {
        const val STARTUP_NOTICE_TIMEOUT_MS = 5_000L
    }
}
