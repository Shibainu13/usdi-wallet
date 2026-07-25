package com.dev.usdi_wallet.hyperledger_identus

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import org.json.JSONArray
import org.json.JSONObject

data class CloudAgentAnonCredSchema(
    val guid: String,
    val id: String,
    val name: String,
    val version: String,
    val attrNames: List<String>,
)

data class CloudAgentCredentialDefinition(
    val guid: String,
    val id: String,
    val schemaId: String,
    val name: String,
    val version: String,
    val tag: String,
)

class CloudAgentVerifierClient(
    private val httpClient: HttpClient = HttpClient(OkHttp),
) {
    suspend fun getAnonCredSchemas(
        baseUrl: String,
        apiKey: String?,
    ): List<CloudAgentAnonCredSchema> {
        val responseText = httpClient.get(endpoint(baseUrl, "schema-registry/schemas")) {
            apiKey(apiKey)
        }.bodyAsText()

        return schemaItems(responseText)
            .filter { it.optString("type") == "AnoncredSchemaV1" }
            .mapNotNull { it.toAnonCredSchema() }
    }

    suspend fun getCredentialDefinitions(
        baseUrl: String,
        apiKey: String?,
    ): List<CloudAgentCredentialDefinition> {
        val responseText = httpClient.get(endpoint(baseUrl, "credential-definition-registry/definitions")) {
            accept(ContentType.Application.Json)
            apiKey(apiKey)
        }.bodyAsText()
        Logger.d("base URL: $baseUrl")
        return responseItems(responseText)
            .mapNotNull { it.toCredentialDefinition() }
    }

    suspend fun getAnonCredSchemaById(
        baseUrl: String,
        apiKey: String?,
        schemaId: String,
    ): CloudAgentAnonCredSchema? {
        if (schemaId.isBlank()) return null
        val responseText = httpClient.get(schemaEndpoint(baseUrl, schemaId)) {
            accept(ContentType.Application.Json)
            apiKey(apiKey)
        }.bodyAsText()

        return JSONObject(responseText).toAnonCredSchema()
    }

    private fun schemaItems(responseText: String): List<JSONObject> {
        return responseItems(responseText)
    }

    private fun responseItems(responseText: String): List<JSONObject> {
        val trimmed = responseText.trim()
        if (trimmed.startsWith("[")) {
            return JSONArray(trimmed).jsonObjects()
        }

        val response = JSONObject(trimmed)
        val list = response.optJSONArray("contents")
            ?: response.optJSONArray("items")
            ?: response.optJSONArray("schemas")
            ?: return emptyList()
        return list.jsonObjects()
    }

    private fun JSONArray.jsonObjects(): List<JSONObject> =
        (0 until length()).mapNotNull { index -> optJSONObject(index) }

    private fun JSONObject.toAnonCredSchema(): CloudAgentAnonCredSchema? {
        val schema = optJSONObject("schema") ?: this
        val attrs = (schema.optJSONArray("attrNames") ?: optJSONArray("attrNames"))
            ?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optString(index).ifBlank { null }
                }
            }
            ?: emptyList()

        if (attrs.isEmpty()) return null

        val guid = optString("guid")
        val id = optString("id")
            .ifBlank { optString("schemaId") }
            .ifBlank { optString("self") }

        return CloudAgentAnonCredSchema(
            guid = guid,
            id = id,
            name = schema.optString("name").ifBlank { optString("name") }.ifBlank { guid },
            version = schema.optString("version").ifBlank { optString("version") },
            attrNames = attrs,
        )
    }

    private fun JSONObject.toCredentialDefinition(): CloudAgentCredentialDefinition? {
        val guid = optString("guid")
        if (guid.isBlank()) return null

        val credentialDefinition = optJSONObject("credentialDefinition")
            ?: optJSONObject("definition")
            ?: this

        return CloudAgentCredentialDefinition(
            guid = guid,
            id = optString("id")
                .ifBlank { optString("credentialDefinitionId") }
                .ifBlank { optString("self") },
            schemaId = optString("schemaId").ifBlank { credentialDefinition.optString("schemaId") },
            name = optString("name").ifBlank { credentialDefinition.optString("name") }.ifBlank { guid },
            version = optString("version").ifBlank { credentialDefinition.optString("version") },
            tag = optString("tag").ifBlank { credentialDefinition.optString("tag") },
        )
    }

    private fun endpoint(baseUrl: String, path: String): String =
        "${baseUrl.trimEnd('/')}/$path"

    private fun schemaEndpoint(baseUrl: String, schemaId: String): String =
        if (schemaId.startsWith("http://") || schemaId.startsWith("https://")) {
            schemaId
        } else {
            endpoint(baseUrl, schemaId.trimStart('/'))
        }

    private fun io.ktor.client.request.HttpRequestBuilder.apiKey(apiKey: String?) {
        if (!apiKey.isNullOrBlank()) {
            header("apikey", apiKey)
        }
    }
}
