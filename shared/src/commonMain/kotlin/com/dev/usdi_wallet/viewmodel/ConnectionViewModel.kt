package com.dev.usdi_wallet.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dev.usdi_wallet.connection.OperationState
import com.dev.usdi_wallet.connection.ProtocolHandler
import com.dev.usdi_wallet.connection.ProtocolHandlerFactory
import com.dev.usdi_wallet.connection.ProtocolOperation
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

class ConnectionViewModel(
    handlerFactory: ProtocolHandlerFactory,
) : ViewModel() {
    private val _currentState = MutableStateFlow<OperationState>(OperationState.Idle)
    val currentState: StateFlow<OperationState> = _currentState.asStateFlow()
    private var activeJob: Job? = null
    private val jobMutex = Mutex()
    private val handlers = handlerFactory.loadHandlers()

    fun processInput(connectionInvite: String) {
        viewModelScope.launch {
            jobMutex.withLock {
                activeJob?.cancel()

                val (handler, operation) = findHandlerForInput(connectionInvite) ?: run {
                    _currentState.value = OperationState.Error("Unknown protocol or operation")
                    return@launch
                }

                activeJob = launch {
                    try {
                        handler.executeOperation(operation).collect { newState ->
                            _currentState.value = newState
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _currentState.value = OperationState.Error(e.message ?: "Operation failed")
                    }
                }
            }
        }
    }

    fun cancelProcessing() {
        viewModelScope.launch {
            jobMutex.withLock {
                activeJob?.cancel()
                activeJob = null
                _currentState.value = OperationState.Idle
            }
        }
    }

    override fun onCleared() {
        activeJob?.cancel()
        handlers.forEach { it.cleanUp() }
        super.onCleared()
    }

    private fun findHandlerForInput(input: String): Pair<ProtocolHandler, ProtocolOperation>? {
        for (handler in handlers) {
            val operation = handler.detectOperation(input)
            if (operation != null) {
                return handler to operation
            }
        }
        return null
    }
}