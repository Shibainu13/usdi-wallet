package com.dev.usdi_wallet.hyperledger_identus

import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.domain.credential.PredicateOperator
import com.dev.usdi_wallet.domain.verification.RequestedField
import com.dev.usdi_wallet.domain.verification.VerifiableCredentialType
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.hyperledger.identus.walletsdk.domain.models.AttachmentData
import org.hyperledger.identus.walletsdk.domain.models.AttachmentDescriptor
import org.hyperledger.identus.walletsdk.domain.models.AnoncredsInputFieldFilter
import org.hyperledger.identus.walletsdk.domain.models.AnoncredsPresentationClaims
import org.hyperledger.identus.walletsdk.domain.models.CredentialType
import org.hyperledger.identus.walletsdk.domain.models.DID
import org.hyperledger.identus.walletsdk.domain.models.Message
import org.hyperledger.identus.walletsdk.domain.models.RequestedAttributes
import org.hyperledger.identus.walletsdk.edgeagent.protocols.ProtocolType
import org.hyperledger.identus.walletsdk.edgeagent.protocols.proofOfPresentation.AnoncredsPresentationOptions
import org.hyperledger.identus.walletsdk.edgeagent.protocols.proofOfPresentation.RequestPresentation
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

data class LocalAnonCredProofMessage(
    val messageType: String,
    val messageId: String,
    val threadId: String,
    val messageJson: String,
)

data class LocalAnonCredVerificationResult(
    val messageId: String,
    val threadId: String?,
    val isValid: Boolean,
    val attributes: Map<String, String> = emptyMap(),
    val error: String? = null,
)

object LocalAnonCredBluetoothExchange {
    private val localRequestIds = mutableSetOf<String>()
    private var presentationSender: (suspend (LocalAnonCredProofMessage) -> Unit)? = null

    fun registerPresentationSender(sender: suspend (LocalAnonCredProofMessage) -> Unit) {
        presentationSender = sender
    }

    fun clearPresentationSender() {
        presentationSender = null
    }

    fun markLocalRequest(messageId: String) {
        synchronized(localRequestIds) {
            localRequestIds.add(messageId)
        }
    }

    fun isLocalRequest(messageId: String): Boolean =
        synchronized(localRequestIds) {
            messageId in localRequestIds
        }

    fun clearLocalRequest(messageId: String) {
        synchronized(localRequestIds) {
            localRequestIds.remove(messageId)
        }
    }

    suspend fun sendPresentation(message: Message): Boolean {
        val sender = presentationSender ?: return false
        sender(
            LocalAnonCredProofMessage(
                messageType = MESSAGE_TYPE_PRESENTATION,
                messageId = message.id,
                threadId = message.thid ?: message.id,
                messageJson = message.toJsonString(),
            )
        )
        return true
    }

    const val MESSAGE_TYPE_REQUEST_PRESENTATION = "request-presentation"
    const val MESSAGE_TYPE_PRESENTATION = "presentation"
}

class IdentusAnonBluetoothProofManager {
    private val sdk = HyperledgerIdentusSdk.getInstance()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun createRequest(
        credentialType: VerifiableCredentialType,
        requestedFields: List<RequestedField>,
    ): LocalAnonCredProofMessage {
        awaitAgent()

        val selectedClaims = requestedFields.filter { it.predicateOperator == null }
        val selectedPredicates = requestedFields.filter { it.predicateOperator != null }
        val credentialDefinitionId = credentialType.metadata["credentialDefinitionId"]
            ?: credentialType.metadata["credentialDefinitionUrl"]
        val presentationDefinitionRequest = sdk.agent.pollux.createPresentationDefinitionRequest(
            type = CredentialType.ANONCREDS_PROOF_REQUEST,
            presentationClaims = AnoncredsPresentationClaims(
                attributes = requestedAttributes(selectedClaims, credentialDefinitionId),
                predicates = requestedPredicates(selectedPredicates),
            ),
            options = AnoncredsPresentationOptions(nonce = numericNonce()),
        )

        val threadId = UUID.randomUUID().toString()
        val verifierDid = sdk.agent.createNewPeerDID(updateMediator = false)
        val holderSessionDid = DID("did:peer:bluetooth-holder-$threadId")
        val request = RequestPresentation(
            body = RequestPresentation.Body(
                goalCode = "present-proof",
                comment = "Bluetooth local AnonCreds proof request",
                willConfirm = false,
                proofTypes = emptyArray(),
            ),
            attachments = arrayOf(
                AttachmentDescriptor(
                    mediaType = "application/json",
                    format = CredentialType.PRESENTATION_EXCHANGE_DEFINITIONS.type,
                    data = AttachmentData.AttachmentBase64(presentationDefinitionRequest.base64UrlEncoded()),
                )
            ),
            thid = threadId,
            from = verifierDid,
            to = holderSessionDid,
            direction = Message.Direction.SENT,
        )
        val message = request.makeMessage()

        sdk.agent.pluto.storeMessage(message)
        Logger.d(IdentusAnonBluetoothProofManager::class.toString()) {
            "Created local Bluetooth proof request messageId=${message.id}, thid=${message.thid}"
        }

        return LocalAnonCredProofMessage(
            messageType = LocalAnonCredBluetoothExchange.MESSAGE_TYPE_REQUEST_PRESENTATION,
            messageId = message.id,
            threadId = message.thid ?: threadId,
            messageJson = message.toJsonString(),
        )
    }

