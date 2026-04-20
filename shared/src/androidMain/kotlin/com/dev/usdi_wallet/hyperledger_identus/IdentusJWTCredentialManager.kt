package com.dev.usdi_wallet.hyperledger_identus

import android.content.Context
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.db.AppDatabase
import com.dev.usdi_wallet.db.data.MessageReadStatus
import com.dev.usdi_wallet.domain.connection.ConnectionManager
import com.dev.usdi_wallet.domain.credential.Claim
import com.dev.usdi_wallet.domain.credential.ClaimType
import com.dev.usdi_wallet.domain.credential.Credential
import com.dev.usdi_wallet.domain.credential.CredentialManager
import com.dev.usdi_wallet.domain.credential.VerificationRequest
import com.dev.usdi_wallet.domain.credential.VerificationResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

class IdentusJWTCredentialManager(
    scope: CoroutineScope,
    context: Context
) : CredentialManager<SdkCredential, SdkMessage> {
    private val sdk = HyperledgerIdentusSdk.getInstance()
    private val processedMessageIds = mutableSetOf<String>()
    private val revokedCredentials = MutableStateFlow<List<SdkCredential>>(emptyList())
    private val revokedCredentialNotified = MutableStateFlow<List<SdkCredential>>(emptyList())
    private val _proofRequestToProcess = MutableStateFlow<List<SdkMessage>>(emptyList())
    private val _verificationResults = MutableStateFlow<List<VerificationResult>>(emptyList())
    private val db: AppDatabase = AppDatabase.getInstance(context)
    private val initCompleted = CompletableDeferred<Unit>()

    init {
        scope.launch {
            db.messageReadStatusDao().getUnreadMessages().forEach {
                processedMessageIds.add(it)
            }

            Logger.d(IdentusJWTCredentialManager::class.toString()) {
                "Processed message IDs: $processedMessageIds"
            }

            db.pendingProofRequestDao().getAllIds().forEach { id ->
                sdk.pluto.getMessage(id).first()?.let { message ->
                    _proofRequestToProcess.update { it + message }
                }
            }

            initCompleted.complete(Unit)
        }
    }

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
        initCompleted.await()

        if (message.id in processedMessageIds) return

        processedMessageIds.add(message.id)

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

            db.messageReadStatusDao().insertMessage(
                MessageReadStatus(
                    messageId = message.id,
                    isRead = true,
                )
            )
        } catch (e: Exception) {
            Logger.e(IdentusJWTCredentialManager::class.toString()) {
                "Failed to process credential offer: ${e.message}"
            }
        }
    }

    private suspend fun handleIssueCredential(message: SdkMessage) {
        try {
            Logger.d(IdentusJWTCredentialManager::class.toString()) {
                "Received issue offer: $message"
            }
            val issueCredential = IssueCredential.fromMessage(message)
            val credential = sdk.agent.processIssuedCredentialMessage(issueCredential)
            Logger.d(IdentusJWTCredentialManager::class.toString()) {
                "Credential received: $credential"
            }

            db.messageReadStatusDao().insertMessage(
                MessageReadStatus(
                    messageId = message.id,
                    isRead = true,
                )
            )
        } catch (e: Exception) {
            Logger.e(IdentusJWTCredentialManager::class.toString()) {
                "Failed to receive credential: ${e.message}"
            }
        }
    }

    private suspend fun handlePresentationRequest(message: SdkMessage) {
        _proofRequestToProcess.value = _proofRequestToProcess.value.plus(message)
        Logger.d(IdentusJWTCredentialManager::class.toString()) {
            "Presentation request received: $message"
        }

        db.messageReadStatusDao().insertMessage(
            MessageReadStatus(
                messageId = message.id,
                isRead = true,
            )
        )
    }

    private suspend fun handleVerification(message: SdkMessage) {
        Logger.d(IdentusJWTCredentialManager::class.toString()) {
            "Received verification: $message"
        }
        try {
            val isValid = sdk.agent.handlePresentation(message)
            _verificationResults.update { current ->
                current + VerificationResult(message.id, isValid)
            }
            Logger.d(IdentusJWTCredentialManager::class.toString()) {
                "Verification result for $message: $isValid"
            }

            db.messageReadStatusDao().insertMessage(
                MessageReadStatus(
                    messageId = message.id,
                    isRead = true,
                )
            )
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

    override suspend fun getRevokedCredential(): StateFlow<List<SdkCredential>> {
        sdk.agent.observeRevokedCredentials().collect { list ->
            val newRevokedCredentials = list.filter { newCredential ->
                revokedCredentials.value.none { notifiedCredentials ->
                    notifiedCredentials.id == newCredential.id
                }
            }
            if (newRevokedCredentials.isNotEmpty()) {
                revokedCredentialNotified.value.plus(newRevokedCredentials)
                revokedCredentials.value = newRevokedCredentials
            } else {
                revokedCredentials.value = emptyList()
            }
        }
        return revokedCredentials.asStateFlow()
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
                },
                value = it.value,
            ) },
            protocol = DIDCOMM1,
            revoked = sdkCredential.revoked ?: false,
        )

    override suspend fun toSdkCredential(credential: Credential): SdkCredential =
        sdk.agent.getAllCredentials().first().find { it.id == credential.id }!!
}