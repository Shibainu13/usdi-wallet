package com.dev.usdi_wallet.hyperledger_identus

import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.domain.credential.Claim
import com.dev.usdi_wallet.domain.credential.ClaimType
import com.dev.usdi_wallet.domain.credential.Predicate
import com.dev.usdi_wallet.domain.verification.RequestedField
import com.dev.usdi_wallet.domain.verification.VerifiableCredentialType
import com.dev.usdi_wallet.domain.verification.VerifiableFieldSchema
import com.dev.usdi_wallet.domain.verification.VerificationManager
import com.dev.usdi_wallet.domain.verification.VerificationPollResult
import com.dev.usdi_wallet.domain.verification.VerificationProtocol
import com.dev.usdi_wallet.domain.verification.VerificationSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

class IdentusAnonVerificationManager(
    private val baseUrl: String,
    private val apiKey: String?,
    private val client: CloudAgentVerifierClient = CloudAgentVerifierClient(),
) : VerificationManager {
    override suspend fun getSupportedCredentialTypes(): List<VerifiableCredentialType> {
        return runCatching {
            client.getCredentialDefinitions(baseUrl, apiKey).map { definition ->
                Logger.d(IdentusAnonVerificationManager::class.toString()) {
                    "Received schema definition: $definition"
                }
                val schema = client.getAnonCredSchemaById(baseUrl, apiKey, definition.schemaId)
                val fields = schema?.attrNames?.map { attrName ->
                    VerifiableFieldSchema(
                        name = attrName,
                        label = attrName.replaceFirstChar { it.uppercase() },
                        type = ClaimType.STRING,
                        supportsPredicate = true,
                    )
                }.orEmpty()

                VerifiableCredentialType(
                    id = definition.id,
                    label = definition.name.ifBlank { definition.guid },
                    protocol = VerificationProtocol.ANONCREDS,
                    fields = fields,
                    metadata = mapOf(
                        "credentialDefinitionId" to definition.guid,
                        "schemaId" to definition.schemaId
                    )
                )
            }
        }.getOrElse { error ->
            Logger.e(IdentusAnonVerificationManager::class.toString()) {
                "Failed to fetch Anoncreds credential definitions: ${error.message}"
            }
            emptyList()
        }
    }

    override suspend fun startVerification(
        credentialType: VerifiableCredentialType,
        requestedFields: List<RequestedField>
    ): VerificationSession {
        val credentialDefinitionId = credentialType.metadata["credentialDefinitionId"]

        val claims = requestedFields
            .filter { it.predicateOperator == null }
            .map { Claim(name = it.field.name, type = it.field.type, pattern = null) }

        val predicates = requestedFields
            .filter { it.predicateOperator != null }
            .mapNotNull { field ->
                val operator = field.predicateOperator ?: return@mapNotNull null
                val value = field.predicateValue?.trim()?.toIntOrNull() ?: return@mapNotNull null
                Predicate(name = field.field.name, operator = operator, value = value)
            }

        val response = client.sendAnonCredProofRequest(
            baseUrl = baseUrl,
            apiKey = apiKey,
            claims = claims,
            predicates = predicates,
            credentialDefinitionId = credentialDefinitionId,
            requestName = "USDI wallet verification",
        )

        val qrContent = response.invitationUrl
            ?: error("Cloud agent did not return an invitation URL")

        Logger.d(IdentusAnonVerificationManager::class.toString()) {
            "QR content: $qrContent"
        }

        return VerificationSession(
            sessionId = response.presentationId ?: UUID.randomUUID().toString(),
            qrContent = qrContent,
            results = pollResults(response.presentationId),
        )
    }

    override suspend fun cancelVerification(session: VerificationSession) {
        // Do nothing
    }

    private fun pollResults(presentationId: String?): Flow<VerificationPollResult> = flow {
        emit(VerificationPollResult.Pending)
        Logger.w(IdentusAnonVerificationManager::class.toString()) {
            "No verification polling result endpoint available yet. Session will remain pending until backend support is added"
        }
    }
}
