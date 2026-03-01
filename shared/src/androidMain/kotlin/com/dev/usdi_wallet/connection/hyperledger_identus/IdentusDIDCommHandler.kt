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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.hyperledger.identus.walletsdk.domain.models.AttachmentData
import org.hyperledger.identus.walletsdk.domain.models.DID
import org.hyperledger.identus.walletsdk.domain.models.Message
import org.hyperledger.identus.walletsdk.edgeagent.protocols.ProtocolType
import org.hyperledger.identus.walletsdk.edgeagent.protocols.issueCredential.IssueCredential
import org.hyperledger.identus.walletsdk.edgeagent.protocols.issueCredential.OfferCredential
import org.hyperledger.identus.walletsdk.edgeagent.protocols.outOfBand.OutOfBandInvitation
import org.hyperledger.identus.walletsdk.mercury.DIDCommProtocol
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64

class IdentusDIDCommHandler(
    private val context: Context,
//    private val mediatorDID: String
) : ProtocolHandler, PersistConnectionCapable, MessageCapable {
    override val protocolId: String = "IdentusDIDCommHandler"
    private val sdk: HyperledgerIdentusSdk by lazy { HyperledgerIdentusSdk.getInstance() }
    private lateinit var myDID: DID
    private var isInitialized = false
    private val agentLock = Mutex()
    private var messageListenerJob: Job? = null
    private var offerCount = 0
    private var issueCount = 0

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

                input.contains(ProtocolType.DidcommIssueCredential.value) ||
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

            emitAndLog(OperationState.InProgress("Received message: $message"))

            when (message.piuri) {
                ProtocolType.DidcommOfferCredential.value if offerCount < 1 -> {
                    emitAndLog(OperationState.InProgress("Processing credential offer..."))

                    try {
//                        val cleanedMessage = cleanOfferMessage(message)
                        val offer = OfferCredential.fromMessage(message)

                        val request = sdk.agent.prepareRequestCredentialWithIssuer(myDID, offer)
                        sdk.agent.sendMessage(request.makeMessage())

                        emitAndLog(OperationState.InProgress("Credential request sent. Waiting for credential"))

                        offerCount++

                    } catch (parseError: Exception) {
                        // Log the full message for debugging
                        Logger.e(protocolId) { "Failed to parse offer: ${parseError.message}" }
                        Logger.e(protocolId) { "Message body: ${message.body}" }

                        // Try manual parsing if needed
                        emitAndLog(OperationState.Error("Credential offer format not supported: ${parseError.message}"))
                    }
                }
                ProtocolType.DidcommIssueCredential.value if issueCount < 1 -> {
                    emitAndLog(OperationState.InProgress("Receiving credential..."))

                    emitAndLog(OperationState.InProgress("Parsing credential..."))
                    cleanIssueCredentialMessage(message)
                    val issueCredential = IssueCredential.fromMessage(message)

                    emitAndLog(OperationState.InProgress("Credential parsed: $issueCredential"))

                    emitAndLog(OperationState.InProgress("Processing credential..."))
                    val credential = sdk.agent.processIssuedCredentialMessage(issueCredential)

                    emitAndLog(OperationState.CredentialReceived(
                        credentialId = credential.id,
                        credentialType = credential.subject ?: "Unknown",
                        issuer = credential.issuer,
                        claims = credential.claims.toString()
                    ))

                    issueCount++
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
                        context = context,
                    )
                    delay(1000)

                    myDID = sdk.agent.createNewPrismDID()
                    sdk.agent.updateMediatorWithDID(myDID)

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

    private fun cleanOfferMessage(message: Message): Message {
        try {
            val bodyJson = Json.parseToJsonElement(message.body).jsonObject.toMutableMap()

            val credentialPreview = bodyJson["credential_preview"]?.jsonObject?.toMutableMap()
            if (credentialPreview != null) {
                credentialPreview.remove("schema_ids")

                credentialPreview["body"]?.jsonObject?.let { body ->
                    val bodyMap = body.toMutableMap()
                    bodyMap["attributes"]?.jsonArray?.let { attributes ->
                        val fixedAttributes = attributes.map { attr ->
                            val attrObj = attr.jsonObject.toMutableMap()
                            if (!attrObj.containsKey("media_type")) {
                                attrObj["media_type"] = JsonNull
                            }
                            JsonObject(attrObj)
                        }
                        bodyMap["attributes"] = JsonArray(fixedAttributes)
                    }
                    credentialPreview["body"] = JsonObject(bodyMap)
                }
                bodyJson["credential_preview"] = JsonObject(credentialPreview)
            }

            return Message(
                id = message.id,
                piuri = message.piuri,
                from = message.from,
                to = message.to,
                fromPrior = message.fromPrior,
                body = Json.encodeToString(bodyJson),
                extraHeaders = message.extraHeaders,
                createdTime = message.createdTime,
                expiresTimePlus = message.expiresTimePlus,
                attachments = message.attachments,
                thid = message.thid,
                pthid = message.pthid,
                ack = message.ack,
                direction = message.direction
            )
        } catch (e: Exception) {
            Logger.e(protocolId) { "Failed to clean offer message: ${e.message}" }
            return message
        }
    }

    private fun cleanIssueCredentialMessage(message: Message): Message {
        try {
            val attachments = message.attachments.map { attachment ->
                val attachmentData = attachment.data as? AttachmentData.AttachmentBase64 ?: return@map attachment

                // Decode the JWT payload
                val jwtString = String(Base64.decode(attachmentData.base64))
                val parts = jwtString.split(".")

                if (parts.size != 3) return@map attachment

                // Decode the payload (middle part)
                val payloadJson = String(Base64.decode(parts[1]))
                val payloadObject = Json.parseToJsonElement(payloadJson).jsonObject.toMutableMap()
                Logger.d(protocolId) { payloadObject.toString() }
                // Fix the vc.credentialSchema field
                val vc = payloadObject["vc"]?.jsonObject?.toMutableMap()
                if (vc != null) {
                    val credentialSchema = vc["credentialSchema"]
                    if (credentialSchema is JsonArray && credentialSchema.isNotEmpty()) {
                        // Take the first element if it's an array
                        vc["credentialSchema"] = credentialSchema.first()
                        payloadObject["vc"] = JsonObject(vc)
                    }
                }

                // Re-encode the payload
                val newPayload = Json.encodeToString(JsonObject(payloadObject))
                val newPayloadBase64 = Base64.encode(newPayload.toByteArray())

                // Reconstruct the JWT (header.newPayload.signature)
                val newJwt = "${parts[0]}.$newPayloadBase64.${parts[2]}"
                val newBase64 = Base64.encode(newJwt.toByteArray())

                attachment.copy(
                    data = AttachmentData.AttachmentBase64(base64 = newBase64)
                )
            }.toTypedArray()  // Convert List to Array

            return message.copy(attachments = attachments)
        } catch (e: Exception) {
            Logger.e(protocolId) { "Failed to clean issue credential message: ${e.message}" }
            return message
        }
    }
}

//curl -X 'POST'   'http://localhost:8085/issue-credentials/credential-offers'   -H 'Content-Type: application/json'   -d '{
//    "validityPeriod": 3600,
//    "credentialFormat": "JWT",
//    "claims": {
//        "emailAddress": "alice@wonderland.com",
//        "givenName": "Alice",
//        "familyName": "Wonderland",
//        "dateOfIssuance": "2024-01-30T00:00:00Z",
//        "faculty": "Computer Science",
//        "gpa": 3
//    },
//    "schemaId": "http://192.168.105.99:8085/schema-registry/schemas/8a46cfe9-4ef7-375e-8243-c4c28547b77a/schema",
//    "credentialDefinitionId": "8a46cfe9-4ef7-375e-8243-c4c28547b77a",
//    "automaticIssuance": true,
//    "connectionId": "d1d006a2-3b92-4a00-9fcf-07a423bcff55",
//    "issuingDID": "did:prism:1264d16d2c119bc731453dfc24bb8c0651696b98e7e8cc4d15977a8a5a00c348",
//    "goalCode": "issue-vc",
//    "goal": "test-wallet",
//    "domain": "faber-college-jwt-vc"
//}'
