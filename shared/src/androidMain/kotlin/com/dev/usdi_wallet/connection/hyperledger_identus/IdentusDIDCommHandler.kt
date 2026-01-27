package com.dev.usdi_wallet.connection.hyperledger_identus

import android.content.Context
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.connection.MessageCapable
import com.dev.usdi_wallet.connection.OperationState
import com.dev.usdi_wallet.connection.PersistConnectionCapable
import com.dev.usdi_wallet.connection.ProtocolHandler
import com.dev.usdi_wallet.connection.ProtocolOperation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.hyperledger.identus.walletsdk.edgeagent.protocols.outOfBand.OutOfBandInvitation
import org.hyperledger.identus.walletsdk.domain.models.DIDPair
import kotlin.coroutines.cancellation.CancellationException

class IdentusDIDCommHandler(
    private val context: Context,
    private val mediatorDID: String
) : ProtocolHandler, PersistConnectionCapable, MessageCapable {
    override val protocolId: String = "IdentusDIDCommHandler"
    private val sdk: HyperledgerIdentusSdk by lazy { HyperledgerIdentusSdk.getInstance(context) }
    private var isInitialized = false
    private val agentLock = Mutex()

    override fun detectOperation(input: String): ProtocolOperation? {
        return try {
            when {
                input.contains("_oob=") ||
                input.contains("OutOfBandInvitation") ||
                input.contains("https://didcomm.org/out-of-band/") ->
                    ProtocolOperation.EstablishConnection(input)

                input.contains("https://didcomm.org/issue-credential/") ||
                input.contains("OfferCredential") ->
                    ProtocolOperation.ReceiveCredential(input)

                input.contains("https://didcomm.org/present-proof/") ||
                input.contains("request-presentation") ->
                    ProtocolOperation.PresentProof(input)

                input.contains("https://didcomm.org/present-proof/") ||
                input.contains("presentation") ->
                    ProtocolOperation.VerifyProof(input)

                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun executeOperation(operation: ProtocolOperation): Flow<OperationState> {
        return when (operation) {
            is ProtocolOperation.EstablishConnection -> establishConnection(operation.input)
            is ProtocolOperation.ReceiveCredential -> receiveCredential(operation.input)
            is ProtocolOperation.PresentProof -> presentProof(operation.input)
            is ProtocolOperation.VerifyProof -> verifyProof(operation.input)
            is ProtocolOperation.ReceiveMessage -> receiveMessage()
            is ProtocolOperation.SendMessage -> sendMessage(operation.input)
        }
    }

    override fun cleanUp() {
        if (isInitialized) {
            sdk.stopAgent()
            isInitialized = false
        }
    }

    override fun establishConnection(input: String): Flow<OperationState> = flow {
        try {
            emitAndLog(OperationState.InProgress("Initializing agent..."))
            initializeAgent()

            emitAndLog(OperationState.InProgress("Parsing invitation..."))
            val oobInvitation = sdk.agent.parseInvitation(input) as? OutOfBandInvitation
                ?: throw IllegalStateException("Could not parseInvitation from $input")
            sdk.agent.acceptOutOfBandInvitation(oobInvitation)

            emitAndLog(OperationState.ConnectionEstablished("Connection established."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emitAndLog(OperationState.Error("Connection failed: ${e.message}"))
        }
    }

    override fun receiveCredential(input: String): Flow<OperationState> = flow {
        TODO("Credential issuance flow not yet implemented")
    }

    override fun presentProof(input: String): Flow<OperationState> = flow {
        TODO("Proof presentation flow not yet implemented")
    }

    override fun verifyProof(input: String): Flow<OperationState> {
        TODO("Proof verification flow not yet implemented")
    }

    override fun sendMessage(message: String): Flow<OperationState> = flow {
        TODO("Message sending flow not yet implemented")
    }

    override fun receiveMessage(): Flow<OperationState> {
        TODO("Message receiving flow not yet implemented")
    }

    private suspend fun initializeAgent(): Boolean {
        try {
            agentLock.withLock {
                if (!isInitialized) {
                    sdk.startAgent(mediatorDID, context)
                    delay(1000)
                    isInitialized = true
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private suspend fun FlowCollector<OperationState>.emitAndLog(state: OperationState) {
        Logger.d(protocolId) { state.logMessage }
        emit(state)
    }
}