package com.dev.usdi_wallet.hyperledger_identus

import android.content.Context
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.db.AppDatabase
import com.dev.usdi_wallet.db.data.MessageReadStatus
import com.dev.usdi_wallet.db.data.PendingProofRequest
import com.dev.usdi_wallet.domain.connection.ConnectionManager
import com.dev.usdi_wallet.domain.credential.Claim
import com.dev.usdi_wallet.domain.credential.ClaimType
import com.dev.usdi_wallet.domain.credential.Credential
import com.dev.usdi_wallet.domain.credential.CredentialManager
import com.dev.usdi_wallet.domain.credential.PredicateOperator
import com.dev.usdi_wallet.domain.credential.ProofRequestDetails
import com.dev.usdi_wallet.domain.credential.ProofRequestField
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
import kotlinx.serialization.json.Json
import org.hyperledger.identus.walletsdk.apollo.utils.Secp256k1KeyPair
import org.hyperledger.identus.walletsdk.domain.models.ClaimType as SdkClaimType
import org.hyperledger.identus.walletsdk.domain.models.CredentialType
import org.hyperledger.identus.walletsdk.domain.models.Curve
import org.hyperledger.identus.walletsdk.domain.models.DID
import org.hyperledger.identus.walletsdk.domain.models.AnoncredsInputFieldFilter
import org.hyperledger.identus.walletsdk.domain.models.AnoncredsPresentationClaims
import org.hyperledger.identus.walletsdk.domain.models.KeyCurve
import org.hyperledger.identus.walletsdk.domain.models.KeyPurpose
import org.hyperledger.identus.walletsdk.domain.models.RequestedAttributes
import org.hyperledger.identus.walletsdk.domain.models.ProvableCredential
import org.hyperledger.identus.walletsdk.domain.models.Message as SdkMessage
import org.hyperledger.identus.walletsdk.edgeagent.DIDCOMM1
import org.hyperledger.identus.walletsdk.edgeagent.EdgeAgentError
import org.hyperledger.identus.walletsdk.edgeagent.protocols.ProtocolType
import org.hyperledger.identus.walletsdk.edgeagent.protocols.issueCredential.IssueCredential
import org.hyperledger.identus.walletsdk.edgeagent.protocols.issueCredential.OfferCredential
import org.hyperledger.identus.walletsdk.edgeagent.protocols.proofOfPresentation.RequestPresentation
import org.hyperledger.identus.walletsdk.domain.models.Credential as SdkCredential
import org.hyperledger.identus.walletsdk.pollux.models.AnonCredential
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.util.UUID

