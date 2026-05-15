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
import eu.europa.ec.eudi.iso18013.transfer.response.DocItem
import eu.europa.ec.eudi.iso18013.transfer.response.device.MsoMdocItem
import eu.europa.ec.eudi.wallet.document.Document
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.issue.openid4vci.Offer
import eu.europa.ec.eudi.wallet.issue.openid4vci.OfferResult
import eu.europa.ec.eudi.wallet.transfer.openId4vp.SdJwtVcItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
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
                        "Received credential from $issuerName, tx = $txCodeSpec: $offeredDocuments"
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

    override fun toUiCredential(sdkCredential: Document): Credential = Credential(
        id = sdkCredential.id,
        issuer = sdkCredential.issuerMetadata.toString(),
        subject = sdkCredential.name,
        protocol = "OPENID4VC",
    )

    override suspend fun toSdkCredential(credential: Credential): Document =
        sdk.wallet.getDocumentById(credential.id)!!
}