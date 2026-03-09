package com.dev.usdi_wallet.hyperledger_identus

import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.message.Message
import com.dev.usdi_wallet.protocol.ProtocolHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.hyperledger.identus.walletsdk.apollo.utils.Secp256k1KeyPair
import org.hyperledger.identus.walletsdk.domain.models.Curve
import org.hyperledger.identus.walletsdk.domain.models.KeyCurve
import org.hyperledger.identus.walletsdk.domain.models.KeyPurpose
import org.hyperledger.identus.walletsdk.edgeagent.protocols.ProtocolType
import org.hyperledger.identus.walletsdk.edgeagent.protocols.issueCredential.IssueCredential
import org.hyperledger.identus.walletsdk.domain.models.Message as SdkMessage
import org.hyperledger.identus.walletsdk.edgeagent.protocols.issueCredential.OfferCredential

class IdentusJWTProtocolHandler(
    val scope: CoroutineScope,
) : ProtocolHandler {
    private val processedOffer: ArrayList<String> = arrayListOf()
    private val issuedCredentials: ArrayList<String> = arrayListOf()

    override suspend fun handleInbound(message: Message) {
        when (message.type) {
            ProtocolType.DidcommOfferCredential.value -> handleOfferCredential(message)
            ProtocolType.DidcommIssueCredential.value -> handleIssueCredential(message)
        }
    }

    override suspend fun handleOutbound(message: Message) {
        TODO("Not yet implemented")
    }

    fun handleOfferCredential(message: Message) {
        try {
            scope.launch {
                if (!processedOffer.contains(message.id)) {
                    processedOffer.add(message.id)
                    Logger.d(this::class.toString()) { "Received credential offer: ${message.raw}" }
                    val sdk = HyperledgerIdentusSdk.getInstance()

                    val offer = OfferCredential.fromMessage(toSdkMessage(message))
                    val index = sdk.agent.pluto.getPrismLastKeyPathIndex().first() + 1
                    val authenticationKey = Secp256k1KeyPair.generateKeyPair(
                        sdk.agent.seed,
                        KeyCurve(Curve.SECP256K1, index)
                    )

                    val subjectDID = sdk.agent.createNewPrismDID(
                        keys = listOf(Pair(KeyPurpose.AUTHENTICATION, authenticationKey.privateKey))
                    )
                    val request = sdk.agent.prepareRequestCredentialWithIssuer(
                        subjectDID,
                        offer,
                    )
                    sdk.agent.sendMessage(request.makeMessage())
                    Logger.d(this::class.toString()) { "Credential request sent" }
                }
            }
        } catch (e: Exception) {
            Logger.e(this::class.toString()) { "Failed to prepare credential request: ${e.message}" }
        }
    }

    fun handleIssueCredential(message: Message) {
        try {
            scope.launch {
                if (!issuedCredentials.contains(message.id)) {
                    issuedCredentials.add(message.id)
                    Logger.d(this::class.toString()) { "Received issue offer: ${message.raw}" }
                    val sdk = HyperledgerIdentusSdk.getInstance()

                    val issueCredential = IssueCredential.fromMessage(toSdkMessage(message))
                    val credential = sdk.agent.processIssuedCredentialMessage(issueCredential)

                    Logger.d(this::class.toString()) { "Credential received" }
                }
            }
        } catch (e: Exception) {
            Logger.e(this::class.toString()) { "Failed to receive credential request: ${e.message}" }
        }
    }

    fun toSdkMessage(message: Message): SdkMessage {
        return Json.decodeFromString<SdkMessage>(message.raw)
    }
}