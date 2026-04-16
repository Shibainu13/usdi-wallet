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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import android.content.Context
class IdentusJWTCredentialManager (
    private val context: Context
): CredentialManager<SdkCredential, SdkMessage> {
    private val file: File by lazy {

        File(context.filesDir, "credentials.json")
    }
    private val sdk = HyperledgerIdentusSdk.getInstance()
    private val processedOffer: ArrayList<String> = arrayListOf()
    private val issuedCredentials: ArrayList<String> = arrayListOf()
    private val processedProofRequests: ArrayList<String> = arrayListOf()
    private val processedVerificationResults: ArrayList<String> = arrayListOf()
    private val _proofRequestToProcess = MutableStateFlow<List<SdkMessage>>(emptyList())
    private val _verificationResults = MutableStateFlow<List<VerificationResult>>(emptyList())
    private val _localCredentials = MutableStateFlow<List<Credential>>(emptyList())
    public val localCredentials: StateFlow<List<Credential>> = _localCredentials
    init {
        _localCredentials.value = loadAll()
        Logger.d(IdentusJWTCredentialManager::class.toString()) {
            "load value : $_localCredentials"
        }
    }
    override fun getLocalCredentials(): Flow<List<Credential>> {
        return localCredentials
    }
    override fun getCredentials(): Flow<List<SdkCredential>> = sdk.agent.getAllCredentials()

    override fun getProofRequestsToProcess(): Flow<List<SdkMessage>> = _proofRequestToProcess.asStateFlow()

    override fun getVerificationResults(): Flow<List<VerificationResult>> = _verificationResults.asStateFlow()

    override suspend fun getCredential(id: String): Credential? {
        TODO("Not yet implemented")
    }

    override suspend fun saveCredential(credential: Credential) {

        val list = loadAll().toMutableList()

        // replace if exists
        list.removeAll { it.id == credential.id }
        list.add(credential)

        val jsonArray = JSONArray()
        list.forEach { jsonArray.put(toJson(it)) }

        file.writeText(jsonArray.toString())
        // update memory cache
        _localCredentials.value = list
    }
    private fun loadAll(): List<Credential> {
        Logger.d { "File path: ${file.absolutePath}" }
        if (!file.exists()) return emptyList()

        val content = file.readText()
        if (content.isBlank()) return emptyList()

        val jsonArray = JSONArray(content)
        val result = mutableListOf<Credential>()

        for (i in 0 until jsonArray.length()) {
            result.add(fromJson(jsonArray.getJSONObject(i)))
        }
        Logger.d { "resukt: ${result.size}" }
        return result
    }
    private fun toJson(credential: Credential): JSONObject {
        return JSONObject().apply {
            put("id", credential.id)
            put("issuer", credential.issuer)
            put("subject", credential.subject)
            put("protocol", credential.protocol)

            val claimsArray = JSONArray()
            credential.claims.forEach { claimsArray.put(claimToJson(it)) }
            put("claims", claimsArray)
        }
    }
    private fun fromJson(json: JSONObject): Credential {
        val claimsJson = json.getJSONArray("claims")
        val claims = mutableListOf<Claim>()

        for (i in 0 until claimsJson.length()) {
            claims.add(claimFromJson(claimsJson.getJSONObject(i)))
        }

        return Credential(
            id = json.getString("id"),
            issuer = json.getString("issuer"),
            subject = json.optString("subject", null),
            protocol = json.getString("protocol"),
            claims = claims
        )
    }
    private fun claimToJson(claim: Claim): JSONObject {
        return JSONObject().apply {
            put("name", claim.name)
            put("type", claim.type.toString())
            put("pattern", claim.pattern)
            put("value", claim.value)

            claim.enum?.let { put("enum", JSONArray(it)) }
            claim.const?.let { put("const", JSONArray(it)) }
        }
    }
    private fun claimFromJson(json: JSONObject): Claim {
        return Claim(
            name = json.getString("name"),
            type = ClaimType.valueOf(json.getString("type").uppercase()),
            pattern = json.optString("pattern", null),
            value = json.opt("value"),
            enum = json.optJSONArray("enum")?.let { toList(it) },
            const = json.optJSONArray("const")?.let { toList(it) }
        )
    }

    private fun toList(array: JSONArray): List<Any> {
        val list = mutableListOf<Any>()
        for (i in 0 until array.length()) {
            list.add(array.get(i))
        }
        return list
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
                //filter(credential);
                val uiCredential = toUiCredential(credential)

                saveCredential(uiCredential)
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
        Logger.d(IdentusJWTCredentialManager::class.toString()) {
            "Received verification: $message"
        }
        Logger.d(IdentusJWTCredentialManager::class.toString()){
            "this is where receive issue result from server"
        }
        try {
            if (!processedVerificationResults.contains(message.id)) {
                processedVerificationResults.add(message.id)
                val isValid = sdk.agent.handlePresentation(message)
                _verificationResults.update { current ->
                    current + VerificationResult(message.id, isValid)
                }
                Logger.d(IdentusJWTCredentialManager::class.toString()) {
                    "Verification result for $message: $isValid"
                }
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
            claims = sdkCredential.claims.map { entry ->
                val extractedValue = extractValue(entry.value)

                Claim(
                    name = entry.key,
                    type = when (entry.value) {
                        is SdkClaimType.StringValue -> ClaimType.STRING
                        is SdkClaimType.NumberValue -> ClaimType.NUMBER
                        is SdkClaimType.BoolValue -> ClaimType.BOOLEAN
                        is SdkClaimType.DataValue -> ClaimType.BYTEARRAY
                    },
                    value = extractedValue
                ).also {
                    // This block executes after Claim is created
                    Logger.d(IdentusJWTCredentialManager::class.simpleName.toString()) {
                        "Mapped claim [${it.name}] with value: ${it.value}"
                    }
                }
            },
            protocol = DIDCOMM1
        )
    private fun extractValue(value: Any): Any? {
        return when (value) {
            is SdkClaimType.StringValue -> value.value
            is SdkClaimType.NumberValue -> value.value
            is SdkClaimType.BoolValue -> value.value
            is SdkClaimType.DataValue -> value.value // maybe ByteArray
            else -> null
        }
    }

    override suspend fun toSdkCredential(credential: Credential): SdkCredential =
        sdk.agent.getAllCredentials().first().find { it.id == credential.id }!!
    private fun filter(credential: Credential){

    }
}