package com.dev.usdi_wallet.hyperledger_identus

import android.app.Application
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.connection.ConnectionManager
import com.dev.usdi_wallet.contact.ContactManager
import com.dev.usdi_wallet.credential.CredentialManager
import com.dev.usdi_wallet.message.Message
import com.dev.usdi_wallet.protocol.Protocol
import kotlinx.coroutines.flow.first
import org.hyperledger.identus.walletsdk.apollo.utils.Secp256k1KeyPair
import org.hyperledger.identus.walletsdk.domain.models.Curve
import org.hyperledger.identus.walletsdk.domain.models.KeyCurve
import org.hyperledger.identus.walletsdk.domain.models.KeyPurpose
import org.hyperledger.identus.walletsdk.domain.models.Message as SdkMessage
import org.hyperledger.identus.walletsdk.edgeagent.DIDCOMM1
import org.hyperledger.identus.walletsdk.edgeagent.protocols.ProtocolType
import org.hyperledger.identus.walletsdk.edgeagent.protocols.issueCredential.IssueCredential
import org.hyperledger.identus.walletsdk.edgeagent.protocols.issueCredential.OfferCredential

class IdentusJWTProtocol(
    override val protocolId: String,
    override val connectionManager: ConnectionManager<SdkMessage>,
    override val contactManager: ContactManager,
    override val credentialManager: CredentialManager,
) : Protocol<SdkMessage>() {
    private val processedOffer: ArrayList<String> = arrayListOf()
    private val issuedCredentials: ArrayList<String> = arrayListOf()

    override suspend fun start() {
        connectionManager.start()
        connectionManager.receiveMessage { msg -> handleInbound(msg) }
    }

    override suspend fun handleInbound(message: SdkMessage) {
        when (message.piuri) {
            ProtocolType.DidcommOfferCredential.value -> handleOfferCredential(message)
            ProtocolType.DidcommIssueCredential.value -> handleIssueCredential(message)
        }
    }

    override suspend fun handleOutbound(message: SdkMessage) {
        TODO("Not yet implemented")
    }

    private suspend fun handleOfferCredential(message: SdkMessage) {
        try {
            if (!processedOffer.contains(message.id)) {
                processedOffer.add(message.id)
                Logger.d(IdentusJWTProtocol::class.toString()) {
                    "Received credential offer: $message"
                }
                val sdk = HyperledgerIdentusSdk.getInstance()

                val offer = OfferCredential.fromMessage(message)
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
                connectionManager.sendMessage(request.makeMessage())
                Logger.d(IdentusJWTProtocol::class.toString()) {
                    "Credential request sent: $request"
                }
            }
        } catch (e: Exception) {
            Logger.e(IdentusJWTProtocol::class.toString()) {
                "Failed to prepare credential request: ${e.message}"
            }
        }
    }

    private suspend fun handleIssueCredential(message: SdkMessage) {
        try {
            if (!issuedCredentials.contains(message.id)) {
                issuedCredentials.add(message.id)
                Logger.d(IdentusJWTProtocol::class.toString()) {
                    "Received issue offer: $message}"
                }
                val sdk = HyperledgerIdentusSdk.getInstance()

                val issueCredential = IssueCredential.fromMessage(message)
                val credential = sdk.agent.processIssuedCredentialMessage(issueCredential)

                Logger.d(IdentusJWTProtocol::class.toString()) {
                    "Credential received: $credential"
                }
            }
        } catch (e: Exception) {
            Logger.e(IdentusJWTProtocol::class.toString()) {
                "Failed to receive credential request: ${e.message}"
            }
        }
    }

    override fun toUiMessage(message: SdkMessage): Message =
        Message(
            id = message.id,
            type = message.piuri,
            from = message.from.toString(),
            to = message.to.toString(),
            raw = message.toJsonString()
        )

    companion object {
        fun getInstance(

            application: Application
        ): IdentusJWTProtocol =
            getInstance(IdentusJWTProtocol::class)
                ?: register(
                    IdentusJWTProtocol(
                        DIDCOMM1,
                        IdentusDIDCommConnectionManager(application),
                        IdentusDIDCommContactManager(),
                        IdentusJWTCredentialManager(),
                    )
                )
    }
}