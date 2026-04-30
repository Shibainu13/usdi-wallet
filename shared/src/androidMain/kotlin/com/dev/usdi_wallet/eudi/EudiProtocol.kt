package com.dev.usdi_wallet.eudi

import android.app.Application
import com.dev.usdi_wallet.domain.message.Message
import com.dev.usdi_wallet.domain.protocol.Protocol
import eu.europa.ec.eudi.wallet.document.Document
import kotlinx.coroutines.CoroutineScope

class EudiProtocol(
    override val protocolId: String,
    override val connectionManager: EudiConnectionManager,
    override val contactManager: EudiContactManager,
    override val credentialManager: EudiCredentialManager,
) : Protocol<Document, String>() {
    override suspend fun startConnection() {
        connectionManager.start()
    }

    override fun toUiMessage(message: String): Message =
        Message(
            id = message.hashCode().toString(),
            type = if (message.contains("credential_offer")) "Offer" else "Presentation",
            raw = message
        )

    companion object {
        fun getInstance(application: Application, scope: CoroutineScope): EudiProtocol {
            return getInstance(EudiProtocol::class) ?: run {
                val connectionManager = EudiConnectionManager(application)
                val contactManager = EudiContactManager()
                val credentialManager = EudiCredentialManager(scope)

                register(
                    EudiProtocol(
                        protocolId = "OPENID4VC",
                        connectionManager = connectionManager,
                        contactManager = contactManager,
                        credentialManager = credentialManager
                    )
                )
            }
        }
    }
}