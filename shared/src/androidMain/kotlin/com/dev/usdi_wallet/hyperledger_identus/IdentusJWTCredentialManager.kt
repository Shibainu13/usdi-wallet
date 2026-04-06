package com.dev.usdi_wallet.hyperledger_identus

import android.opengl.ETC1.isValid
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.connection.ConnectionManager
import com.dev.usdi_wallet.credential.Claim
import com.dev.usdi_wallet.credential.ClaimType
import com.dev.usdi_wallet.credential.Credential
import com.dev.usdi_wallet.credential.CredentialManager
import com.dev.usdi_wallet.credential.VerificationRequest
import com.dev.usdi_wallet.credential.VerificationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import org.hyperledger.identus.walletsdk.apollo.utils.Secp256k1KeyPair
import org.hyperledger.identus.walletsdk.domain.models.ClaimType as SdkClaimType
import org.hyperledger.identus.walletsdk.domain.models.CredentialType
import org.hyperledger.identus.walletsdk.domain.models.Curve
import org.hyperledger.identus.walletsdk.domain.models.DID
import org.hyperledger.identus.walletsdk.domain.models.InputFieldFilter
import org.hyperledger.identus.walletsdk.domain.models.JWTPresentationClaims
import org.hyperledger.identus.walletsdk.domain.models.KeyCurve
import org.hyperledger.identus.walletsdk.domain.models.KeyPurpose
import org.hyperledger.identus.walletsdk.domain.models.ProvableCredential
import org.hyperledger.identus.walletsdk.domain.models.Message as SdkMessage
import org.hyperledger.identus.walletsdk.edgeagent.DIDCOMM1
import org.hyperledger.identus.walletsdk.edgeagent.EdgeAgentError
import org.hyperledger.identus.walletsdk.edgeagent.protocols.ProtocolType
import org.hyperledger.identus.walletsdk.edgeagent.protocols.issueCredential.IssueCredential
import org.hyperledger.identus.walletsdk.edgeagent.protocols.issueCredential.OfferCredential
import org.hyperledger.identus.walletsdk.edgeagent.protocols.proofOfPresentation.RequestPresentation
import org.hyperledger.identus.walletsdk.domain.models.Credential as SdkCredential

class IdentusJWTCredentialManager : CredentialManager<SdkCredential, SdkMessage> {
    private val sdk = HyperledgerIdentusSdk.getInstance()
    private val processedOffer: ArrayList<String> = arrayListOf()
    private val issuedCredentials: ArrayList<String> = arrayListOf()
    private val processedProofRequests: ArrayList<String> = arrayListOf()
    private val _proofRequestToProcess = MutableStateFlow<List<SdkMessage>>(emptyList())
    private val _verificationResults = MutableStateFlow<List<VerificationResult>>(emptyList())

    override fun getCredentials(): Flow<List<SdkCredential>> = sdk.agent.getAllCredentials()

    override fun getProofRequestsToProcess(): Flow<List<SdkMessage>> = _proofRequestToProcess.asStateFlow()

    override fun getVerificationResults(): Flow<List<VerificationResult>> = _verificationResults.asStateFlow()

    override suspend fun getCredential(id: String): Credential? {
        TODO("Not yet implemented")
    }

    override suspend fun saveCredential(credential: Credential) {
        TODO("Not yet implemented")
    }

    override suspend fun removeCredential(id: String) {
        TODO("Not yet implemented")
    }

    override suspend fun handleInbound(
        message: SdkMessage,
        connectionManager: ConnectionManager<SdkMessage>,
    ) {
        when (message.piuri) {
            ProtocolType.DidcommOfferCredential.value
                -> handleOfferCredential(message, connectionManager)
            ProtocolType.DidcommIssueCredential.value
                -> handleIssueCredential(message)
            ProtocolType.DidcommRequestPresentation.value if message.direction == SdkMessage.Direction.RECEIVED
                -> handlePresentationRequest(message)
            ProtocolType.DidcommPresentation.value if message.direction == SdkMessage.Direction.RECEIVED
                -> handleVerification(message)
        }
    }

