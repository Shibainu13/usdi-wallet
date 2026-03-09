package com.dev.usdi_wallet.hyperledger_identus

import android.app.Application
import androidx.lifecycle.asFlow
import com.dev.usdi_wallet.connection.ConnectionManager
import com.dev.usdi_wallet.connection.ConnectionStartupConfig
import com.dev.usdi_wallet.connection.ConnectionState
import com.dev.usdi_wallet.message.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.hyperledger.identus.walletsdk.edgeagent.DIDCOMM1
import org.hyperledger.identus.walletsdk.edgeagent.EdgeAgent
import org.hyperledger.identus.walletsdk.domain.models.Message as SdkMessage

class IdentusDIDCommConnectionManager(
    val scope: CoroutineScope,
    val context: Application,
) : ConnectionManager {
    private val sdk = HyperledgerIdentusSdk.getInstance()
    override val protocolId: String = PROTOCOL_ID
    override val state: Flow<ConnectionState> =
        sdk.agentStatusStream().asFlow().map { it.toConnectionState() }

    override suspend fun sendMessage(message: Message) {
        val sdkMessage = toIdentusMessage(message)
        scope.launch {
            sdk.agent.sendMessage(sdkMessage)
        }
    }

    override suspend fun receiveMessage(msgHandler: (message: Message) -> Unit) {
        scope.launch(Dispatchers.IO) {
            sdk.agent.let {
                it.handleReceivedMessagesEvents().collect { list ->
                    list.forEach { msgHandler(toUsdiMessage(it)) }
                }
            }
        }
    }

    override suspend fun start(config: ConnectionStartupConfig) {
        scope.launch {
            sdk.startAgent(config.config as String, context)
        }
    }

    override suspend fun stop() {
        sdk.stopAgent()
    }

    fun toUsdiMessage(libMessage: SdkMessage): Message =
        Message(
            id = libMessage.id,
            type = libMessage.piuri,
            from = libMessage.from.toString(),
            to = libMessage.to.toString(),
            raw = libMessage.toJsonString()
        )

    fun toIdentusMessage(message: Message): SdkMessage {
        return Json.decodeFromString<SdkMessage>(message.raw)
    }

    private fun EdgeAgent.State.toConnectionState(): ConnectionState = when (this) {
        EdgeAgent.State.STARTING -> ConnectionState.STARTING
        EdgeAgent.State.RUNNING  -> ConnectionState.RUNNING
        EdgeAgent.State.STOPPING -> ConnectionState.STOPPING
        EdgeAgent.State.STOPPED  -> ConnectionState.STOPPED
        else                     -> ConnectionState.IDLE
    }

    companion object {
        const val PROTOCOL_ID = DIDCOMM1
    }
}