class IdentusAnonCredentialManager(
    scope: CoroutineScope,
    context: Context
) : CredentialManager<SdkCredential, SdkMessage> {
    private val file: File by lazy {

        File(context.filesDir, "credentials.json")
    }
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
            db.messageReadStatusDao().getReadMessages().forEach {
                processedMessageIds.add(it)
            }

            Logger.d(IdentusAnonCredentialManager::class.toString()) {
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

    override suspend fun getProofRequestDetails(proofRequest: SdkMessage): ProofRequestDetails {
        val request = RequestPresentation.fromMessage(proofRequest)
        val attachmentJson = request.attachments.firstNotNullOf { it.data.getDataAsJsonString() }
        val json = JSONObject(attachmentJson)
        val fields = if (json.has("requested_attributes") || json.has("requested_predicates")) {
            anoncredRequestedFields(json)
        } else {
            presentationExchangeRequestedFields(json)
        }

        return ProofRequestDetails(
            verifier = proofRequest.from.toString(),
            name = json.optString("name").takeIf { it.isNotBlank() },
            requestedFields = fields,
        )
    }

    override suspend fun denyProofRequest(proofRequest: SdkMessage) {
        _proofRequestToProcess.value = _proofRequestToProcess.value.filter { it.id != proofRequest.id }
        db.pendingProofRequestDao().deletePending(proofRequest.id)
        Logger.d(IdentusAnonCredentialManager::class.toString()) {
            "Proof request denied locally: ${proofRequest.id}"
        }
    }

    override suspend fun findMatchingCredentials(proofRequest: SdkMessage): List<SdkCredential> {
        val criteria = try {
            proofRequestCriteria(proofRequest)
        } catch (e: Exception) {
            Logger.e(IdentusAnonCredentialManager::class.toString()) {
                "Invalid proof request ${proofRequest.id}: ${e.message}"
            }
            return emptyList()
        }
        Logger.d(IdentusAnonCredentialManager::class.toString()) {
            "Finding matching credentials for proof request ${proofRequest.id}: $criteria"
        }
        val result= sdk.agent.getAllCredentials().first().filter { credential ->
            val matchesCredentialDefinition =
                credentialMatchesRequestedCredentialDefinition(credential, criteria)

            val containsAllClaims =
                credentialContainsAllRequestedClaims(credential, criteria)

            Logger.d("Checking credential: $credential")
            Logger.d("credentialMatchesRequestedCredentialDefinition = $matchesCredentialDefinition")
            Logger.d("credentialContainsAllRequestedClaims = $containsAllClaims")
            credential is ProvableCredential &&
                credential.revoked != true &&
                    matchesCredentialDefinition &&
                    containsAllClaims
        }

        Logger.d("Credential full list: ${result} ")
        return result
    }

    override suspend fun getCredential(id: String): Credential? {
        return loadAll().firstOrNull { it.id == id }
    }

    override suspend fun saveCredential(credential: Credential) {

        val list = loadAll().toMutableList()

        // replace if exists
        list.removeAll { it.id == credential.id }
        list.add(credential)

        val jsonArray = JSONArray()
        list.forEach { jsonArray.put(toJson(it)) }

        file.writeText(jsonArray.toString())

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
        Logger.d { "result: ${result.size}" }
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
            subject = json.opt("subject")as? String,
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
            pattern = json.opt("pattern")as? String,
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
        val updated = loadAll().filterNot { it.id == id }
        val jsonArray = JSONArray()
        updated.forEach { jsonArray.put(toJson(it)) }
        file.writeText(jsonArray.toString())
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
            Logger.d(IdentusAnonCredentialManager::class.toString()) {
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
            Logger.d(IdentusAnonCredentialManager::class.toString()) {
                "Credential request sent: $request"
            }

            db.messageReadStatusDao().insertMessage(
                MessageReadStatus(
                    messageId = message.id,
                    isRead = true,
                )
            )
        } catch (e: Exception) {
            Logger.e(IdentusAnonCredentialManager::class.toString()) {
                "Failed to process credential offer: ${e.message}"
            }
        }
    }

    private suspend fun handleIssueCredential(message: SdkMessage) {
        try {
            Logger.d(IdentusAnonCredentialManager::class.toString()) {
                "Received issue offer: $message"
            }
            val issueCredential = IssueCredential.fromMessage(message)
            val credential = sdk.agent.processIssuedCredentialMessage(issueCredential)
            Logger.d(IdentusAnonCredentialManager::class.toString()) {
                "Credential received: $credential"
            }

            db.messageReadStatusDao().insertMessage(
                MessageReadStatus(
                    messageId = message.id,
                    isRead = true,
                )
            )
        } catch (e: Exception) {
            Logger.e(IdentusAnonCredentialManager::class.toString()) {
                "Failed to receive credential: ${e.message}"
            }
        }
    }

    private suspend fun handlePresentationRequest(message: SdkMessage) {
        if (_proofRequestToProcess.value.none { it.id == message.id }) {
            _proofRequestToProcess.value = _proofRequestToProcess.value.plus(message)
        }
        Logger.d(IdentusAnonCredentialManager::class.toString()) {
            "Presentation request received: $message"
        }

        db.pendingProofRequestDao().insertPending(
            PendingProofRequest(
                messageId = message.id,
                thid = message.thid ?: message.id,
                createdAt = System.currentTimeMillis(),
            )
        )

        db.messageReadStatusDao().insertMessage(
            MessageReadStatus(
                messageId = message.id,
                isRead = true,
            )
        )
    }

    private suspend fun handleVerification(message: SdkMessage) {
        Logger.d(IdentusAnonCredentialManager::class.toString()) {
            "Received verification: $message"
        }
        Logger.d(IdentusAnonCredentialManager::class.toString()){
            "this is where receive issue result from server"
        }
        try {
            val isValid = sdk.agent.handlePresentation(message)
            _verificationResults.update { current ->
                current + VerificationResult(message.id, isValid)
            }
            Logger.d(IdentusAnonCredentialManager::class.toString()) {
                "Verification result for $message: $isValid"
            }

            db.messageReadStatusDao().insertMessage(
                MessageReadStatus(
                    messageId = message.id,
                    isRead = true,
                )
            )
        } catch (e: Exception) {
            Logger.e(IdentusAnonCredentialManager::class.toString()) {
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
        Logger.d(IdentusAnonCredentialManager::class.toString()) {
            "Sending AnonCred proof request to ${request.destination}; attributes=${request.claims.map { it.name }}, predicates=${request.predicates}"
        }
        sdk.agent.initiatePresentationRequest(
            type = CredentialType.ANONCREDS_PROOF_REQUEST,
            toDID = DID(request.destination),
            presentationClaims = AnoncredsPresentationClaims(
                attributes = request.claims.associate { claim ->
                    claim.name to RequestedAttributes(
                        name = claim.name,
                        names = setOf(claim.name),
                        restrictions = anoncredRestrictions(request),
                        nonRevoked = null,
                    )
                },
                predicates = request.predicates.associate { predicate ->
                    predicate.name to AnoncredsInputFieldFilter(
                        type = ClaimType.NUMBER.toString(),
                        name = predicate.name,
                        gt = predicate.value.takeIf { predicate.operator == PredicateOperator.GREATER_THAN },
                        gte = predicate.value.takeIf { predicate.operator == PredicateOperator.GREATER_THAN_OR_EQUAL },
                        lt = predicate.value.takeIf { predicate.operator == PredicateOperator.LESS_THAN },
                        lte = predicate.value.takeIf { predicate.operator == PredicateOperator.LESS_THAN_OR_EQUAL },
                    )
                },
            ),
        )
    }

    suspend fun generatePresentationOOBInvitation(
        claims: List<Claim>,
        challenge: String,
        domain: String,
    ): String {
        val invitationId = UUID.randomUUID().toString()
        val requestId    = UUID.randomUUID().toString()
        val attachmentId = UUID.randomUUID().toString()
        val defId        = UUID.randomUUID().toString()
        val newPeerDID   = sdk.agent.createNewPeerDID(
            services = emptyArray(),
            updateMediator = true
        )

        // Build input_descriptors from claims
        val inputDescriptors = claims.map { claim ->
            mapOf(
                "id"   to UUID.randomUUID().toString(),
                "name" to claim.name,
                "constraints" to mapOf(
                    "fields" to listOf(
                        mapOf(
                            "path"   to listOf("\$.vc.credentialSubject.${claim.name}", "\$.credentialSubject.${claim.name}"),
                            "id"     to UUID.randomUUID().toString(),
                            "name"   to claim.name,
                            "filter" to mapOf(
                                "type"    to claim.type.toString(),
                                "pattern" to claim.pattern,
                            ).filterValues { it != null },
                        )
                    )
                )
            )
        }

        val invitation = mapOf(
            "id"   to invitationId,
            "type" to "https://didcomm.org/out-of-band/2.0/invitation",
            "from" to sdk.agent.getAllRegisteredPeerDIDs(),
            "body" to mapOf(
                "goal_code" to "present-vp",
                "goal"      to "Request proof presentation",
                "accept"    to listOf("didcomm/v2"),
            ),
            "attachments" to listOf(
                mapOf(
                    "id"         to attachmentId,
                    "media_type" to "application/json",
                    "data" to mapOf(
                        "json" to mapOf(
                            "id"   to requestId,
                            "type" to "https://didcomm.atalaprism.io/present-proof/3.0/request-presentation",
                            "body" to mapOf(
                                "goal_code"    to "Request Proof Presentation",
                                "will_confirm" to false,
                                "proof_types"  to emptyList<Any>(),
                            ),
                            "attachments" to listOf(
                                mapOf(
                                    "id"         to UUID.randomUUID().toString(),
                                    "media_type" to "application/json",
                                    "data" to mapOf(
                                        "json" to mapOf(
                                            "options" to mapOf(
                                                "challenge" to challenge,
                                                "domain"    to domain,
                                            ),
                                            "presentation_definition" to mapOf(
                                                "id"                to defId,
                                                "input_descriptors" to inputDescriptors,
                                                "format" to mapOf(
                                                    "jwt" to mapOf("alg" to listOf("ES256K"))
                                                ),
                                            ),
                                        )
                                    ),
                                    "format" to "prism/jwt",
                                )
                            ),
                            "thid" to invitationId,
                            "from" to newPeerDID,
                        )
                    ),
                )
            ),
            "created_time" to System.currentTimeMillis() / 1000,
            "expires_time" to (System.currentTimeMillis() / 1000) + 300,
        )

        val json    = Json.encodeToString(invitation)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
        return "https://domain.com/path?_oob=$encoded"
    }

    override suspend fun preparePresentationProof(credential: SdkCredential, message: SdkMessage) {
        Logger.d {"message prepare "+ message.toString() }
        if (credential is ProvableCredential) {
            try {
                Logger.d(IdentusAnonCredentialManager::class.toString()) {
                    "Creating proof presentation for request ${message.id} with credential ${credential.id}"
                }
                Logger.d("Step1 debug");
                val presentation = sdk.agent.preparePresentationForRequestProof(
                    RequestPresentation.fromMessage(message),
                    credential,
                )
                Logger.d("Step2 debug");
                val outMessage = presentation.makeMessage()
                Logger.d("Step3 debug");
                Logger.d {
                    """
                     Sending proof presentation:
                     request.id=${message.id}
                     request.thid=${message.thid}
                     presentation.id=${outMessage.id}
                     presentation.thid=${outMessage.thid}
                     presentation.from=${outMessage.from}
                     presentation.to=${outMessage.to}
                     presentation.piuri=${outMessage.piuri}
                     """.trimIndent()
                }
                Logger.d("Message sent to server: ${outMessage}")
                val response = sdk.agent.sendMessage(outMessage)

                Logger.d {
                    "sendMessage response=$response"
                }
                _proofRequestToProcess.value = _proofRequestToProcess.value.filter { it.id != message.id }
                db.pendingProofRequestDao().deletePending(message.id)
                Logger.d(IdentusAnonCredentialManager::class.toString()) {
                    "Proof presentation sent for request ${message.id}"
                }
            } catch (e: EdgeAgentError.CredentialNotValidForPresentationRequest) {
                Logger.e(IdentusAnonCredentialManager::class.toString()) {
                    "Error presenting proof: ${e.message}"
                }
            } catch (e: Exception) {
                Logger.e(IdentusAnonCredentialManager::class.toString()) {
                    "Failed to send proof presentation: ${e.message}"
                }
            }
        } else {
            Logger.e(IdentusAnonCredentialManager::class.toString()) {
                "Credential ${credential.id} cannot create presentations"
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

    override fun toUiCredential(sdkCredential: SdkCredential): Credential {
        val claims = extractClaimsFromAnonCredential(sdkCredential)

        claims.forEach { claim ->
            Logger.d(IdentusAnonCredentialManager::class.simpleName.toString()) {
                "Mapped claim [${claim.name}] with value: ${claim.value}"
            }
        }

        return Credential(
            id = sdkCredential.id,
            issuer = sdkCredential.issuer,
            subject = sdkCredential.subject,
            claims = claims,
            protocol = DIDCOMM1,
            revoked = sdkCredential.revoked ?: false,
        )
    }

    private fun extractClaimsFromAnonCredential(sdkCredential: SdkCredential): List<Claim> {
        val fallbackClaims = sdkCredential.claims.map { entry ->
            claimFromRawValue(entry.key, extractValue(entry.value))
        }

        val rawClaims = extractClaimsFromAnonCredentialJson(sdkCredential.id)
            ?: extractClaimsFromAnonCredentialValues(sdkCredential as? AnonCredential)
            ?: return fallbackClaims

        val rawClaimsByName = rawClaims.associateBy { it.name }
        val fallbackNames = fallbackClaims.map { it.name }.toSet()
        val mergedClaims = fallbackClaims.map { fallbackClaim ->
            rawClaimsByName[fallbackClaim.name] ?: fallbackClaim
        }
        val extraRawClaims = rawClaims.filter { it.name !in fallbackNames }

        return mergedClaims + extraRawClaims
    }

    private fun extractClaimsFromAnonCredentialJson(json: String): List<Claim>? {
        return runCatching {
            findAnonCredentialValues(JSONObject(json))
        }.getOrNull()
            ?.let { claimsFromAnonCredentialValues(it) }
            ?.takeIf { it.isNotEmpty() }
            ?: extractClaimsFromAnonCredentialText(json)
    }

    private fun extractClaimsFromAnonCredentialText(text: String): List<Claim>? {
        val values = valuesObjectText(text)?.let { valuesText ->
            runCatching { JSONObject(valuesText) }.getOrNull()
                ?: runCatching { JSONObject(valuesText.replace("\\\"", "\"")) }.getOrNull()
        }

        return values
            ?.let { claimsFromAnonCredentialValues(it) }
            ?.takeIf { it.isNotEmpty() }
    }

    private fun valuesObjectText(text: String): String? {
        val valuesKeyIndex = text.indexOf("\"values\"").takeIf { it >= 0 }
            ?: text.indexOf("values").takeIf { it >= 0 }
            ?: return null
        val valuesStart = text.indexOf('{', valuesKeyIndex).takeIf { it >= 0 } ?: return null
        var depth = 0
        var inString = false
        var isEscaped = false

        for (index in valuesStart until text.length) {
            val char = text[index]
            when {
                isEscaped -> isEscaped = false
                char == '\\' && inString -> isEscaped = true
                char == '"' -> inString = !inString
                !inString && char == '{' -> depth++
                !inString && char == '}' -> {
                    depth--
                    if (depth == 0) return text.substring(valuesStart, index + 1)
                }
            }
        }

        return null
    }

    private fun extractClaimsFromAnonCredentialValues(credential: AnonCredential?): List<Claim>? {
        return credential?.values
            ?.map { (name, attribute) -> claimFromRawValue(name, attribute.raw) }
            ?.takeIf { it.isNotEmpty() }
    }

    private fun claimsFromAnonCredentialValues(values: JSONObject): List<Claim> {
        val claims = mutableListOf<Claim>()
        val keys = values.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            claims.add(claimFromRawValue(key, extractRawValue(values.opt(key))))
        }

        return claims
    }

    private fun findAnonCredentialValues(json: JSONObject): JSONObject? {
        json.optJSONObject("values")?.let { values ->
            if (valuesLooksLikeAnonCredentialAttributes(values)) return values
        }

        val keys = json.keys()
        while (keys.hasNext()) {
            val child = json.opt(keys.next())
            when (child) {
                is JSONObject -> findAnonCredentialValues(child)?.let { return it }
                is JSONArray -> {
                    for (index in 0 until child.length()) {
                        (child.opt(index) as? JSONObject)?.let { item ->
                            findAnonCredentialValues(item)?.let { return it }
                        }
                    }
                }
            }
        }

        return null
    }

    private fun valuesLooksLikeAnonCredentialAttributes(values: JSONObject): Boolean {
        val keys = values.keys()
        while (keys.hasNext()) {
            val attribute = values.opt(keys.next())
            if (attribute is JSONObject && attribute.has("raw")) return true
        }

        return false
    }

    private fun extractRawValue(value: Any?): Any? {
        return when (value) {
            is JSONObject -> value.opt("raw").takeUnless { it == JSONObject.NULL }
                ?: value.opt("value").takeUnless { it == JSONObject.NULL }
                ?: value.toString()
            JSONObject.NULL -> null
            else -> value
        }
    }

    private fun claimFromRawValue(name: String, value: Any?): Claim {
        return Claim(
            name = name,
            type = when (value) {
                is Number -> ClaimType.NUMBER
                is Boolean -> ClaimType.BOOLEAN
                null -> ClaimType.NULL
                else -> ClaimType.STRING
            },
            value = value,
        )
    }
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

    private fun anoncredRestrictions(request: VerificationRequest): Map<String, String> {
        return buildMap {
            request.schema?.let { put("schema_id", it) }
            request.issuer?.let { put("issuer_did", it) }
        }
    }

    private data class ProofRequestCriteria(
        val attributes: Set<String>,
        val predicates: List<RequestedPredicate>,
        val credentialDefinitionIds: Set<String>,
    )

    private data class RequestedPredicate(
        val name: String,
        val operator: String,
        val value: Long,
    )

    private fun proofRequestCriteria(message: SdkMessage): ProofRequestCriteria {
        val request = RequestPresentation.fromMessage(message)
        val attachmentJson = request.attachments.firstNotNullOf { it.data.getDataAsJsonString() }
        val json = JSONObject(attachmentJson)

        return if (json.has("requested_attributes") || json.has("requested_predicates")) {
            ProofRequestCriteria(
                attributes = anoncredRequestedAttributes(json.optJSONObject("requested_attributes")),
                predicates = anoncredRequestedPredicates(json.optJSONObject("requested_predicates")),
                credentialDefinitionIds = anoncredRequestedCredentialDefinitionIds(json),
            )
        } else {
            ProofRequestCriteria(
                attributes = presentationExchangeRequestedAttributes(json),
                predicates = emptyList(),
                credentialDefinitionIds = emptySet(),
            )
        }
    }

    private fun credentialMatchesRequestedCredentialDefinition(
        credential: SdkCredential,
        criteria: ProofRequestCriteria,
    ): Boolean {
        if (criteria.credentialDefinitionIds.isEmpty()) return true
        val credentialDefinitionId = credentialDefinitionId(credential) ?: return false
        Logger.d("credentialDefinitionId $credentialDefinitionId");
        Logger.d("credentialDefinitionIds from proof ${criteria.credentialDefinitionIds}");


        val result =normalizeCredentialDefinitionId(credentialDefinitionId) ;
        if (result in criteria.credentialDefinitionIds) return true;
        return false;
    }

    private fun credentialDefinitionId(credential: SdkCredential): String? {
        Logger.d("Full credential: $credential");
        if (credential is AnonCredential) {
            return credential.credentialDefinitionID
        }
        val properties = credential.properties
        return properties["credentialDefinitionID"] as? String
    }

    private fun credentialContainsAllRequestedClaims(
        credential: SdkCredential,
        criteria: ProofRequestCriteria,
    ): Boolean {
        val claims = toUiCredential(credential).claims
        val claimNames = claims.mapTo(mutableSetOf()) { it.name }
        val predicateNames = criteria.predicates.mapTo(mutableSetOf()) { it.name }
        Logger.d("Map request claim");


        val claimNamesHasAllPredicates=claimNames.containsAll(predicateNames);
        val claimNamesHasAllAttributes=claimNames.containsAll(criteria.attributes);
        val credentialSatifiesRequestedFromPredicate = credentialSatisfiesRequestedPredicates(claims, criteria.predicates);
        Logger.d("claimNamesHasAllPredicates $claimNamesHasAllPredicates");
        Logger.d("claimNamesHasAllAttributes $claimNamesHasAllAttributes");
        Logger.d("credentialSatifiesRequestedFromPredicates $credentialSatifiesRequestedFromPredicate");


        return  claimNamesHasAllPredicates&&
                claimNamesHasAllAttributes &&
                credentialSatifiesRequestedFromPredicate
    }

    private fun anoncredRequestedAttributes(requestedAttributes: JSONObject?): Set<String> {
        if (requestedAttributes == null) return emptySet()

        val result = mutableSetOf<String>()
        val keys = requestedAttributes.keys()
        while (keys.hasNext()) {
            val attribute = requestedAttributes.optJSONObject(keys.next()) ?: continue
            attribute.optString("name").takeIf { it.isNotBlank() }?.let { result.add(it) }
            attribute.optJSONArray("names")?.let { names ->
                for (index in 0 until names.length()) {
                    names.optString(index).takeIf { it.isNotBlank() }?.let { result.add(it) }
                }
            }
        }
        return result
    }

    private fun credentialSatisfiesRequestedPredicates(
        claims: List<Claim>,
        predicates: List<RequestedPredicate>,
    ): Boolean {
        if (predicates.isEmpty()) return true
        Logger.d("List predicate: ${predicates}");
        val claimsByName = claims.associateBy { it.name }
        return predicates.all { predicate ->
            val claimValue = claimsByName[predicate.name]?.value?.toPredicateLong() ?: return@all false
            when (predicate.operator) {
                ">" -> claimValue > predicate.value
                ">=" -> claimValue >= predicate.value
                "<" -> claimValue < predicate.value
                "<=" -> claimValue <= predicate.value
                else -> false
            }
        }
    }

    private fun Any.toPredicateLong(): Long? {
        return when (this) {
            is Number -> toLong()
            is String -> trim().toLongOrNull()
            else -> null
        }
    }

    private fun anoncredRequestedPredicates(requestedPredicates: JSONObject?): List<RequestedPredicate> {
        if (requestedPredicates == null) return emptyList()

        val result = mutableListOf<RequestedPredicate>()
        val keys = requestedPredicates.keys()
        while (keys.hasNext()) {
            val predicate = requestedPredicates.optJSONObject(keys.next()) ?: continue
            val name = predicate.optString("name").takeIf { it.isNotBlank() } ?: continue
            val operator = predicate.optString("p_type").takeIf { it.isNotBlank() } ?: continue
            if (!predicate.has("p_value")) continue
            val value = predicate.optLong("p_value")
            result.add(RequestedPredicate(name, operator, value))
        }
        return result
    }

    private fun anoncredRequestedFields(json: JSONObject): List<ProofRequestField> {
        val result = mutableListOf<ProofRequestField>()
        val requestedAttributes = json.optJSONObject("requested_attributes")
        requestedAttributes?.keys()?.let { keys ->
            while (keys.hasNext()) {
                val attribute = requestedAttributes.optJSONObject(keys.next()) ?: continue
                val names = mutableListOf<String>()
                attribute.optString("name").takeIf { it.isNotBlank() }?.let { names.add(it) }
                attribute.optJSONArray("names")?.let { array ->
                    for (index in 0 until array.length()) {
                        array.optString(index).takeIf { it.isNotBlank() }?.let { names.add(it) }
                    }
                }
                names.distinct().forEach { name ->
                    result.add(ProofRequestField(name = name, requirement = "Reveal value"))
                }
            }
        }

        val requestedPredicates = json.optJSONObject("requested_predicates")
        requestedPredicates?.keys()?.let { keys ->
            while (keys.hasNext()) {
                val predicate = requestedPredicates.optJSONObject(keys.next()) ?: continue
                val name = predicate.optString("name").takeIf { it.isNotBlank() } ?: continue
                val operator = predicate.optString("p_type").takeIf { it.isNotBlank() } ?: continue
                if (!predicate.has("p_value")) continue
                result.add(
                    ProofRequestField(
                        name = name,
                        requirement = "$operator ${predicate.optLong("p_value")}",
                    )
                )
            }
        }

        return result
    }

    private fun anoncredRequestedCredentialDefinitionIds(json: JSONObject): Set<String> {
        val result = mutableSetOf<String>()
        collectCredentialDefinitionIds(json.optJSONObject("requested_attributes"), result)
        collectCredentialDefinitionIds(json.optJSONObject("requested_predicates"), result)
        return result
    }

    private fun collectCredentialDefinitionIds(requestedItems: JSONObject?, result: MutableSet<String>) {
        if (requestedItems == null) return

        val keys = requestedItems.keys()
        while (keys.hasNext()) {
            val requestedItem = requestedItems.optJSONObject(keys.next()) ?: continue
            requestedItem.optJSONArray("restrictions")?.let { restrictions ->
                for (index in 0 until restrictions.length()) {
                    val restriction = restrictions.optJSONObject(index) ?: continue
                    restriction.optString("cred_def_id")
                        .takeIf { it.isNotBlank() }
                        ?.let(::normalizeCredentialDefinitionId)
                        ?.let { result.add(it) }
                }
            }
        }
    }

    private fun normalizeCredentialDefinitionId(value: String): String? {
        val trimmed = value.trim().trimEnd('/')
        if (trimmed.isBlank()) return null

        val definitionRegistryMarker = "/definitions/"
        if (definitionRegistryMarker in trimmed) {
            return trimmed.substringAfter(definitionRegistryMarker)
                .substringBefore('/')
                .takeIf { it.isNotBlank() }
        }

        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed.substringBefore('?')
                .substringBefore('#')
                .trimEnd('/')
                .substringAfterLast('/')
                .takeIf { it.isNotBlank() }
        } else {
            trimmed
        }
    }

    private fun presentationExchangeRequestedAttributes(json: JSONObject): Set<String> {
        val fields = json
            .optJSONObject("presentation_definition")
            ?.optJSONArray("input_descriptors")
            ?: return emptySet()

        val result = mutableSetOf<String>()
        for (descriptorIndex in 0 until fields.length()) {
            val constraintFields = fields
                .optJSONObject(descriptorIndex)
                ?.optJSONObject("constraints")
                ?.optJSONArray("fields")
                ?: continue

            for (fieldIndex in 0 until constraintFields.length()) {
                val field = constraintFields.optJSONObject(fieldIndex) ?: continue
                field.optString("name").takeIf { it.isNotBlank() }?.let { result.add(it) }
                field.optJSONArray("path")?.let { paths ->
                    for (pathIndex in 0 until paths.length()) {
                        paths.optString(pathIndex)
                            .substringAfterLast('.', "")
                            .takeIf { it.isNotBlank() }
                            ?.let { result.add(it) }
                    }
                }
            }
        }
        return result
    }

    private fun presentationExchangeRequestedFields(json: JSONObject): List<ProofRequestField> {
        val descriptors = json
            .optJSONObject("presentation_definition")
            ?.optJSONArray("input_descriptors")
            ?: return emptyList()

        val result = mutableListOf<ProofRequestField>()
        for (descriptorIndex in 0 until descriptors.length()) {
            val constraintFields = descriptors
                .optJSONObject(descriptorIndex)
                ?.optJSONObject("constraints")
                ?.optJSONArray("fields")
                ?: continue

            for (fieldIndex in 0 until constraintFields.length()) {
                val field = constraintFields.optJSONObject(fieldIndex) ?: continue
                val name = field.optString("name").takeIf { it.isNotBlank() }
                    ?: field.optJSONArray("path")
                        ?.optString(0)
                        ?.substringAfterLast('.', "")
                        ?.takeIf { it.isNotBlank() }
                    ?: continue
                val filter = field.optJSONObject("filter")
                result.add(
                    ProofRequestField(
                        name = name,
                        requirement = filter?.let(::formatPresentationExchangeFilter),
                    )
                )
            }
        }
        return result
    }

    private fun formatPresentationExchangeFilter(filter: JSONObject): String? {
        return listOfNotNull(
            filter.optString("type").takeIf { it.isNotBlank() },
            filter.optString("pattern").takeIf { it.isNotBlank() }?.let { "pattern: $it" },
            filter.optString("const").takeIf { it.isNotBlank() }?.let { "const: $it" },
        ).joinToString(", ").takeIf { it.isNotBlank() }
    }
}
