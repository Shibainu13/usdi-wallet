package com.dev.usdi_wallet.hyperledger_identus

import android.app.Application
import androidx.lifecycle.asFlow
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.domain.connection.ConnectionManager
import com.dev.usdi_wallet.domain.connection.ConnectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.hyperledger.identus.walletsdk.edgeagent.EdgeAgent
import org.hyperledger.identus.walletsdk.domain.models.Message as SdkMessage

class IdentusDIDCommConnectionManager(
    val context: Application,
) : ConnectionManager<SdkMessage> {
    private val sdk = HyperledgerIdentusSdk.getInstance()
    override val state: Flow<ConnectionState> =
        sdk.agentStatusStream().asFlow().map { it.toConnectionState() }

    override suspend fun sendMessage(message: SdkMessage) {
        sdk.sendMessage(message)
    }

    override suspend fun receiveMessage(msgHandler: suspend (message: SdkMessage) -> Unit) {
        if (!sdk.canReceiveMessages()) {
            Logger.w(IdentusDIDCommConnectionManager::class.toString()) {
                "DIDComm message receiving skipped because mediator is unavailable"
            }
            return
        }

        try {
            sdk.agent.let {
                it.handleReceivedMessagesEvents().collect { list ->
                    list.forEach { msg ->
                        Logger.d(IdentusDIDCommConnectionManager::class.toString()) {
                            "Received message $msg"
                        }
                        msgHandler(msg)
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            sdk.disableMediatorForSession()
            Logger.w(IdentusDIDCommConnectionManager::class.toString()) {
                "DIDComm message receiving failed: ${error.message}. Mediator calls are disabled for this session."
            }
        }
    }

    override suspend fun start() {
        sdk.startAgent(IdentusDIDCommConfig.MEDIATOR_DID, context)
    }

    override suspend fun stop() {
        sdk.stopAgent()
    }

    private fun EdgeAgent.State.toConnectionState(): ConnectionState =
        when (this) {
            EdgeAgent.State.STARTING -> ConnectionState.STARTING
            EdgeAgent.State.RUNNING  -> ConnectionState.RUNNING
            EdgeAgent.State.STOPPING -> ConnectionState.STOPPING
            EdgeAgent.State.STOPPED  -> ConnectionState.STOPPED
        }
}
