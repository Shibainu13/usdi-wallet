package com.dev.usdi_wallet.ui.agent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.connection.ConnectionManager
import com.dev.usdi_wallet.connection.ConnectionStartupConfig
import com.dev.usdi_wallet.connection.ConnectionState
import com.dev.usdi_wallet.connection.hyperledger_identus.connection.IdentusDIDCommConnectionManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AgentViewModel(application: Application) : AndroidViewModel(application) {
    private val managers: Map<String, ConnectionManager> = mapOf(
        IdentusDIDCommConnectionManager.PROTOCOL_ID to IdentusDIDCommConnectionManager(viewModelScope, application)
    )

    val protocolStates: Map<String, StateFlow<ConnectionState>> = managers.mapValues { (_, manager) ->
        manager.state
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

    fun startAgents(configs: List<ConnectionStartupConfig>) {
        configs.forEach { connectionConfig ->
            val manager = managers[connectionConfig.protocolId]
            if (manager == null) {
                Logger.w(AgentViewModel::class.toString()) { "No manager registered for protocol: ${connectionConfig.protocolId}" }
                return@forEach
            }
            viewModelScope.launch {
                try {
                    manager.start(connectionConfig)
                } catch (e: Exception) {
                    Logger.e(AgentViewModel::class.toString()) {"Error starting agent ${connectionConfig.protocolId}: ${e.message}"}
                }
            }
        }
    }

    fun stopAgent(protocolId: String) {
        viewModelScope.launch {
            try {
                managers[protocolId]?.stop()
                    ?: Logger.w(AgentViewModel::class.toString()) { "No manager registered for protocol: $protocolId" }
            } catch (e: Exception) {
                Logger.e(AgentViewModel::class.toString()) {"Error stopping agent $protocolId ${e.message}" }
            }
        }
    }

    fun stopAllAgents() {
        managers.keys.forEach { stopAgent(it) }
    }

    fun stateFor(protocolId: String): StateFlow<ConnectionState>? = protocolStates[protocolId]

    override fun onCleared() {
        super.onCleared()
        stopAllAgents()
    }
}