package com.dev.usdi_wallet.hyperledger_identus

import android.app.Application
import androidx.lifecycle.asFlow
import com.dev.usdi_wallet.connection.ConnectionManager
import com.dev.usdi_wallet.connection.ConnectionState
import com.dev.usdi_wallet.message.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.hyperledger.identus.walletsdk.edgeagent.EdgeAgent
import org.hyperledger.identus.walletsdk.domain.models.Message as SdkMessage

class IdentusDIDCommConnectionManager(
    val context: Application,
) : ConnectionManager<SdkMessage> {
    private val sdk = HyperledgerIdentusSdk.getInstance()
    override val state: Flow<ConnectionState> =
        sdk.agentStatusStream().asFlow().map { it.toConnectionState() }

    override suspend fun sendMessage(message: SdkMessage) {
        sdk.agent.sendMessage(message)
    }

    override suspend fun receiveMessage(msgHandler: suspend (message: SdkMessage) -> Unit) {
        sdk.agent.let {
            it.handleReceivedMessagesEvents().collect { list ->
                list.forEach { msg -> msgHandler(msg) }
            }
        }
    }

    override suspend fun start() {
        sdk.startAgent(IdentusDIDCommConfig.MEDIATOR_DID, context)
    }

    override suspend fun stop() {
        sdk.stopAgent()
    }

    private fun EdgeAgent.State.toConnectionState(): ConnectionState = when (this) {
        EdgeAgent.State.STARTING -> ConnectionState.STARTING
        EdgeAgent.State.RUNNING  -> ConnectionState.RUNNING
        EdgeAgent.State.STOPPING -> ConnectionState.STOPPING
        EdgeAgent.State.STOPPED  -> ConnectionState.STOPPED
        else                     -> ConnectionState.IDLE
    }

}