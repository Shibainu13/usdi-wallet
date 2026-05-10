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
    override val credentialManager: EudiSdJwtCredentialManager,
) : Protocol<Document, EudiMessage>() {
    override suspend fun startConnection() {
        connectionManager.start()
    }

    override fun toUiMessage(message: EudiMessage): Message =
        Message(
            id = message.hashCode().toString(),
            type = if (message is EudiMessage.CredentialOffer) "Offer" else "Presentation",
            raw = message.toString()
        )

    companion object {
        fun getInstance(application: Application, scope: CoroutineScope): EudiProtocol {
            return getInstance(EudiProtocol::class) ?: run {
                val connectionManager = EudiConnectionManager(application)
                val contactManager = EudiContactManager()
                val credentialManager = EudiSdJwtCredentialManager(scope)

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