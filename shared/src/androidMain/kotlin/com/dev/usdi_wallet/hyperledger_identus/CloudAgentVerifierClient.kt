package com.dev.usdi_wallet.hyperledger_identus

import com.dev.usdi_wallet.domain.credential.Claim
import com.dev.usdi_wallet.domain.credential.Predicate
import com.dev.usdi_wallet.domain.credential.PredicateOperator
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

data class CloudAgentConnectionInvitation(
    val connectionId: String,
    val invitationUrl: String,
    val state: String? = null,
)

data class CloudAgentProofRequestResult(
    val presentationId: String?,
    val status: String?,
    val raw: String,
)

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

    suspend fun createConnectionInvitation(
        baseUrl: String,
        apiKey: String?,
        label: String,
    ): CloudAgentConnectionInvitation {
        val body = JSONObject()
            .put("label", label.ifBlank { "usdi-mobile-verifier" })
            .put("goalCode", "present-proof")
            .put("goal", "Mobile verifier connection")

        val responseText = httpClient.post(endpoint(baseUrl, "connections")) {
            contentType(ContentType.Application.Json)
            apiKey(apiKey)
            setBody(body.toString())
        }.bodyAsText()

        val response = JSONObject(responseText)
        return CloudAgentConnectionInvitation(
            connectionId = connectionId(response),
            invitationUrl = invitationUrl(response),
            state = response.optString("state").ifBlank { null },
        )
    }

    suspend fun getConnection(
        baseUrl: String,
        apiKey: String?,
        connectionId: String,
    ): CloudAgentConnectionInvitation {
        val responseText = httpClient.get(endpoint(baseUrl, "connections/$connectionId")) {
            apiKey(apiKey)
        }.bodyAsText()

        val response = JSONObject(responseText)
        return CloudAgentConnectionInvitation(
            connectionId = connectionId(response).ifBlank { connectionId },
            invitationUrl = invitationUrl(response),
            state = response.optString("state").ifBlank { null },
        )
    }

    suspend fun sendAnonCredProofRequest(
        baseUrl: String,
        apiKey: String?,
        connectionId: String,
        claims: List<Claim>,
        predicates: List<Predicate>,
        credentialDefinitionId: String?,
        requestName: String,
    ): CloudAgentProofRequestResult {
        val proofRequest = JSONObject()
            .put("requested_attributes", requestedAttributes(claims, credentialDefinitionId))
            .put("requested_predicates", requestedPredicates(predicates, credentialDefinitionId))
            .put("name", requestName.ifBlank { "Mobile verifier proof" })
            .put("nonce", numericNonce())
            .put("version", "1.0")

        val body = JSONObject()
            .put("connectionId", connectionId)
            .put("credentialFormat", "AnonCreds")
            .put("proofs", JSONArray())
            .put("anoncredPresentationRequest", proofRequest)

        val responseText = httpClient.post(endpoint(baseUrl, "present-proof/presentations")) {
            contentType(ContentType.Application.Json)
            apiKey(apiKey)
            setBody(body.toString())
        }.bodyAsText()

        val response = JSONObject(responseText)
        return CloudAgentProofRequestResult(
            presentationId = response.optString("presentationId")
                .ifBlank { response.optString("id") }
                .ifBlank { response.optString("recordId") }
                .ifBlank { null },
            status = response.optString("status")
                .ifBlank { response.optString("state") }
                .ifBlank { null },
            raw = responseText,
        )
    }

    private fun requestedAttributes(
        claims: List<Claim>,
        credentialDefinitionId: String?,
    ): JSONObject {
        val attributes = JSONObject()
        claims.forEach { claim ->
            attributes.put(
                "${claim.name}_attr",
                JSONObject()
                    .put("name", claim.name)
                    .putRestrictions(credentialDefinitionId),
            )
        }
        return attributes
    }

    private fun requestedPredicates(
        predicates: List<Predicate>,
        credentialDefinitionId: String?,
    ): JSONObject {
        val requestedPredicates = JSONObject()
        predicates.forEach { predicate ->
            requestedPredicates.put(
                "${predicate.name}_predicate",
                JSONObject()
                    .put("name", predicate.name)
                    .put("p_type", predicate.operator.anonCredOperator())
                    .put("p_value", predicate.value)
                    .putRestrictions(credentialDefinitionId),
            )
        }
        return requestedPredicates
    }

    private fun JSONObject.putRestrictions(credentialDefinitionId: String?): JSONObject {
        if (!credentialDefinitionId.isNullOrBlank()) {
            put(
                "restrictions",
                JSONArray().put(JSONObject().put("cred_def_id", credentialDefinitionId)),
            )
        }
        return this
    }

    private fun PredicateOperator.anonCredOperator(): String =
        when (this) {
            PredicateOperator.GREATER_THAN -> ">"
            PredicateOperator.GREATER_THAN_OR_EQUAL -> ">="
            PredicateOperator.LESS_THAN -> "<"
            PredicateOperator.LESS_THAN_OR_EQUAL -> "<="
        }

    private fun numericNonce(): String =
        System.currentTimeMillis().toString() + (100000..999999).random().toString()

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

    private fun connectionId(response: JSONObject): String =
        response.optString("connectionId")
            .ifBlank { response.optString("connection_id") }
            .ifBlank { response.optString("id") }
            .ifBlank { response.optString("guid") }

    private fun invitationUrl(response: JSONObject): String =
        response.optString("invitationUrl")
            .ifBlank { response.optString("invitation_url") }
            .ifBlank { response.optString("invitationURL") }
            .ifBlank { nestedInvitationUrl(response) }
            .ifBlank { encodedInvitationUrl(response) }

    private fun nestedInvitationUrl(response: JSONObject): String {
        val keys = listOf("invitation", "oobInvitation", "outOfBandInvitation")
        keys.forEach { key ->
            val value = response.opt(key)
            when (value) {
                is JSONObject -> invitationUrl(value).ifBlank { encodedOobUrl(value) }.let {
                    if (it.isNotBlank()) return it
                }
                is String -> stringInvitationUrl(value).let {
                    if (it.isNotBlank()) return it
                }
            }
        }
        return ""
    }

    private fun encodedInvitationUrl(response: JSONObject): String {
        if (!response.has("type") || !response.optString("type").contains("out-of-band")) {
            return ""
        }
        return encodedOobUrl(response)
    }

    private fun stringInvitationUrl(value: String): String {
        if (value.contains("_oob=")) return value
        val parsed = runCatching { JSONObject(value) }.getOrNull() ?: return ""
        return invitationUrl(parsed).ifBlank { encodedOobUrl(parsed) }
    }

    private fun encodedOobUrl(invitation: JSONObject): String {
        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(invitation.toString().toByteArray())
        return "https://usdi-wallet.local?_oob=$encoded"
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
