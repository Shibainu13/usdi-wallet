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

data class LocalAnonCredProblemReport(
    val messageId: String = UUID.randomUUID().toString(),
    val threadId: String,
    val description: String,
)

object LocalAnonCredBluetoothExchange {
    private val localRequestIds = mutableSetOf<String>()
    private var presentationSender: (suspend (LocalAnonCredProofMessage) -> Unit)? = null
    private var problemReportSender: (suspend (LocalAnonCredProblemReport) -> Unit)? = null

    fun registerPresentationSender(sender: suspend (LocalAnonCredProofMessage) -> Unit) {
        presentationSender = sender
    }

    fun registerProblemReportSender(sender: suspend (LocalAnonCredProblemReport) -> Unit) {
        problemReportSender = sender
    }

    fun clearPresentationSender() {
        presentationSender = null
    }

    fun clearProblemReportSender() {
        problemReportSender = null
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

    suspend fun sendProblemReport(threadId: String, description: String): Boolean {
        val sender = problemReportSender ?: return false
        sender(
            LocalAnonCredProblemReport(
                threadId = threadId,
                description = description,
            )
        )
        return true
    }

    const val MESSAGE_TYPE_REQUEST_PRESENTATION = "request-presentation"
    const val MESSAGE_TYPE_PRESENTATION = "presentation"
    const val HOLDER_DENIED_PROOF_REQUEST = "Proof request denied by holder"
}

class IdentusAnonBluetoothProofManager {
    private val sdk = HyperledgerIdentusSdk.getInstance()
    private val json = Json { ignoreUnknownKeys = true }
    private val vdrStatusListClient = VdrStatusListClient()

