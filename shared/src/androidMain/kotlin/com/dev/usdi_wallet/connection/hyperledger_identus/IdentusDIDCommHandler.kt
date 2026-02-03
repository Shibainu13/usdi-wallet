package com.dev.usdi_wallet.connection.hyperledger_identus

import android.content.Context
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.connection.MessageCapable
import com.dev.usdi_wallet.connection.OperationState
import com.dev.usdi_wallet.connection.PersistConnectionCapable
import com.dev.usdi_wallet.connection.ProtocolHandler
import com.dev.usdi_wallet.connection.ProtocolOperation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.hyperledger.identus.walletsdk.domain.models.Message
import org.hyperledger.identus.walletsdk.edgeagent.protocols.ProtocolType
import org.hyperledger.identus.walletsdk.edgeagent.protocols.issueCredential.IssueCredential
import org.hyperledger.identus.walletsdk.edgeagent.protocols.issueCredential.OfferCredential
import org.hyperledger.identus.walletsdk.edgeagent.protocols.outOfBand.OutOfBandInvitation
import kotlin.coroutines.cancellation.CancellationException

class IdentusDIDCommHandler(
    private val context: Context,
//    private val mediatorDID: String
) : ProtocolHandler, PersistConnectionCapable, MessageCapable {
    override val protocolId: String = "IdentusDIDCommHandler"
    private val sdk: HyperledgerIdentusSdk by lazy { HyperledgerIdentusSdk.getInstance() }
    private var isInitialized = false
    private val agentLock = Mutex()
    private var messageListenerJob: Job? = null

    override fun detectOperation(input: String): ProtocolOperation? {
        return try {
            when {
                input.contains("_oob=") ||
                input.contains("OutOfBandInvitation") ||
                input.contains(ProtocolType.Didcomminvitation.value) ->
                    ProtocolOperation.EstablishConnection(input)

                input.contains(ProtocolType.DidcommOfferCredential.value) ||
                input.contains("OfferCredential") ->
                    ProtocolOperation.ReceiveCredential(input)

                input.contains(ProtocolType.DidcommRequestPresentation.value) ||
                input.contains("request-presentation") ->
                    ProtocolOperation.PresentProof(input)

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

            emitAndLog(OperationState.InProgress("Verifying connection..."))

            val didPairs = sdk.agent.getAllDIDPairs()
            val connectionPair = didPairs.lastOrNull()
                ?: throw IllegalStateException("Could not getDIDPairs from $input")

            emitAndLog(OperationState.ConnectionEstablished("Connection with ${connectionPair.receiver} established"))

            startMessageListener()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emitAndLog(OperationState.Error("Connection failed: ${e.message}"))
        }
    }

    private fun startMessageListener() {
        messageListenerJob?.cancel()
        messageListenerJob = CoroutineScope(Dispatchers.Default).launch {
            sdk.agent.handleReceivedMessagesEvents().collect { messages ->
                for (message in messages) {
                    val operation = detectOperation(message.piuri)

                    when (operation) {
                        is ProtocolOperation.ReceiveCredential -> {
                            receiveCredential(message).collect { state ->

                            }
                        }

                        else -> {
                            Logger.d(protocolId) { "Message type not handled in background" }
                        }
                    }
                }
            }
        }
    }

    override fun receiveCredential(input: Any): Flow<OperationState> = flow {
        try {
            val message = when (input) {
                is String -> sdk.agent.parseInvitation(input) as Message
                is Message -> input
                else -> throw IllegalStateException("Input type not handled")
            }

            when {
                message.piuri.contains("offer-credential") -> {
                    emitAndLog(OperationState.InProgress("Processing credential offer..."))

                    val offer = OfferCredential.fromMessage(message)
                    val didPairs = sdk.agent.getAllDIDPairs()
                    val myDID = didPairs.lastOrNull()?.holder
                        ?: throw IllegalStateException("Could not getDIDPairs from $message")

                    val request = sdk.agent.prepareRequestCredentialWithIssuer(myDID, offer)
                    sdk.agent.sendMessage(request.makeMessage())

                    emitAndLog(OperationState.InProgress("Credential request sent. Waiting for credential"))
                }

                message.piuri.contains("issue-credential") -> {
                    emitAndLog(OperationState.InProgress("Receiving credential..."))

                    val issueCredential = IssueCredential.fromMessage(message)
                    val credential = sdk.agent.processIssuedCredentialMessage(issueCredential)

                    emitAndLog(OperationState.CredentialReceived(
                        credentialId = credential.id,
                        credentialType = credential.subject ?: "Unknown",
                        issuer = credential.issuer,
                        claims = credential.claims.toString()
                    ))
                }
            }
        } catch (e: Exception) {
            emitAndLog(OperationState.Error("Failed to receive credential: ${e.message}"))
        }
    }

    override fun presentProof(input: Any): Flow<OperationState> = flow {
        TODO("Proof presentation flow not yet implemented")
    }

    override fun verifyProof(input: Any): Flow<OperationState> {
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
                    sdk.startAgent(
                        mediatorDID = "did:peer:2.Ez6LSghwSE437wnDE1pt3X6hVDUQzSjsHzinpX3XFvMjRAm7y.Vz6Mkhh1e5CEYYq6JBUcTZ6Cp2ranCWRrv7Yax3Le4N59R6dd.SeyJ0IjoiZG0iLCJzIjp7InVyaSI6Imh0dHA6Ly8xOTIuMTY4LjEwNS45OTo4MDgwIiwiYSI6WyJkaWRjb21tL3YyIl19fQ.SeyJ0IjoiZG0iLCJzIjp7InVyaSI6IndzOi8vMTkyLjE2OC4xMDUuOTk6ODA4MC93cyIsImEiOlsiZGlkY29tbS92MiJdfX0",
                        context = context
                    )
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