package com.dev.usdi_wallet.hyperledger_identus

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import co.touchlab.kermit.Logger
class VdrStatusListClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val bearerToken: String = DEFAULT_BEARER_TOKEN,
    private val httpClient: HttpClient = HttpClient(OkHttp),
) {
    suspend fun isCredentialActive(credentialId: String, index: String): Boolean {
        Logger.d("clmia s eo chay")
        val response = httpClient.get(statusListEndpoint(credentialId, index)) {
            header(HttpHeaders.Authorization, bearerToken)
            contentType(ContentType.Application.Json)
            setBody("")
        }
        val responseText = response.bodyAsText()
        Logger.d("responseText: $responseText")
        if (!response.status.isSuccess()) {
            error("VDR status-list request failed with HTTP ${response.status.value}: $responseText")
        }
        return parseStatusListResponse(responseText)
            ?: error("Unexpected VDR status-list response: $responseText")
    }

    private fun statusListEndpoint(credentialId: String, index: String): String {
        Logger.d("con chim khong lo ")
        val normalizedCredentialId = credentialId.credentialDefinitionId()
        Logger.d("full credential Id: $normalizedCredentialId")
        val url = "$baseUrl/v1/status-lists/${normalizedCredentialId.pathSegment()}/${index}"
        Logger.d("url send to VDR: $url")
        return url
    }

    private fun String.pathSegment(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun String.credentialDefinitionId(): String {
        val trimmed = trim()
            .substringBefore('?')
            .substringBefore('#')
            .trimEnd('/')
        if (trimmed.isBlank()) return trimmed

        val definitionsMarker = "/definitions/"
        if (definitionsMarker in trimmed) {
            return trimmed.substringAfter(definitionsMarker)
                .substringBefore('/')
                .takeIf { it.isNotBlank() }
                ?: trimmed
        }

        return trimmed
    }

    companion object {
        private const val DEFAULT_BASE_URL = "http://13.90.44.25:9001"
        private const val DEFAULT_BEARER_TOKEN = "Bearer change-this-api-token"
    }
}

internal fun parseStatusListResponse(responseText: String): Boolean? {
    val trimmed = responseText.trim()
    Logger.d("result: $trimmed")
    if (trimmed.equals("true", ignoreCase = true)) return true
    else return false


}

private fun JSONObject.optNullableBoolean(key: String): Boolean? {
    if (!has(key) || isNull(key)) return null
    return when (val value = opt(key)) {
        is Boolean -> value
        is String -> when {
            value.equals("true", ignoreCase = true) -> true
            value.equals("false", ignoreCase = true) -> false
            else -> null
        }
        else -> null
    }
}
