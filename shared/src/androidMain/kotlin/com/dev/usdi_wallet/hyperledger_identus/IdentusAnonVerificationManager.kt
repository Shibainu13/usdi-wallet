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
}