    suspend fun createRequest(
        credentialType: VerifiableCredentialType,
        requestedFields: List<RequestedField>,
    ): LocalAnonCredProofMessage {
        awaitAgent()

        val selectedClaims = requestedFields.filter { it.predicateOperator == null }
        val selectedPredicates = requestedFields.filter { it.predicateOperator != null }
        val credentialDefinitionId = credentialDefinitionRestriction(credentialType)
        val presentationDefinitionRequest = sdk.agent.pollux.createPresentationDefinitionRequest(
            type = CredentialType.ANONCREDS_PROOF_REQUEST,
            presentationClaims = AnoncredsPresentationClaims(
                attributes = requestedAttributes(selectedClaims, credentialType, credentialDefinitionId),
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
        Logger.d(IdentusAnonBluetoothProofManager::class.toString()) { "Received local Bluetooth proof request" }
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

    suspend fun verifyPresentation(
        messageJson: String,
        credentialType: VerifiableCredentialType?,
    ): LocalAnonCredVerificationResult {
        awaitAgent()
        Logger.d(IdentusAnonBluetoothProofManager::class.toString()) { "Received local Bluetooth presentation" }
        val message = decodeMessage(messageJson, Message.Direction.RECEIVED)
        require(message.piuri == ProtocolType.DidcommPresentation.value) {
            "Expected presentation message, received ${message.piuri}"
        }

        return runCatching {
            sdk.agent.pluto.storeMessage(message)
            val isValid = sdk.agent.handlePresentation(message)
            val attributes = extractAnonCredRevealedAttributes(message.toJsonString())
                .ifEmpty { extractAnonCredRevealedAttributes(message.toString()) }

            if (!isValid) {
                LocalAnonCredVerificationResult(
                    messageId = message.id,
                    threadId = message.thid,
                    isValid = false,
                    attributes = attributes,
                    error = "Presentation verification failed",
                )
            } else {
                val revocationStatus = checkVdrRevocationStatus(credentialType, attributes)
                if (!revocationStatus.isActive) {
                    LocalAnonCredVerificationResult(
                        messageId = message.id,
                        threadId = message.thid,
                        isValid = false,
                        attributes = attributes,
                        error = revocationStatus.error ?: PRESENTATION_REVOKED_MESSAGE,
                    )
                } else {
                    LocalAnonCredVerificationResult(
                        messageId = message.id,
                        threadId = message.thid,
                        isValid = true,
                        attributes = attributes,
                    )
                }
            }
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

    private suspend fun checkVdrRevocationStatus(
        credentialType: VerifiableCredentialType?,
        attributes: Map<String, String>,
    ): VdrRevocationStatus {
        val index = revocationIndex(attributes)
            ?: return revocationStatusNotChecked("Presentation is missing revocation index")
        val credentialId = revocationCredentialId(credentialType, attributes)
            ?: return revocationStatusNotChecked("Presentation is missing credential ID for revocation check")

        return runCatching {
            Logger.d(IdentusAnonBluetoothProofManager::class.toString()) {
                "Checking VDR revocation status for credentialId=$credentialId, index=$index"
            }
            val isActive = vdrStatusListClient.isCredentialActive(credentialId, index)
            VdrRevocationStatus(
                isActive = isActive,
                error = if (isActive) null else PRESENTATION_REVOKED_MESSAGE,
            )
        }.getOrElse { error ->
            Logger.e(IdentusAnonBluetoothProofManager::class.toString()) {
                "VDR revocation check failed for credentialId=$credentialId, index=$index: ${error.message}"
            }
            VdrRevocationStatus(
                isActive = false,
                error = "Could not check credential revocation status: ${error.message ?: error::class.simpleName}",
            )
        }
    }

    private fun revocationStatusNotChecked(reason: String): VdrRevocationStatus {
        Logger.w(IdentusAnonBluetoothProofManager::class.toString()) {
            "$reason; accepting valid Bluetooth presentation without VDR revocation check"
        }
        return VdrRevocationStatus(isActive = true)
    }

    private fun requestedAttributes(
        fields: List<RequestedField>,
        credentialType: VerifiableCredentialType,
        credentialDefinitionId: String?,
    ): Map<String, RequestedAttributes> {
        val restrictions = credentialDefinitionId
            ?.takeIf { it.isNotBlank() }
            ?.let { mapOf("cred_def_id" to it) }
            ?: emptyMap()

        return requestedAttributeNames(fields, credentialType).associate { name ->
            name to RequestedAttributes(
                name = name,
                names = setOf(name),
                restrictions = restrictions,
                nonRevoked = null,
            )
        }
    }

    private fun credentialDefinitionRestriction(
        credentialType: VerifiableCredentialType,
    ): String? {
        val candidates = listOfNotNull(
            credentialType.metadata["credentialDefinitionUrl"],
            credentialType.metadata["credentialDefinitionId"],
            credentialType.id,
        ).mapNotNull { value ->
            value.withCredentialDefinitionResourceSuffix().takeIf { it.isNotBlank() }
        }
        val restriction = candidates.firstOrNull { !it.isBareUuid() }
        if (restriction == null && candidates.isNotEmpty()) {
            Logger.w(IdentusAnonBluetoothProofManager::class.toString()) {
                "Skipping bare UUID credential definition restriction candidates=$candidates"
            }
        }
        return restriction
    }

    private fun requestedAttributeNames(
        fields: List<RequestedField>,
        credentialType: VerifiableCredentialType,
    ): List<String> {
        val requestedNames = fields.map { it.field.name }
        if (requestedNames.any { it.normalizedSystemClaimName() == REVOCATION_INDEX_ATTRIBUTE }) {
            return requestedNames
        }

        val schemaIndexName = credentialType.fields
            .firstOrNull { it.name.normalizedSystemClaimName() == REVOCATION_INDEX_ATTRIBUTE }
            ?.name
            ?: REVOCATION_INDEX_ATTRIBUTE

        return requestedNames + schemaIndexName
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

    private fun revocationIndex(attributes: Map<String, String>): String? =
        attributes.firstValueForSystemClaim(REVOCATION_INDEX_ATTRIBUTE)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun revocationCredentialId(
        credentialType: VerifiableCredentialType?,
        attributes: Map<String, String>,
    ): String? =
        attributes.firstValueForSystemClaim("credentialid")
            ?.takeIf { it.isNotBlank() }
            ?: credentialType?.metadata?.get("credentialDefinitionUrl")?.takeIf { it.isNotBlank() }
            ?: credentialType?.metadata?.get("credentialDefinitionId")?.takeIf { it.isNotBlank() }
            ?: credentialType?.id?.takeIf { it.isNotBlank() }

    private fun Map<String, String>.firstValueForSystemClaim(systemClaim: String): String? =
        entries.firstOrNull { (key, _) -> key.normalizedSystemClaimName() == systemClaim }?.value

    private fun String.normalizedSystemClaimName(): String =
        trim()
            .withoutKnownTypeSuffix()
            .filterNot { it == '_' || it == '-' }
            .lowercase()

    private fun String.withoutKnownTypeSuffix(): String {
        val suffix = substringAfterLast("_", missingDelimiterValue = "")
        return if (suffix.lowercase() in KNOWN_TYPE_SUFFIXES) {
            substringBeforeLast("_")
        } else {
            this
        }
    }

    private fun String.isBareUuid(): Boolean = BARE_UUID.matches(trim())

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

    private data class VdrRevocationStatus(
        val isActive: Boolean,
        val error: String? = null,
    )

    private companion object {
        const val PRESENTATION_REVOKED_MESSAGE = "Presentation is already revoked"
        const val REVOCATION_INDEX_ATTRIBUTE = "index"
        val BARE_UUID = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )
        val KNOWN_TYPE_SUFFIXES = setOf("str", "num", "bool", "date")
    }
}
