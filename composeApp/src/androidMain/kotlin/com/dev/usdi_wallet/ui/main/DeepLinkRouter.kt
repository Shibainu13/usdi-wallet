package com.dev.usdi_wallet.ui.main

import android.content.Intent
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.domain.protocol.Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

enum class DeepLinkContentType {
    Credential,
    Presentation,
    Contact,
    Unknown,
}

data class DeepLinkRouteResult(
    val protocolId: String,
    val contentType: DeepLinkContentType,
)

class DeepLinkRouter private constructor(
    private val protocols: List<Protocol<*, *>>,
    private val scope: CoroutineScope,
) {
    fun handle(intent: Intent) {
        if (Intent.ACTION_VIEW != intent.action) return
        val uri = intent.data ?: return
        handle(uri.toString())
    }

    fun handle(link: String): Job =
        handle(link, onSuccess = {}, onError = {})

    fun handle(
        link: String,
        onSuccess: (DeepLinkRouteResult) -> Unit,
        onError: (String) -> Unit,
    ): Job = scope.launch {
        runCatching {
            routeToContactManager(link)
        }.onSuccess(onSuccess)
            .onFailure { error ->
                val message = error.message ?: "Unable to handle link"
                Logger.e(DeepLinkRouter::class.toString()) {
                    "Failed to handle deep link: $message"
                }
                onError(message)
            }
    }

    private suspend fun routeToContactManager(uri: String): DeepLinkRouteResult {
        val trimmed = invitationUrlFromInput(uri.trim())
        if (trimmed.isBlank()) error("Empty URL")

        Logger.d(DeepLinkRouter::class.toString()) {
            "Handling deep link: $trimmed"
        }

        val protocol = protocols.firstOrNull { protocol ->
            protocol.contactManager.canHandle(trimmed)
        }

        if (protocol == null) {
            Logger.w(DeepLinkRouter::class.toString()) {
                "No contact protocol found for $trimmed"
            }
            error("Unsupported invitation or credential URL")
        }

        Logger.d(DeepLinkRouter::class.toString()) {
            "Routing $trimmed to ${protocol::class.simpleName}"
        }

        protocol.contactManager.parseInvitation(trimmed)
        return DeepLinkRouteResult(
            protocolId = protocol.protocolId,
            contentType = classify(trimmed),
        )
    }

    private fun invitationUrlFromInput(input: String): String {
        if (!input.trimStart().startsWith("{")) return input

        val json = runCatching { JSONObject(input) }.getOrNull() ?: return input
        return invitationUrl(json).ifBlank { input }
    }

    private fun invitationUrl(json: JSONObject): String =
        json.optString("invitationUrl")
            .ifBlank { json.optString("invitation_url") }
            .ifBlank { json.optString("invitationURL") }
            .ifBlank { json.optString("url") }
            .ifBlank { json.optString("oobUrl") }
            .ifBlank { json.optString("outOfBandInvitationUrl") }
            .ifBlank { json.optString("credentialOfferInvitationUrl") }
            .ifBlank { nestedInvitationUrl(json) }

    private fun nestedInvitationUrl(json: JSONObject): String {
        listOf("invitation", "oobInvitation", "outOfBandInvitation").forEach { key ->
            when (val value = json.opt(key)) {
                is JSONObject -> invitationUrl(value).ifBlank { encodedOobUrl(value) }.let {
                    if (it.isNotBlank()) return it
                }
                is String -> {
                    if (value.contains("_oob=")) return value
                    runCatching { JSONObject(value) }.getOrNull()?.let { nested ->
                        invitationUrl(nested).ifBlank { encodedOobUrl(nested) }.let {
                            if (it.isNotBlank()) return it
                        }
                    }
                }
            }
        }
        return ""
    }

    private fun encodedOobUrl(invitation: JSONObject): String {
        if (!invitation.optString("type").contains("out-of-band")) return ""
        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(invitation.toString().toByteArray(StandardCharsets.UTF_8))
        return "https://usdi-wallet.local?_oob=$encoded"
    }

    private fun classify(uri: String): DeepLinkContentType {
        val text = listOf(uri, decodeOobInvitation(uri).orEmpty())
            .joinToString(separator = "\n")
            .lowercase()

        return when {
            text.contains("credential_offer=") ||
                text.startsWith("openid-credential-offer://") ||
                text.startsWith("eudi-openid4ci://authorize") ||
                text.contains("offer-credential") ||
                text.contains("issue-credential") ||
                text.contains("credential-offer") -> DeepLinkContentType.Credential

            text.startsWith("openid4vp://") ||
                text.startsWith("mdoc-openid4vp://") ||
                text.contains("response_type=vp_token") ||
                text.contains("request-presentation") ||
                text.contains("present-proof") -> DeepLinkContentType.Presentation

            text.contains("didcomm") ||
                text.contains("_oob=") -> DeepLinkContentType.Contact

            else -> DeepLinkContentType.Unknown
        }
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

    companion object {
        private var _instance: DeepLinkRouter? = null

        fun getInstance(protocols: List<Protocol<*, *>>, scope: CoroutineScope): DeepLinkRouter =
            _instance ?: DeepLinkRouter(protocols, scope).also { _instance = it }

        fun getInstance(): DeepLinkRouter = _instance!!
    }
}
