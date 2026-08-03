package com.dev.usdi_wallet.hyperledger_identus

import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.domain.credential.ClaimType
import com.dev.usdi_wallet.domain.verification.RequestedField
import com.dev.usdi_wallet.domain.verification.VerifiableCredentialType
import com.dev.usdi_wallet.domain.verification.VerifiableFieldSchema
import com.dev.usdi_wallet.domain.verification.VerificationManager
import com.dev.usdi_wallet.domain.verification.VerificationProtocol
import com.dev.usdi_wallet.domain.verification.VerificationSession

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
                    val suffix = attrName.knownTypeSuffix()
                    VerifiableFieldSchema(
                        name = attrName,
                        label = attrName.displayLabel(),
                        type = when (suffix) {
                            "num" -> ClaimType.NUMBER
                            "bool" -> ClaimType.BOOLEAN
                            else -> ClaimType.STRING
                        },
                        supportsPredicate = suffix == null || suffix == "num" || suffix == "date",
                    )
                }.orEmpty()

                VerifiableCredentialType(
                    id = definition.id,
                    label = definition.name.ifBlank { definition.guid },
                    protocol = VerificationProtocol.ANONCREDS,
                    fields = fields,
                    metadata = mapOf(
                        "credentialDefinitionId" to definition.guid,
                        "credentialDefinitionUrl" to definition.credentialDefinitionUrl(baseUrl),
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

    @Suppress("UNUSED_PARAMETER")
    override suspend fun startVerification(
        credentialType: VerifiableCredentialType,
        requestedFields: List<RequestedField>
    ): VerificationSession {
        throw UnsupportedOperationException(
            "AnonCreds QR proof invitations were removed. Use Bluetooth local proof."
        )
    }

    override suspend fun cancelVerification(session: VerificationSession) {
        // Do nothing
    }

    private fun CloudAgentCredentialDefinition.credentialDefinitionUrl(baseUrl: String): String {
        val trimmedId = id.normalizedCredentialDefinitionReference()
        val trimmedBaseUrl = baseUrl.trim().trimEnd('/')
        return when {
            trimmedId.startsWith("http://") || trimmedId.startsWith("https://") -> trimmedId
            trimmedId.startsWith("/credential-definition-registry/") -> "$trimmedBaseUrl$trimmedId"
            trimmedId.startsWith("credential-definition-registry/") -> "$trimmedBaseUrl/$trimmedId"
            else -> "$trimmedBaseUrl/credential-definition-registry/definitions/$guid/definition"
        }.withCredentialDefinitionResourceSuffix()
    }

    private fun String.knownTypeSuffix(): String? {
        val suffix = substringAfterLast("_", missingDelimiterValue = "").lowercase()
        return suffix.takeIf { it in KNOWN_TYPE_SUFFIXES }
    }

    private fun String.displayLabel(): String {
        val withoutSuffix = if (knownTypeSuffix() != null) substringBeforeLast("_") else this
        return withoutSuffix
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .replace(Regex("\\s+"), " ")
            .replaceFirstChar { it.uppercase() }
    }

    private companion object {
        val KNOWN_TYPE_SUFFIXES = setOf("str", "num", "bool", "date")
    }
}
