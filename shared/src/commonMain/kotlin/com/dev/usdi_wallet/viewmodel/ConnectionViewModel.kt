package com.dev.usdi_wallet.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dev.usdi_wallet.connection.ConnectionState
import com.dev.usdi_wallet.connection.ProtocolHandlerFactory
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ConnectionViewModel(
    handlerFactory: ProtocolHandlerFactory,
): ViewModel() {
    private val _currentState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val currentState: StateFlow<ConnectionState> = _currentState
    private var activeJob: Job? = null
    private val jobMutex = Mutex()
    private val handlers = handlerFactory.loadHandlers()

    fun processInput(connectionInvite: String) {
        val handler = handlers.find { it.canHandle(connectionInvite) }
            ?: return _currentState.update { ConnectionState.Error("Unknown connection protocol") }

        viewModelScope.launch {
            jobMutex.withLock {
                activeJob?.cancel()

                activeJob = launch {
                    try {
                        handler.handle(connectionInvite).collect { newState ->
                            _currentState.update { newState }
                        }
                    } catch (e: Exception) {
                        _currentState.update { ConnectionState.Error(e.message ?: "Unknown Error") }
                    }
                }
            }
        }
    }

    suspend fun cancelProcessing() {
        jobMutex.withLock {
            activeJob?.cancel()
            activeJob = null
            _currentState.update { ConnectionState.Idle }
        }
    }

    override fun onCleared() {
        activeJob?.cancel()
        handlers.forEach { it.cleanUp() }
    }
}