    suspend fun receiveRequest(messageJson: String): LocalAnonCredProofMessage {
        awaitAgent()

        val message = decodeMessage(messageJson, Message.Direction.RECEIVED)
        require(message.piuri == ProtocolType.DidcommRequestPresentation.value) {
            "Expected request-presentation message, received ${message.piuri}"
        }

        sdk.agent.pluto.storeMessage(message)
        LocalAnonCredBluetoothExchange.markLocalRequest(message.id)

        val credentialManager = IdentusAnonProtocol.getInstance().credentialManager as? IdentusAnonCredentialManager
            ?: error("Identus AnonCred credential manager is not available")
        credentialManager.enqueuePresentationRequest(message)

        Logger.d(IdentusAnonBluetoothProofManager::class.toString()) {
            "Queued local Bluetooth proof request messageId=${message.id}, thid=${message.thid}"
        }

        return LocalAnonCredProofMessage(
            messageType = LocalAnonCredBluetoothExchange.MESSAGE_TYPE_REQUEST_PRESENTATION,
            messageId = message.id,
            threadId = message.thid ?: message.id,
            messageJson = message.toJsonString(),
        )
    }

    suspend fun verifyPresentation(messageJson: String): LocalAnonCredVerificationResult {
        awaitAgent()

        val message = decodeMessage(messageJson, Message.Direction.RECEIVED)
        require(message.piuri == ProtocolType.DidcommPresentation.value) {
            "Expected presentation message, received ${message.piuri}"
        }

        return runCatching {
            sdk.agent.pluto.storeMessage(message)
            val isValid = sdk.agent.handlePresentation(message)
            val attributes = extractAnonCredRevealedAttributes(message.toJsonString())
                .ifEmpty { extractAnonCredRevealedAttributes(message.toString()) }

            LocalAnonCredVerificationResult(
                messageId = message.id,
                threadId = message.thid,
                isValid = isValid,
                attributes = attributes,
            )
        }.getOrElse { error ->
            Logger.e(IdentusAnonBluetoothProofManager::class.toString()) {
                "Failed to verify local Bluetooth presentation ${message.id}: ${error.message}"
            }
            LocalAnonCredVerificationResult(
                messageId = message.id,
                threadId = message.thid,
                isValid = false,
                error = error.message,
            )
        }
    }

    private fun requestedAttributes(
        fields: List<RequestedField>,
        credentialDefinitionId: String?,
    ): Map<String, RequestedAttributes> {
        val restrictions = credentialDefinitionId
            ?.takeIf { it.isNotBlank() }
            ?.let { mapOf("cred_def_id" to it) }
            ?: emptyMap()

        return fields.associate { field ->
            val name = field.field.name
            name to RequestedAttributes(
                name = name,
                names = setOf(name),
                restrictions = restrictions,
                nonRevoked = null,
            )
        }
    }

    private fun requestedPredicates(
        fields: List<RequestedField>,
    ): Map<String, AnoncredsInputFieldFilter> =
        fields.mapNotNull { field ->
            val operator = field.predicateOperator ?: return@mapNotNull null
            val value = field.predicateValue?.trim()?.toIntOrNull() ?: return@mapNotNull null
            field.field.name to AnoncredsInputFieldFilter(
                type = "NUMBER",
                name = field.field.name,
                gt = value.takeIf { operator == PredicateOperator.GREATER_THAN },
                gte = value.takeIf { operator == PredicateOperator.GREATER_THAN_OR_EQUAL },
                lt = value.takeIf { operator == PredicateOperator.LESS_THAN },
                lte = value.takeIf { operator == PredicateOperator.LESS_THAN_OR_EQUAL },
            )
        }.toMap()

    private fun decodeMessage(value: String, direction: Message.Direction): Message =
        json.decodeFromString<Message>(value).copy(direction = direction)

    private suspend fun awaitAgent() {
        while (!sdk.canUseLocalAgent()) {
            delay(100)
        }
    }

    private fun numericNonce(): String =
        System.currentTimeMillis().toString() + (100000..999999).random().toString()

    private fun String.base64UrlEncoded(): String =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(toByteArray(StandardCharsets.UTF_8))
}
