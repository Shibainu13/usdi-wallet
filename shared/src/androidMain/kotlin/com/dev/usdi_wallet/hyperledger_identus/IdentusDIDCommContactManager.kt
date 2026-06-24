package com.dev.usdi_wallet.hyperledger_identus

import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.domain.contact.Contact
import com.dev.usdi_wallet.domain.contact.ContactManager
import kotlinx.coroutines.flow.Flow
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
                 invitation.contains("_oob") -> true
            else -> false
        }
    }

    override suspend fun parseInvitation(invitation: String) {
        try {
            Logger.d(this::class.toString()) {
                "IdentusDIDCommContactManager.kt.parseInvitation: Parsing invitation"
            }

            handleParsedInvitation(sdk.agent.parseInvitation(invitation))

            Logger.d(this::class.toString()) {
                "IdentusDIDCommContactManager.kt.parseInvitation: Invitation accepted"
            }
        } catch (e: Exception) {
            Logger.w(this::class.toString()) {
                "IdentusDIDCommContactManager.kt.parseInvitation: SDK parser failed (${e::class.simpleName}: ${e.message}), trying normalized OOB parser"
            }

            parseConnectionlessPresentationInvitation(invitation)?.let { message ->
                sdk.agent.pluto.storeMessage(message)
                onPresentationRequest(message)
                Logger.d(this::class.toString()) {
                    "IdentusDIDCommContactManager.kt.parseInvitation: Connectionless presentation invitation accepted"
                }
                return
            }

            val normalizedInvitation = normalizedOobInvitation(invitation)
            if (normalizedInvitation == null) {
                Logger.e(this::class.toString()) {
                    "IdentusDIDCommContactManager.kt.parseInvitation: Error while parsing invitation $invitation: ${e::class.simpleName}: ${e.message}"
                }
                throw e
            }

            try {
                handleParsedInvitation(sdk.agent.parseInvitation(normalizedInvitation))
                Logger.d(this::class.toString()) {
                    "IdentusDIDCommContactManager.kt.parseInvitation: Normalized invitation accepted"
                }
            } catch (fallbackError: Exception) {
                Logger.e(this::class.toString()) {
                    "IdentusDIDCommContactManager.kt.parseInvitation: Error while parsing normalized invitation: ${fallbackError::class.simpleName}: ${fallbackError.message}"
                }
                throw fallbackError
            }
        }
    }

    private suspend fun parseConnectionlessPresentationInvitation(invitation: String): SdkMessage? {
        val decoded = decodeOobInvitation(invitation) ?: return null
        val root = runCatching { JSONObject(decoded) }.getOrNull() ?: return null
        val request = presentationRequestJson(root) ?: return null
        val type = request.optString("type")
        if (type != ProtocolType.DidcommRequestPresentation.value) return null

        val from = didString(request.opt("from")).ifBlank { didString(root.opt("from")) }
        if (from.isBlank()) return null

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

        if (attachments.isEmpty()) return null

        val to = sdk.agent.createNewPeerDID(updateMediator = true)
        return SdkMessage(
            id = request.optString("id").ifBlank { UUID.randomUUID().toString() },
            piuri = type,
            from = DID(from),
            to = to,
            body = body,
            attachments = attachments.toTypedArray(),
            thid = request.optString("thid")
                .ifBlank { root.optString("id") }
                .ifBlank { request.optString("id") },
            direction = SdkMessage.Direction.RECEIVED,
        )
    }

    private fun presentationRequestJson(root: JSONObject): JSONObject? {
        if (root.optString("type") == ProtocolType.DidcommRequestPresentation.value) {
            return root
        }

        listOf("attachments", "requests~attach").forEach { key ->
            root.optJSONArray(key)?.let { attachments ->
                (0 until attachments.length()).forEach { index ->
                    val request = attachments.optJSONObject(index)?.attachmentJson()
                    if (request?.optString("type") == ProtocolType.DidcommRequestPresentation.value) {
                        return request
                    }
                }
            }
        }

        listOf("invitation", "oobInvitation", "outOfBandInvitation", "requestPresentation").forEach { key ->
            root.optJSONObject(key)?.let { nested ->
                presentationRequestJson(nested)?.let { return it }
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
            val padded = encoded.padEnd(encoded.length + ((4 - encoded.length % 4) % 4), '=')
            return runCatching {
                JSONObject(String(Base64.getUrlDecoder().decode(padded), StandardCharsets.UTF_8))
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
        val padded = oob.padEnd(oob.length + ((4 - oob.length % 4) % 4), '=')
        return runCatching {
            String(Base64.getUrlDecoder().decode(padded), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    override fun getContacts(): Flow<List<Contact>> {
        val result = sdk.pluto.getAllDidPairs().map { pairs ->
            pairs.map { toUsdiContact(it) }
        }
        Logger.d(this::class.toString()) {
            "IdentusDIDCommContactManager.kt.getContacts: Getting contacts: $result"
        }
        return result
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
        Logger.d(this::class.toString()) {
            "IdentusDIDCommContactManager.kt.toUsdiContact: Converting to contact: $result"
        }
        return result
    }

}
