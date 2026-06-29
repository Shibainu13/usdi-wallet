package com.dev.usdi_wallet.eudi

import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.domain.credential.ClaimType
import com.dev.usdi_wallet.domain.verification.RequestedField
import com.dev.usdi_wallet.domain.verification.VerifiableCredentialType
import com.dev.usdi_wallet.domain.verification.VerifiableFieldSchema
import com.dev.usdi_wallet.domain.verification.VerificationManager
import com.dev.usdi_wallet.domain.verification.VerificationPollResult
import com.dev.usdi_wallet.domain.verification.VerificationProtocol
import com.dev.usdi_wallet.domain.verification.VerificationSession
import com.dev.usdi_wallet.network.WalletHttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.headers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.collections.listOf
import kotlin.time.Duration.Companion.milliseconds

class EudiVerificationManager(
    private val verifierBaseUrl: String
) : VerificationManager {
    private val httpClient = WalletHttpClient.instance
    override suspend fun getSupportedCredentialTypes(): List<VerifiableCredentialType> {
        val supportedCredentialTypes: List<VerifiableCredentialType> = listOf(
            VerifiableCredentialType(
                id = "eudi_pid",
                label = "EU Digital Identity (PID)",
                protocol = VerificationProtocol.EUDI,
                fields = listOf(
                    VerifiableFieldSchema("family_name", "Family name", ClaimType.STRING),
                    VerifiableFieldSchema("given_name", "Given name", ClaimType.STRING),
                    VerifiableFieldSchema("birthdate", "Birthdate", ClaimType.STRING),
                    VerifiableFieldSchema("family_name_birth", "Family name birth", ClaimType.STRING),
                    VerifiableFieldSchema("given_name_birth", "Given name birth", ClaimType.STRING),
                    VerifiableFieldSchema("birth_place", "Birth place", ClaimType.STRING),
                    VerifiableFieldSchema("resident_address", "Resident address", ClaimType.STRING),
                    VerifiableFieldSchema("resident_country", "Resident country", ClaimType.STRING),
                    VerifiableFieldSchema("resident_state", "Resident state", ClaimType.STRING),
                    VerifiableFieldSchema("resident_city", "Resident city", ClaimType.STRING),
                    VerifiableFieldSchema("resident_postal_code", "Resident postal code", ClaimType.STRING),
                    VerifiableFieldSchema("resident_street", "Resident street", ClaimType.STRING),
                    VerifiableFieldSchema("resident_house_number", "Resident house number", ClaimType.STRING),
                    VerifiableFieldSchema("sex", "Sex", ClaimType.STRING),
                    VerifiableFieldSchema("nationality", "Nationality", ClaimType.STRING),
                    VerifiableFieldSchema("issuance_date", "Issuance date", ClaimType.STRING),
                    VerifiableFieldSchema("expiry_date", "Expiry date", ClaimType.STRING),
                    VerifiableFieldSchema("issuing_authority", "Issuing authority", ClaimType.STRING),
                    VerifiableFieldSchema("document_number", "Document number", ClaimType.STRING),
                    VerifiableFieldSchema("personal_administrative_number", "Personal administrative number", ClaimType.STRING),
                    VerifiableFieldSchema("issuing_country", "Issuing country", ClaimType.STRING),
                    VerifiableFieldSchema("issuing_jurisdiction", "Issuing jurisdiction", ClaimType.STRING),
                    VerifiableFieldSchema("portrait", "Portrait", ClaimType.STRING),
                    VerifiableFieldSchema("email_address", "Email address", ClaimType.STRING),
                    VerifiableFieldSchema("mobile_phone_number", "Mobile phone number", ClaimType.STRING),
                    VerifiableFieldSchema("trust_anchor", "Trust anchor", ClaimType.STRING)
                ),
                metadata = mapOf("docType" to "urn:eudi:pid:1")
            )
        )
        return supportedCredentialTypes
    }

    override suspend fun startVerification(
        credentialType: VerifiableCredentialType,
        requestedFields: List<RequestedField>
    ): VerificationSession {
        val docType = credentialType.metadata["docType"]
            ?: error("EUDI credential type missing docType data")

        val dcqlQuery = buildDcqlQuery(docType, requestedFields)

        val response: InitTransactionResponse = httpClient.post("$verifierBaseUrl/ui/presentations") {
            contentType(ContentType.Application.Json)
            headers { append("Accept", "application/json") }
            setBody(
                InitTransactionRequest(
                    dcqlQuery = dcqlQuery,
                    nonce = UUID.randomUUID().toString(),
                    jarMode = "by_reference",
                    requestUriMethod = "post"
                )
            )
        }.body()

        Logger.d(EudiVerificationManager::class.toString()) {
            "Initialized verification request response: $response"
        }
        return VerificationSession(
            sessionId = response.transactionId,
            qrContent = response.requestUri,
            results = pollResults(response.transactionId)
        )
    }

    override suspend fun cancelVerification(session: VerificationSession) {
        // EUDI verifier endpoint has no explicit cancel — sessions expire via VERIFIER_MAXAGE.
        // Nothing to do client-side beyond stopping the poll, which happens when the
        // collecting coroutine is canceled by the ViewModel.
    }

    private fun pollResults(transactionId: String): Flow<VerificationPollResult> = flow {
        emit(VerificationPollResult.Pending)
        while (true) {
            delay(2000.milliseconds)
            val response = runCatching {
                httpClient.get("$verifierBaseUrl/ui/presentations/$transactionId") {
                    headers { append("Accept", "application/json") }
                }
            }.getOrNull()

            if (response == null) continue

            when (response.status.value) {
                404 -> continue
                in 200..299 -> {
                    val result = runCatching { response.body<GetWalletResponseResult>() }.getOrNull()
                    if (result == null) continue
                    when {
                        result.error != null -> {
                            emit(VerificationPollResult.Failed(result.error))
                            return@flow
                        }
                        result.claims != null -> {
                            emit(VerificationPollResult.Success(result.claims))
                            return@flow
                        }
                        else -> continue
                    }
                }
                else -> continue
            }
        }
    }

    private fun buildDcqlQuery(
        docType: String,
        requestedFields: List<RequestedField>,
    ): DcqlQuery {
        val credentialId = UUID.randomUUID().toString()
        return DcqlQuery(
            credentials = listOf(
                DcqlCredential(
                    id = credentialId,
                    format = "dc+sd-jwt",
                    meta = DcqlMeta(doctypeValue = docType),
                    claims = requestedFields.map { field ->
                        DcqlClaim(path = listOf(docType, field.field.name))
                    }
                )
            ),
            credentialSets = listOf(
                DcqlCredentialSet(
                    options = listOf(listOf(credentialId)),
                    purpose = "Identity verification"
                )
            ),
        )
    }
}

@Serializable
data class InitTransactionRequest(
    @SerialName("dcql_query")
    val dcqlQuery: DcqlQuery,
    val nonce: String,
    @SerialName("jar_mode")
    val jarMode: String,
    @SerialName("request_uri_method")
    val requestUriMethod: String,
)

@Serializable
data class DcqlQuery(
    val credentials: List<DcqlCredential>,
    @SerialName("credential_sets")
    val credentialSets: List<DcqlCredentialSet>,
)

@Serializable
data class DcqlCredential(
    val id: String,
    val format: String,
    val meta: DcqlMeta,
    val claims: List<DcqlClaim>,
)

@Serializable
data class DcqlMeta(
    @SerialName("doctype_value")
    val doctypeValue: String,
)

@Serializable
data class DcqlClaim(val path: List<String>)

@Serializable
data class DcqlCredentialSet(
    val options: List<List<String>>,
    val purpose: String,
)

@Serializable
data class InitTransactionResponse(
    @SerialName("transaction_id")
    val transactionId: String,
    @SerialName("request_uri")
    val requestUri: String,
)

@Serializable
data class GetWalletResponseResult(
    val claims: Map<String, String>? = null,
    val error: String? = null,
)