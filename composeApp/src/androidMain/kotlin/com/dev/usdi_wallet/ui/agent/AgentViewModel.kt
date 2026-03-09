package com.dev.usdi_wallet.ui.agent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.connection.ConnectionState
import com.dev.usdi_wallet.hyperledger_identus.IdentusJWTProtocol
import com.dev.usdi_wallet.protocol.Protocol
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AgentViewModel(application: Application) : AndroidViewModel(application) {
    private val protocols = listOf<Protocol<*>>(
        IdentusJWTProtocol.getInstance(application),
    )
    private val protocolMap: Map<String, Protocol<*>> = protocols.associateBy { it.protocolId }
    val protocolStates: Map<String, StateFlow<ConnectionState>> = protocolMap.mapValues { (_, protocol) ->
        protocol.connectionManager.state
            .catch { emit(ConnectionState.ERROR) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = ConnectionState.IDLE
            )
    }

    val aggregateState: StateFlow<ConnectionState> =
        combine(protocolStates.values.toList()) { states ->
            when {
                states.all { it == ConnectionState.RUNNING } -> ConnectionState.RUNNING
                states.any { it == ConnectionState.ERROR } -> ConnectionState.ERROR
                states.any { it == ConnectionState.STARTING } -> ConnectionState.STARTING
                states.all { it == ConnectionState.STOPPED } -> ConnectionState.STOPPED
                else -> ConnectionState.IDLE
            }
        }
        .catch { emit(ConnectionState.ERROR) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConnectionState.IDLE
        )

    fun startAgents() {
        viewModelScope.launch {
            try {
                protocols.forEach { it.start() }
            } catch (e: Exception) {
                Logger.e(AgentViewModel::class.toString()) {
                    "Error starting agent: ${e.message}"
                }
            }
        }
    }

    fun stopAgent(protocolId: String) {
        viewModelScope.launch {
            try {
                protocolMap[protocolId]?.connectionManager?.stop()
                    ?: Logger.w(AgentViewModel::class.toString()) {
                        "No manager registered for protocol: $protocolId"
                    }
            } catch (e: Exception) {
                Logger.e(AgentViewModel::class.toString()) {
                    "Error stopping agent $protocolId ${e.message}"
                }
            }
        }
    }

    fun stopAllAgents() {
        protocolMap.keys.forEach { stopAgent(it) }
    }

    fun stateFor(protocolId: String): StateFlow<ConnectionState>? = protocolStates[protocolId]

    override fun onCleared() {
        super.onCleared()
        stopAllAgents()
    }
}