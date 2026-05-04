package com.dev.usdi_wallet.eudi

import android.net.Uri
import android.util.Base64
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.domain.connection.ConnectionManager
import com.dev.usdi_wallet.domain.credential.Claim
import com.dev.usdi_wallet.domain.credential.ClaimType
import com.dev.usdi_wallet.domain.credential.Credential
import com.dev.usdi_wallet.domain.credential.CredentialManager
import com.dev.usdi_wallet.domain.credential.VerificationRequest
import com.dev.usdi_wallet.domain.credential.VerificationResult
import eu.europa.ec.eudi.iso18013.transfer.TransferEvent
import eu.europa.ec.eudi.iso18013.transfer.response.DisclosedDocument
import eu.europa.ec.eudi.iso18013.transfer.response.DisclosedDocuments
import eu.europa.ec.eudi.iso18013.transfer.response.device.MsoMdocItem
import eu.europa.ec.eudi.wallet.document.Document
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.MsoMdocData
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcData
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import eu.europa.ec.eudi.wallet.document.nameSpacedDataJSONObject
import eu.europa.ec.eudi.wallet.issue.openid4vci.IssueEvent
import eu.europa.ec.eudi.wallet.transfer.openId4vp.SdJwtVcItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.multipaz.crypto.Algorithm

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

    override suspend fun preparePresentationProof(
        credential: Document,
        message: String,
        disclosedClaimLabels: List<String>?
    ) {
        // We can only present IssuedDocuments
        val issuedDocument = credential as? IssuedDocument ?: run {
            Logger.e(EudiCredentialManager::class.toString()) { "Cannot present a non-issued document." }
            return
        }

        sdk.wallet.addTransferEventListener { event ->
            if (event is TransferEvent.RequestReceived) {
                try {
                    val processedRequest = event.processedRequest.getOrThrow()

                    val itemsToDisclose = disclosedClaimLabels?.map { claimLabel ->
                        when (issuedDocument.format) {

                            is MsoMdocFormat -> {
                                // mDocs require a namespace. We dynamically search the parsed JSON
                                // payload of the document to find which namespace this claim belongs to.
                                val jsonObject = issuedDocument.nameSpacedDataJSONObject
                                var targetNamespace = "org.iso.18013.5.1" // Safe fallback

                                jsonObject.keys().forEach { ns ->
                                    if (jsonObject.getJSONObject(ns).has(claimLabel)) {
                                        targetNamespace = ns
                                    }
                                }

                                MsoMdocItem(
                                    namespace = targetNamespace,
                                    elementIdentifier = claimLabel
                                )
                            }

                            is SdJwtVcFormat -> {
                                // SD-JWTs don't use namespaces, they just use JSON paths/keys.
                                SdJwtVcItem(
                                    path = listOf(claimLabel)
                                )
                            }
                        }
                    } ?: emptyList()

                    // Note: Uncomment keyUnlockData when you implement biometrics!
                    // val keyUnlockData = sdk.wallet.getDefaultKeyUnlockData(credential.id)

                    val disclosedDocuments = DisclosedDocuments(
                        DisclosedDocument(
                            documentId = credential.id,
                            disclosedItems = itemsToDisclose,
                            // keyUnlockData = keyUnlockData,
                        )
                    )

                    val response = processedRequest.generateResponse(
                        disclosedDocuments = disclosedDocuments,
                        signatureAlgorithm = Algorithm.ES256,
                    ).getOrThrow()

                    sdk.wallet.sendResponse(response)

                } catch (e: Exception) {
                    Logger.e(EudiCredentialManager::class.toString()) {
                        "Failed to present EUDI proof: ${e.message}"
                    }
                }
            }
        }

        // Start the OpenID4VP transfer
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
            issuer = issued.name,
            subject = null,
            protocol = "OPENID4VC",
            claims = extractEudiClaims(sdkCredential),
            revoked = false
        )
    }

    override suspend fun toSdkCredential(credential: Credential): Document =
        sdk.wallet.getDocumentById(credential.id)!!

    private fun extractEudiClaims(document: IssuedDocument): List<Claim> {
        val claimsList = mutableListOf<Claim>()

        when (val data = document.data) {
            // 1. Handle ISO 18013-5 mDoc Formats
            is MsoMdocData -> {
                // EUDI provides a handy extension to get mDoc data as a JSONObject
                val jsonObject = document.nameSpacedDataJSONObject
                jsonObject.keys().forEach { namespace ->
                    val elements = jsonObject.getJSONObject(namespace)
                    elements.keys().forEach { elementIdentifier ->
                        val value = elements.get(elementIdentifier)
                        claimsList.add(
                            Claim(
                                name = elementIdentifier, // e.g., "first_name"
                                type = mapClaimType(value),
                                value = value
                            )
                        )
                    }
                }
            }

            // 2. Handle SD-JWT Formats
            is SdJwtVcData -> {
                // EUDI stores the raw SD-JWT string here (format: jwt~disclosure1~disclosure2...)
                val parts = data.sdJwtVc.split("~")

                // A. Extract any plaintext claims directly in the JWT payload (if any)
                try {
                    val jwtPayloadBase64 = parts[0].split(".")[1]
                    val payloadJson = String(Base64.decode(jwtPayloadBase64, Base64.URL_SAFE))
                    val payloadObj = JSONObject(payloadJson)

                    payloadObj.keys().forEach { key ->
                        // Filter out standard JWT and SD-JWT protocol keys
                        if (key !in listOf("iss", "iat", "exp", "sub", "_sd", "_sd_alg", "vct", "cnf")) {
                            val value = payloadObj.get(key)
                            claimsList.add(Claim(name = key, type = mapClaimType(value), value = value))
                        }
                    }
                } catch (e: Exception) {
                    Logger.e { "Failed to parse SD-JWT base payload: ${e.message}" }
                }

                // B. Extract the hidden/selectively disclosable claims from the disclosures
                if (parts.size > 1) {
                    for (i in 1 until parts.size) {
                        val disclosure = parts[i]
                        if (disclosure.isNotBlank()) {
                            try {
                                // Disclosures are base64url encoded JSON arrays: [salt, claim_name, claim_value]
                                val decodedDisclosure = String(Base64.decode(disclosure, Base64.URL_SAFE))
                                val disclosureArray = JSONArray(decodedDisclosure)

                                if (disclosureArray.length() == 3) {
                                    val claimName = disclosureArray.getString(1)
                                    val claimValue = disclosureArray.get(2)

                                    claimsList.add(
                                        Claim(
                                            name = claimName,
                                            type = mapClaimType(claimValue),
                                            value = claimValue
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                Logger.e { "Failed to parse SD-JWT disclosure: $disclosure" }
                            }
                        }
                    }
                }
            }
        }

        return claimsList
    }

    private fun mapClaimType(value: Any): ClaimType {
        return when (value) {
            is String -> ClaimType.STRING
            is Number -> ClaimType.NUMBER
            is Boolean -> ClaimType.BOOLEAN
            is ByteArray -> ClaimType.BYTEARRAY // mDocs often store portraits as ByteArrays
            else -> ClaimType.STRING
        }
    }
}