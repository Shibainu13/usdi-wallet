package com.dev.usdi_wallet.eudi

import android.app.Application
import com.dev.usdi_wallet.domain.auth.AndroidWalletAuthManager
import com.dev.usdi_wallet.domain.message.Message
import com.dev.usdi_wallet.domain.protocol.Protocol
import eu.europa.ec.eudi.wallet.document.Document
import kotlinx.coroutines.CoroutineScope

class EudiProtocol(
    override val protocolId: String,
    override val connectionManager: EudiConnectionManager,
    override val contactManager: EudiContactManager,
    override val credentialManager: EudiSdJwtCredentialManager,
    override val verificationManager: EudiVerificationManager,
    override val walletAuthManager: AndroidWalletAuthManager,
) : Protocol<Document, EudiMessage>() {
    override suspend fun startConnection() {
        connectionManager.start()
        connectionManager.receiveMessage { msg ->
            credentialManager.handleInbound(msg, connectionManager)
        }
    }

    override fun toUiMessage(message: EudiMessage): Message =
        Message(
            id = message.hashCode().toString(),
            type = if (message is EudiMessage.CredentialOffer) "Offer" else "Presentation",
            raw = message.toString()
        )

    companion object {
        private var _instance: EudiProtocol? = null

        fun getInstance(application: Application, scope: CoroutineScope): EudiProtocol {
            if (_instance != null) { return _instance!! }
            val walletAuthManager = AndroidWalletAuthManager.getInstance()
            val connectionManager = EudiConnectionManager(application)
            val contactManager = EudiContactManager()
            val credentialManager = EudiSdJwtCredentialManager(scope, walletAuthManager)
            val verificationManager = EudiVerificationManager(
                "https://usdi-wallet.duckdns.org"
            )

            return register(
                EudiProtocol(
                    protocolId = "OPENID4VC",
                    connectionManager = connectionManager,
                    contactManager = contactManager,
                    credentialManager = credentialManager,
                    verificationManager = verificationManager,
                    walletAuthManager = walletAuthManager,
                    )
                ).also { _instance = it }
        }

        fun getInstance(): EudiProtocol {
            return _instance ?: error("EudiProtocol has not been initialized")
        }
    }
}