package com.dev.usdi_wallet.eudi

import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.domain.connection.ConnectionManager
import com.dev.usdi_wallet.domain.credential.Claim
import com.dev.usdi_wallet.domain.credential.ClaimType
import com.dev.usdi_wallet.domain.credential.Credential
import com.dev.usdi_wallet.domain.credential.CredentialManager
import com.dev.usdi_wallet.domain.credential.VerificationRequest
import com.dev.usdi_wallet.domain.credential.VerificationResult
import eu.europa.ec.eudi.iso18013.transfer.response.DisclosedDocument
import eu.europa.ec.eudi.iso18013.transfer.response.DisclosedDocuments
import eu.europa.ec.eudi.wallet.document.CreateDocumentSettings
import eu.europa.ec.eudi.wallet.document.Document
import eu.europa.ec.eudi.wallet.document.DocumentExtensions.getDefaultCreateDocumentSettings
import eu.europa.ec.eudi.wallet.document.DocumentExtensions.getDefaultKeyUnlockData
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import eu.europa.ec.eudi.wallet.issue.openid4vci.IssueEvent
import eu.europa.ec.eudi.wallet.issue.openid4vci.Offer
import eu.europa.ec.eudi.wallet.issue.openid4vci.OfferResult
import eu.europa.ec.eudi.wallet.transfer.openId4vp.SdJwtVcItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import org.multipaz.crypto.Algorithm

