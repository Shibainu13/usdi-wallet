package com.dev.usdi_wallet.connection.hyperledger_identus

import android.app.Application
import android.util.Log
import com.dev.usdi_wallet.connection.ConnectionState
import com.dev.usdi_wallet.connection.ProtocolHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import org.hyperledger.identus.walletsdk.edgeagent.protocols.outOfBand.OutOfBandInvitation

class IdentusDIDCommHandler(
    private val context: Application,
    private val mediatorDID: String
): ProtocolHandler {
    override val protocolId: String = "identus-didcomm"
    private val sdk: HyperledgerIdentusSdk = HyperledgerIdentusSdk.getInstance(context)
    private var isInitialized = false

    override fun canHandle(input: String): Boolean {
        return try {
            when {
                input.contains("_oob=") -> true
                input.trim().startsWith("{") && input.contains("OutOfBandInvitation") -> true
                input.trim().startsWith("{") && input.contains("\"type\"") &&
                        input.contains("https://didcomm.org/out-of-band/") -> true
                else -> false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun handle(input: String): Flow<ConnectionState> = flow {
        try {
            emitAndLog(ConnectionState.Pending("Initializing agent..."))

            if (!initializeAgent()) {
                emitAndLog(ConnectionState.Error("Failed to initialize agent"))
                return@flow
            }
            Log.d("IdentusDIDCommHandler", "input: $input")

            val oobInvitation = sdk.agent.parseInvitation(input)

            Log.d("IdentusDIDCommHandler", "Parsed: $oobInvitation.")

            emitAndLog(ConnectionState.Pending("Parsing invitation..."))
            sdk.agent.acceptOutOfBandInvitation(oobInvitation as OutOfBandInvitation)

            emitAndLog(ConnectionState.Pending("Establishing connection..."))

            var retries = 0
            val maxRetries = 10

            while(retries < maxRetries) {
                delay(1000)

                val connections = sdk.pluto.getAllDidPairs().first()

                val newConnection = connections.firstOrNull { pair ->
                    pair.receiver.toString().isNotEmpty()
                }

                if (newConnection != null) {
                    emitAndLog(ConnectionState.Success("Connection established successfully"))
                    return@flow
                }

                retries++
                emitAndLog(ConnectionState.Pending("Connection failed $retries time(s). Try again..."))
            }

            emitAndLog(ConnectionState.Error("Connection timeout."))
        } catch (e: Exception) {
            emitAndLog(ConnectionState.Error("Connection failed: ${e.message ?: "Unknown Error"}"))
        }
    }

    override fun cleanUp() {
        sdk.stopAgent()
    }

    private suspend fun initializeAgent(): Boolean {
        try {
            if (!isInitialized) {
                sdk.startAgent(mediatorDID, context)
                isInitialized = true
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private suspend fun FlowCollector<ConnectionState>.emitAndLog(state: ConnectionState) {
        Log.d("IdentusDIDCommHandler", "Emitting state: $state")
        emit(state)
    }
}