package com.dev.usdi_wallet.hyperledger_identus

import android.app.Application
import androidx.lifecycle.MutableLiveData
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.domain.backup.WalletBackupManager
import com.dev.usdi_wallet.domain.connection.ConnectionManager
import com.dev.usdi_wallet.domain.contact.ContactManager
import com.dev.usdi_wallet.domain.credential.CredentialManager
import com.dev.usdi_wallet.domain.message.Message
import com.dev.usdi_wallet.domain.protocol.Protocol
import com.dev.usdi_wallet.domain.verification.VerificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.hyperledger.identus.walletsdk.domain.models.Credential as SdkCredential
import org.hyperledger.identus.walletsdk.domain.models.Message as SdkMessage
import org.hyperledger.identus.walletsdk.edgeagent.DIDCOMM1

class IdentusAnonProtocol(
    private val scope: CoroutineScope,
    override val protocolId: String,
    override val connectionManager: ConnectionManager<SdkMessage>,
    override val contactManager: ContactManager,
    override val credentialManager: CredentialManager<SdkCredential, SdkMessage>,
    override val verificationManager: VerificationManager,
    override val walletBackupManager: WalletBackupManager?
) : Protocol<SdkCredential, SdkMessage>() {
    private val messages = MutableLiveData<List<SdkMessage>>()
    private val sdk = HyperledgerIdentusSdk.getInstance()

    override suspend fun startConnection() {
        runCatching { connectionManager.start() }
            .onFailure { error ->
                Logger.w(IdentusAnonProtocol::class.toString()) {
                    "DIDComm startup failed; continuing without mediator: ${error.message}"
                }
                return
            }

        runCatching {
            connectionManager.receiveMessage { msg ->
                messages.value = messages.value?.plus(msg) ?: emptyList()
                credentialManager.handleInbound(
                    msg,
                    connectionManager
                )
            }
        }.onFailure { error ->
            Logger.w(IdentusAnonProtocol::class.toString()) {
                "DIDComm message receiving stopped: ${error.message}"
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

    override fun onActivityStart() {
        sdk.resumeAgent()
    }

    override fun onActivityStop() {
        scope.launch {
            sdk.pauseAgent()
        }
    }

    companion object {
        private var _instance: IdentusAnonProtocol? = null

        fun getInstance(application: Application, scope: CoroutineScope): IdentusAnonProtocol {
            if (_instance != null) { return _instance!! }

            val connectionManager = IdentusDIDCommConnectionManager(application)
            val credentialManager = IdentusAnonCredentialManager(scope, application)
            val verificationManager = IdentusAnonVerificationManager(
                baseUrl = "http://13.90.44.25:8085",
                apiKey = null,
            )
            val contactManager = IdentusDIDCommContactManager(
                onCredentialOffer = { message ->
                    credentialManager.handleInbound(message, connectionManager)
                },
                onPresentationRequest = { message ->
                    credentialManager.enqueuePresentationRequest(message)
                },
            )
            val walletBackupManager = IdentusBackupManager()

            return register(
                IdentusAnonProtocol(
                    scope,
                    DIDCOMM1,
                    connectionManager,
                    contactManager,
                    credentialManager,
                    verificationManager,
                    walletBackupManager,
                )
            ).also { _instance = it }

        }
        fun getInstance(): IdentusAnonProtocol {
            return _instance ?: error("IdentusAnoncredsProtocol not initialized")
        }
    }
}
