package com.dev.usdi_wallet.connection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConnectionManager(
    private val handlers: List<ProtocolHandler>
) {
    private val _currentState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val currentState: StateFlow<ConnectionState> = _currentState

    fun processInput(connectionInvite: String) {
        val handler = handlers.find { it.canHandle(connectionInvite) }
            ?: return _currentState.update { ConnectionState.Error("Unknown connection protocol") }

        CoroutineScope(Dispatchers.Default).launch {
            handler.handle(connectionInvite).collect { newState ->
                _currentState.value = newState
            }
        }
    }

    fun cleanUp() {
        handlers.forEach { it.cleanUp() }
    }
}