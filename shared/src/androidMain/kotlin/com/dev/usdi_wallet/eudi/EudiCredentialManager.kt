package com.dev.usdi_wallet.eudi

import android.net.Uri
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.domain.connection.ConnectionManager
import com.dev.usdi_wallet.domain.credential.Credential
import com.dev.usdi_wallet.domain.credential.CredentialManager
import com.dev.usdi_wallet.domain.credential.VerificationRequest
import com.dev.usdi_wallet.domain.credential.VerificationResult
import eu.europa.ec.eudi.wallet.document.Document
import eu.europa.ec.eudi.wallet.document.DocumentExtensions.getDefaultKeyUnlockData
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.issue.openid4vci.IssueEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class EudiCredentialManager(
    scope: CoroutineScope,
) : CredentialManager<Document, String> {
    private val sdk = EudiSdk.getInstance()
    private val _verificationResults = MutableStateFlow<List<VerificationResult>>(emptyList())

    init {
        scope.launch {
            sdk.inboundUriFlow.collect { uri ->
                handleInbound(uri, null)
            }
        }
    }

    override fun getCredentials(): Flow<List<Document>> = flow {
        emit(sdk.wallet.getDocuments { it is IssuedDocument })
    }

    override fun getProofRequestsToProcess(): Flow<List<String>> {
        TODO("Not yet implemented")
    }

    override fun getVerificationResults(): Flow<List<VerificationResult>> {
        TODO("Not yet implemented")
    }

    override suspend fun getCredential(id: String): Credential? {
        TODO("Not yet implemented")
    }

    override suspend fun saveCredential(credential: Credential) {
        TODO("Not yet implemented")
    }

    override suspend fun removeCredential(id: String) {
        TODO("Not yet implemented")
    }

    override suspend fun handleInbound(message: String, connectionManager: ConnectionManager<String>?) {
        if (message.contains("credential_offer=")) {
            handleIssueCredential(message)
        } else if (message.startsWith("openid4vp") || message.startsWith("mdoc-openid4vp")) {
            val docs = sdk.wallet.getDocuments { it is IssuedDocument }
            if (docs.isNotEmpty()) {
                preparePresentationProof(docs.first(), message)
            }
        }
    }

    override suspend fun sendVerificationRequest(
        request: VerificationRequest,
        domain: String,
        challenge: String
    ) {
        TODO("Not yet implemented")
    }

    private fun handleIssueCredential(offerUri: String) {
        val vciManager = sdk.wallet.createOpenId4VciManager()

        vciManager.issueDocumentByOfferUri(offerUri) { event ->
            when (event) {
                is IssueEvent.Started ->
                    Logger.d(EudiCredentialManager::class.toString()) {
                        "Issuance Started"
                    }
                is IssueEvent.DocumentIssued ->
                    Logger.d(EudiCredentialManager::class.toString()) {
                        "Document Issued: ${event.documentId}"
                    }
                is IssueEvent.Failure ->
                    Logger.e(EudiCredentialManager::class.toString()) {
                        "Failed to issue document: ${event.cause}"
                    }
                is IssueEvent.DocumentRequiresUserAuth -> {
//                    val unblockData = event.keysRequireAuth.mapValues { (alias, secureArea) ->
//                        sdk.wallet.getDefaultKeyUnlockData(secureArea, alias)
//                    }
//                    event.resume(unblockData)
                }
                else -> {}
            }
        }
    }

    override suspend fun preparePresentationProof(credential: Document, message: String) {
        sdk.wallet.startRemotePresentation(Uri.parse(message))
    }

    override suspend fun getRevokedCredential(): StateFlow<List<Document>> {
        TODO("Not yet implemented")
    }

    override fun toUiCredential(sdkCredential: Document): Credential {
        val issued = sdkCredential as? IssuedDocument
            ?: throw IllegalArgumentException("Document is not an IssuedDocument")

        return Credential(
            id = issued.id,
            issuer = issued.name ?: "EUDI Issuer",
            subject = null,
            protocol = "OPENID4VC",
            claims = emptyList(), // Requires format-specific extraction later
            revoked = false
        )
    }

    override suspend fun toSdkCredential(credential: Credential): Document =
        sdk.wallet.getDocumentById(credential.id)!!
}