class EudiSdJwtCredentialManager(
    scope: CoroutineScope,
) : CredentialManager<Document, EudiMessage> {

    private val sdk = EudiSdk.getInstance()
    private val _proofRequestToProcess = MutableStateFlow<List<EudiMessage>>(emptyList())
    private val _verificationResults = MutableStateFlow<List<VerificationResult>>(emptyList())

    override suspend fun handleInbound(message: EudiMessage, connectionManager: ConnectionManager<EudiMessage>?) {
        when(message) {
            is EudiMessage.CredentialOffer -> handleIssueCredential(message)
            is EudiMessage.PresentationRequest -> handlePresentationRequest(message)
        }
    }

    private fun handlePresentationRequest(message: EudiMessage) {
        if (message is EudiMessage.PresentationRequest) {
            _proofRequestToProcess.value = _proofRequestToProcess.value.plus(message)
        } else {
            Logger.e(EudiSdJwtCredentialManager::class.toString()) {
                "Expected message of type EudiMessage.PresentationRequest, received $message"
            }
        }
    }

    private fun handleIssueCredential(message: EudiMessage.CredentialOffer) {
        sdk.openId4VciManager.resolveDocumentOffer(message.rawUri) { result  ->
            when (result) {
                is OfferResult.Success -> {
                    val offer: Offer = result.offer
                    val issuerName = offer.issuerMetadata.toString()
                    val offeredDocuments: List<Offer.OfferedDocument> = offer.offeredDocuments
                    val txCodeSpec = offer.txCodeSpec

                    Logger.d(EudiSdJwtCredentialManager::class.toString()) {
                        "Received offer from $issuerName, tx = $txCodeSpec: $offeredDocuments"
                    }

                    sdk.openId4VciManager.issueDocumentByOffer(
                        offer = offer,
                    ) { issueEvent ->
                        when (issueEvent) {
                            is IssueEvent.DocumentIssued -> {
                                Logger.d(EudiSdJwtCredentialManager::class.toString()) {
                                    "Document issued: $issueEvent"
                                }
                                // getDocuments() will now return this document
                            }
                            is IssueEvent.DocumentFailed -> {
                                Logger.e(EudiSdJwtCredentialManager::class.toString()) {
                                    "Document issuance failed: $issueEvent"
                                }
                            }
                            is IssueEvent.Started -> {
                                Logger.d(EudiSdJwtCredentialManager::class.toString()) {
                                    "Issuance started, total: $issueEvent"
                                }
                            }
                            is IssueEvent.DocumentRequiresCreateSettings -> {
                                val isEuPid = when (val format = issueEvent.offeredDocument.documentFormat) {
                                    is MsoMdocFormat -> format.docType == "eu.europa.ec.eudi.pid.1"
                                    is SdJwtVcFormat -> format.vct == "urn:eudi:pid:1"
                                    else -> false
                                }
                                val createDocumentSettings = when {
                                    isEuPid -> sdk.wallet.getDefaultCreateDocumentSettings(
                                        offeredDocument = issueEvent.offeredDocument,
                                        numberOfCredentials = 1,
                                        credentialPolicy = CreateDocumentSettings.CredentialPolicy.RotateUse
                                    )


                                    else -> sdk.wallet.getDefaultCreateDocumentSettings(
                                        offeredDocument = issueEvent.offeredDocument,
                                        numberOfCredentials = 1,
                                        credentialPolicy = CreateDocumentSettings.CredentialPolicy.RotateUse
                                    )
                                }
                                // Resume with settings
                                issueEvent.resume(createDocumentSettings)
                            }
                            is IssueEvent.DocumentRequiresUserAuth -> {
                                // Document requires user authentication to sign
                                val signingAlgorithm = issueEvent.signingAlgorithm
                                val document = issueEvent.document

                                // Create keyUnlockData (e.g., prompt for biometrics)
                                val keyUnlockData = issueEvent.keysRequireAuth.mapValues { (keyAlias, secureArea) ->
                                    getDefaultKeyUnlockData(secureArea, keyAlias)
                                }

                                // Resume after authentication
                                issueEvent.resume(keyUnlockData)

                                // Or cancel the process
                                // issueEvent.cancel("User cancelled authentication")
                            }

                            is IssueEvent.DocumentDeferred -> {
                                // Issuance is deferred (will be issued later)
                                val documentId = issueEvent.documentId
                                val documentName = issueEvent.name
                                val docType = issueEvent.docType
                            }
                            is IssueEvent.Finished -> {
                                Logger.d(EudiSdJwtCredentialManager::class.toString()) {
                                    "Issuance finished"
                                }
                            }
                            is IssueEvent.Failure -> {
                                Logger.d(EudiSdJwtCredentialManager::class.toString()) {
                                    "Issuance failed: $issueEvent"
                                }
                            }
                        }
                    }
                }
                is OfferResult.Failure -> {
                    val error = result.cause
                    Logger.e(EudiSdJwtCredentialManager::class.toString()) {
                        "Failed to handle EUDI issue credential: $error"
                    }
                }
            }
        }
    }

    override suspend fun preparePresentationProof(
        credential: Document,
        message: EudiMessage,
        disclosedClaimLabels: List<String>?
    ) {
        if (message is EudiMessage.PresentationRequest) {
            try {
                val processedRequest = message.processedRequest
                val document = sdk.wallet.getDocumentById(credential.id) as IssuedDocument

                val disclosedDocuments = DisclosedDocuments(
                    DisclosedDocument(
                        documentId = document.id,
                        disclosedItems = disclosedClaimLabels?.map { label ->
                            SdJwtVcItem(
                                path = label.split("."),
                            )
                        } ?: emptyList(),
                    )
                )
                val response = processedRequest.generateResponse(
                    disclosedDocuments,
                    Algorithm.ES256,
                ).getOrThrow()

                sdk.wallet.sendResponse(response)
            } catch (e: Exception) {
                Logger.e(EudiSdJwtCredentialManager::class.toString()) {
                    "Failed to prepare presentation: $e"
                }
            }
        } else {
            Logger.e(EudiSdJwtCredentialManager::class.toString()) {
                "Expected message of type EudiMessage.PresentationRequest, received $message"
            }
        }
    }

    override fun getCredentials(): Flow<List<Document>> = flow {
        emit(sdk.wallet.getDocuments { it is IssuedDocument })
    }

    override fun getProofRequestsToProcess(): Flow<List<EudiMessage>> = _proofRequestToProcess.asStateFlow()
    override fun getVerificationResults(): Flow<List<VerificationResult>> = flow { emit(emptyList()) }
    override suspend fun getCredential(id: String): Credential? { TODO("Not yet implemented") }
    override suspend fun saveCredential(credential: Credential) { TODO("Not yet implemented") }
    override suspend fun removeCredential(id: String) { TODO("Not yet implemented") }
    override suspend fun sendVerificationRequest(request: VerificationRequest, domain: String, challenge: String) { TODO("Not yet implemented") }
    override suspend fun getRevokedCredential(): Flow<List<Document>> = flow { emit(emptyList()) }

    override fun toUiCredential(sdkCredential: Document): Credential {
        val claims = when (sdkCredential) {
            is IssuedDocument -> {
                sdkCredential.data.claims.filter { it.identifier != "picture" }.map { claim ->
                    Claim(
                        name = claim.identifier,
                        type = when (claim.value) {
                            is Boolean -> ClaimType.BOOLEAN
                            is Number -> ClaimType.NUMBER
                            is ByteArray -> ClaimType.BYTEARRAY
                            else -> ClaimType.STRING
                        },
                        value = claim.value.toString()
                    )
                }
            }
            else -> emptyList()
        }

        return Credential(
            id = sdkCredential.id,
            issuer = sdkCredential.issuerMetadata?.issuerDisplay.toString(),
            subject = sdkCredential.name,
            claims = claims,
            protocol = "OPENID4CI"
        )
    }

    override suspend fun toSdkCredential(credential: Credential): Document =
        sdk.wallet.getDocumentById(credential.id)!!
}