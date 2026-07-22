package com.dev.usdi_wallet.hyperledger_identus

import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.domain.contact.Contact
import com.dev.usdi_wallet.domain.contact.ContactManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.hyperledger.identus.walletsdk.domain.models.AttachmentData
import org.hyperledger.identus.walletsdk.domain.models.AttachmentDescriptor
import org.hyperledger.identus.walletsdk.domain.models.DID
import org.hyperledger.identus.walletsdk.domain.models.DIDPair
import org.hyperledger.identus.walletsdk.domain.models.Message as SdkMessage
import org.hyperledger.identus.walletsdk.edgeagent.DIDCOMM1
import org.hyperledger.identus.walletsdk.edgeagent.protocols.ProtocolType
import org.hyperledger.identus.walletsdk.edgeagent.protocols.outOfBand.ConnectionlessCredentialOffer
import org.hyperledger.identus.walletsdk.edgeagent.protocols.outOfBand.ConnectionlessRequestPresentation
import org.hyperledger.identus.walletsdk.edgeagent.protocols.outOfBand.OutOfBandInvitation
import org.hyperledger.identus.walletsdk.edgeagent.protocols.outOfBand.PrismOnboardingInvitation
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

class IdentusDIDCommContactManager(
    private val onCredentialOffer: suspend (SdkMessage) -> Unit = {},
    private val onPresentationRequest: suspend (SdkMessage) -> Unit = {},
) : ContactManager {
    private val sdk = HyperledgerIdentusSdk.getInstance()

    override fun canHandle(invitation: String): Boolean {
        return when {
            invitation.contains(ProtocolType.Didcomminvitation.value) ||
                invitation.contains(ProtocolType.DidcommOfferCredential.value) ||
                invitation.contains(ProtocolType.DidcommRequestPresentation.value) ||
                 invitation.contains("_oob") -> true
            else -> false
        }
    }

    override suspend fun parseInvitation(invitation: String) {
        try {
            Logger.d(IdentusDIDCommContactManager::class.toString()) {
                "Parsing invitation"
            }

            handleParsedInvitation(sdk.agent.parseInvitation(invitation))

            Logger.d(IdentusDIDCommContactManager::class.toString()) {
                "Invitation accepted"
            }
        } catch (e: Exception) {
            Logger.w(IdentusDIDCommContactManager::class.toString()) {
                "SDK parser failed (${e.message}), trying normalized OOB parser"
            }

            parseConnectionlessCredentialOfferInvitation(invitation)?.let { message ->
                sdk.agent.pluto.storeMessage(message)
                onCredentialOffer(message)
                Logger.d(IdentusDIDCommContactManager::class.toString()) {
                    "Connectionless credential offer invitation accepted"
                }
                return
            }

            parseConnectionlessPresentationInvitation(invitation)?.let { message ->
                sdk.agent.pluto.storeMessage(message)
                onPresentationRequest(message)
                Logger.d(IdentusDIDCommContactManager::class.toString()) {
                    "Connectionless presentation invitation accepted"
                }
                return
            }

            val normalizedInvitation = normalizedOobInvitation(invitation)
            if (normalizedInvitation == null) {
                Logger.e(IdentusDIDCommContactManager::class.toString()) {
                    "Error while parsing invitation $invitation: ${e.message}"
                }
                throw e
            }

            try {
                handleParsedInvitation(sdk.agent.parseInvitation(normalizedInvitation))
                Logger.d(IdentusDIDCommContactManager::class.toString()) {
                    "Normalized invitation accepted"
                }
            } catch (fallbackError: Exception) {
                Logger.e(IdentusDIDCommContactManager::class.toString()) {
                    "Error while parsing normalized invitation: ${fallbackError.message}"
                }
                throw fallbackError
            }
        }
    }

    private suspend fun parseConnectionlessCredentialOfferInvitation(invitation: String): SdkMessage? =
        parseConnectionlessMessageInvitation(
            invitation = invitation,
            piuri = ProtocolType.DidcommOfferCredential.value,
        )

    private suspend fun parseConnectionlessPresentationInvitation(invitation: String): SdkMessage? {
        return parseConnectionlessMessageInvitation(
            invitation = invitation,
            piuri = ProtocolType.DidcommRequestPresentation.value,
        )
    }

    private suspend fun parseConnectionlessMessageInvitation(
        invitation: String,
        piuri: String,
    ): SdkMessage? {
        val decoded = decodeOobInvitation(invitation) ?: return null
        val root = runCatching { JSONObject(decoded) }.getOrNull() ?: return null
        val request = embeddedDidCommMessageJson(root, piuri) ?: return null

        val from = didString(request.opt("from")).ifBlank { didString(root.opt("from")) }
        if (from.isBlank()) return null

        val toDid = didString(request.opt("to"))
            .ifBlank { sdk.agent.createNewPeerDID(updateMediator = true).toString() }

        val body = when (val bodyValue = request.opt("body")) {
            is JSONObject -> bodyValue.toString()
            is String -> bodyValue.ifBlank { "{}" }
            else -> "{}"
        }

        val attachments = request.optJSONArray("attachments")
            ?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.toAttachmentDescriptor()
                }
            }
            .orEmpty()

        if (attachments.isEmpty() && piuri == ProtocolType.DidcommRequestPresentation.value) {
            return null
        }

        return SdkMessage(
            id = request.optString("id").ifBlank { UUID.randomUUID().toString() },
            piuri = piuri,
            from = DID(from),
            to = DID(toDid),
            body = body,
            attachments = attachments.toTypedArray(),
            thid = request.optString("thid")
                .ifBlank { root.optString("id") }
                .ifBlank { request.optString("id") },
            direction = SdkMessage.Direction.RECEIVED,
        )
    }

    private fun embeddedDidCommMessageJson(root: JSONObject, piuri: String): JSONObject? {
        if (root.optString("type") == piuri) {
            return root
        }

        listOf("attachments", "requests~attach").forEach { key ->
            root.optJSONArray(key)?.let { attachments ->
                (0 until attachments.length()).forEach { index ->
                    val request = attachments.optJSONObject(index)?.attachmentJson()
                    if (request?.optString("type") == piuri) {
                        return request
                    }
                }
            }
        }

        listOf(
            "invitation",
            "oobInvitation",
            "outOfBandInvitation",
            "requestPresentation",
            "offerCredential",
            "credentialOffer",
        ).forEach { key ->
            root.optJSONObject(key)?.let { nested ->
                embeddedDidCommMessageJson(nested, piuri)?.let { return it }
            }
        }

        return null
    }

    private fun JSONObject.toAttachmentDescriptor(): AttachmentDescriptor? {
        val data = optJSONObject("data")?.toAttachmentData() ?: return null
        return AttachmentDescriptor(
            id = optString("id").ifBlank { UUID.randomUUID().toString() },
            mediaType = optString("media_type").ifBlank { optString("mediaType") }.ifBlank { null },
            data = data,
            format = optString("format").ifBlank { null },
        )
    }

    private fun JSONObject.toAttachmentData(): AttachmentData? {
        optString("base64").ifBlank { null }?.let { base64 ->
            return AttachmentData.AttachmentBase64(base64)
        }

        return when (val jsonValue = opt("json")) {
            is JSONObject -> AttachmentData.AttachmentJsonData(jsonValue.toString())
            is JSONArray -> AttachmentData.AttachmentJsonData(jsonValue.toString())
            is String -> AttachmentData.AttachmentJsonData(jsonValue)
            else -> null
        }
    }

    private fun JSONObject.attachmentJson(): JSONObject? {
        val data = optJSONObject("data") ?: return null
        data.optJSONObject("json")?.let { return it }
        data.optString("json").ifBlank { null }?.let { value ->
            return runCatching { JSONObject(value) }.getOrNull()
        }
        data.optString("base64").ifBlank { null }?.let { encoded ->
            return runCatching {
                JSONObject(String(decodeBase64(encoded), StandardCharsets.UTF_8))
            }.getOrNull()
        }
        return null
    }

    private fun didString(value: Any?): String =
        when (value) {
            is String -> value
            is JSONArray -> if (value.length() > 0) value.optString(0) else ""
            else -> ""
        }

    private suspend fun handleParsedInvitation(invitation: org.hyperledger.identus.walletsdk.edgeagent.protocols.outOfBand.InvitationType) {
        when (invitation) {
                is OutOfBandInvitation -> {
                    sdk.agent.acceptOutOfBandInvitation(invitation)
                }

                is PrismOnboardingInvitation -> {
                    sdk.agent.acceptInvitation(invitation)
                }

                is ConnectionlessCredentialOffer -> {
                    val message = invitation.offerCredential.makeMessage()
                    sdk.agent.pluto.storeMessage(message)
                    onCredentialOffer(message)
                }

                is ConnectionlessRequestPresentation -> {
                    val message = invitation.requestPresentation.makeMessage()
                    sdk.agent.pluto.storeMessage(message)
                    onPresentationRequest(message)
                }
        }
    }

    private fun normalizedOobInvitation(invitation: String): String? {
        val decoded = decodeOobInvitation(invitation) ?: return null
        val json = runCatching { JSONObject(decoded) }.getOrNull() ?: return null
        var changed = false
        val now = System.currentTimeMillis() / 1000

        if (json.optLong("created_time", 0) <= 0L) {
            json.put("created_time", now)
            changed = true
        }

        if (json.optLong("expires_time", 0) <= now) {
            json.put("expires_time", now + 3600)
            changed = true
        }

        val from = json.opt("from")
        if (from is JSONArray && from.length() > 0) {
            json.put("from", from.optString(0))
            changed = true
        }

        if (!changed) return null

        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(json.toString().toByteArray(StandardCharsets.UTF_8))
        return "https://usdi-wallet.local?_oob=$encoded"
    }

    private fun decodeOobInvitation(invitation: String): String? {
        if (invitation.trimStart().startsWith("{")) return invitation

        val rawOob = invitation.substringAfter("_oob=", missingDelimiterValue = "")
            .substringBefore("&")
            .ifBlank { return null }
        val oob = URLDecoder.decode(rawOob, StandardCharsets.UTF_8.name())
        return runCatching {
            String(decodeBase64(oob), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun decodeBase64(value: String): ByteArray {
        val padded = value.padEnd(value.length + ((4 - value.length % 4) % 4), '=')
        return runCatching { Base64.getUrlDecoder().decode(padded) }
            .getOrElse { Base64.getDecoder().decode(padded) }
    }

    override fun getContacts(): Flow<List<Contact>> {
        return flow<List<Contact>> {
            emit(emptyList())
            val contacts = sdk.pluto.getAllDidPairs().map { pairs ->
                pairs.map { toUsdiContact(it) }
            }
            Logger.d(IdentusDIDCommContactManager::class.toString()) {
                "Getting contacts: $contacts"
            }
            emitAll(contacts)
        }.catch { error ->
            Logger.w(IdentusDIDCommContactManager::class.toString()) {
                "DIDComm contacts unavailable: ${error.message}"
            }
            emit(emptyList())
        }
    }

    override fun removeContact(contact: Contact) {
        TODO("Not yet implemented")
    }

    fun toUsdiContact(didPair: DIDPair): Contact {

        val result=Contact(
            holder = didPair.holder.toString(),
            name = didPair.name ?: "Unknown",
            protocol = DIDCOMM1,
        )
        Logger.d(IdentusDIDCommContactManager::class.toString()) {
            "Converting to contact: $result"
        }
        return result
    }
}