    private suspend fun handleOfferCredential(
        message: SdkMessage,
        connectionManager: ConnectionManager<SdkMessage>,
    ) {
        try {
            if (!processedOffer.contains(message.id)) {
                processedOffer.add(message.id)
                Logger.d(IdentusJWTCredentialManager::class.toString()) {
                    "Received credential offer: $message"
                }
                val offer = OfferCredential.fromMessage(message)
                val index = sdk.agent.pluto.getPrismLastKeyPathIndex().first() + 1
                val authenticationKey = Secp256k1KeyPair.generateKeyPair(
                    sdk.agent.seed,
                    KeyCurve(Curve.SECP256K1, index)
                )
                val subjectDID = sdk.agent.createNewPrismDID(
                    keys = listOf(Pair(KeyPurpose.AUTHENTICATION, authenticationKey.privateKey))
                )
                val request = sdk.agent.prepareRequestCredentialWithIssuer(subjectDID, offer)
                connectionManager.sendMessage(request.makeMessage())
                Logger.d(IdentusJWTCredentialManager::class.toString()) {
                    "Credential request sent: $request"
                }
            }
        } catch (e: Exception) {
            Logger.e(IdentusJWTCredentialManager::class.toString()) {
                "Failed to process credential offer: ${e.message}"
            }
        }
    }

    private suspend fun handleIssueCredential(message: SdkMessage) {
        try {
            if (!issuedCredentials.contains(message.id)) {
                issuedCredentials.add(message.id)
                Logger.d(IdentusJWTCredentialManager::class.toString()) {
                    "Received issue offer: $message"
                }
                val issueCredential = IssueCredential.fromMessage(message)
                val credential = sdk.agent.processIssuedCredentialMessage(issueCredential)
                Logger.d(IdentusJWTCredentialManager::class.toString()) {
                    "Credential received: $credential"
                }
            }
        } catch (e: Exception) {
            Logger.e(IdentusJWTCredentialManager::class.toString()) {
                "Failed to receive credential: ${e.message}"
            }
        }
    }

    private fun handlePresentationRequest(message: SdkMessage) {
        if (!processedProofRequests.contains(message.id)) {
            processedProofRequests.add(message.id)
            _proofRequestToProcess.value = _proofRequestToProcess.value.plus(message)
            Logger.d(IdentusJWTCredentialManager::class.toString()) {
                "Presentation request received: $message"
            }
        }
    }

    private suspend fun handleVerification(message: SdkMessage) {
        try {
            val isValid = sdk.agent.handlePresentation(message)
            _verificationResults.update { current ->
                current + VerificationResult(message.id, isValid)
            }
            Logger.d(IdentusJWTCredentialManager::class.toString()) {
                "Verification result for $message: $isValid"
            }
        } catch (e: Exception) {
            Logger.e(IdentusJWTCredentialManager::class.toString()) {
                "Failed to verify presentation: ${e.message}"
            }
            _verificationResults.update { current ->
                current + VerificationResult(message.id, isValid = false)
            }
        }
    }

    override suspend fun sendVerificationRequest(
        request: VerificationRequest,
        domain: String,
        challenge: String,
    ) {
        sdk.agent.initiatePresentationRequest(
            type = CredentialType.JWT,
            toDID = DID(request.destination),
            presentationClaims = JWTPresentationClaims(
                claims = request.claims.associate { claim ->
                    claim.name to InputFieldFilter(
                        type = claim.type.toString(),
                        pattern = claim.pattern,
                        enum = claim.enum,
                        const = claim.const,
                        value = claim.value,
                    )
                }
            ),
            domain = domain,
            challenge = challenge,
        )
    }

    override suspend fun preparePresentationProof(credential: SdkCredential, message: SdkMessage) {
        if (credential is ProvableCredential) {
            try {
                val presentation = sdk.agent.preparePresentationForRequestProof(
                    RequestPresentation.fromMessage(message),
                    credential,
                )
                sdk.agent.sendMessage(presentation.makeMessage())
                _proofRequestToProcess.value = _proofRequestToProcess.value.filter { it.id != message.id }
            } catch (e: EdgeAgentError.CredentialNotValidForPresentationRequest) {
                Logger.e(IdentusJWTCredentialManager::class.toString()) {
                    "Error presenting proof: ${e.message}"
                }
            }
        }
    }

    override fun toUiCredential(sdkCredential: SdkCredential): Credential =
        Credential(
            id = sdkCredential.id,
            issuer = sdkCredential.issuer,
            subject = sdkCredential.subject,
            claims = sdkCredential.claims.map { Claim(
                name = it.key,
                type = when(it.value) {
                    is SdkClaimType.StringValue -> ClaimType.STRING
                    is SdkClaimType.NumberValue -> ClaimType.NUMBER
                    is SdkClaimType.BoolValue -> ClaimType.BOOLEAN
                    is SdkClaimType.DataValue -> ClaimType.BYTEARRAY
                }
            ) },
            protocol = DIDCOMM1
        )

    override suspend fun toSdkCredential(credential: Credential): SdkCredential =
        sdk.agent.getAllCredentials().first().find { it.id == credential.id }!!
}