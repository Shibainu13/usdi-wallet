package com.dev.usdi_wallet.eudi

import android.app.Application
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.domain.connection.ConnectionManager
import com.dev.usdi_wallet.domain.connection.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class EudiConnectionManager(
    private val context: Application,
) : ConnectionManager<String> {
    private val sdk = EudiSdk.getInstance()
    private val _state = MutableStateFlow(ConnectionState.IDLE)
    override val state: Flow<ConnectionState> = _state.asStateFlow()

    override suspend fun start() {
        _state.value = ConnectionState.STARTING
        try {
            sdk.start(context)
            _state.value = ConnectionState.RUNNING
        } catch (e: Exception) {
            _state.value = ConnectionState.ERROR
            Logger.e(EudiConnectionManager::class.toString()) {
                "Failed to start eudi sdk: ${e.message}"
            }
        }
    }

    override suspend fun sendMessage(message: String) {
        TODO("Not yet implemented")
    }

    override suspend fun receiveMessage(msgHandler: suspend (message: String) -> Unit) {
        TODO("Not yet implemented")
    }

    override suspend fun stop() {
        _state.value = ConnectionState.STOPPING
        sdk.wallet.stopProximityPresentation()
        sdk.wallet.stopRemotePresentation()
        _state.value = ConnectionState.STOPPED
    }
}