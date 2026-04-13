package com.dev.usdi_wallet.hyperledger_identus

import android.app.Application
import androidx.lifecycle.MutableLiveData
import com.dev.usdi_wallet.domain.connection.ConnectionManager
import com.dev.usdi_wallet.domain.contact.ContactManager
import com.dev.usdi_wallet.domain.credential.CredentialManager
import com.dev.usdi_wallet.domain.message.Message
import com.dev.usdi_wallet.domain.protocol.Protocol
import org.hyperledger.identus.walletsdk.domain.models.Credential as SdkCredential
import org.hyperledger.identus.walletsdk.domain.models.Message as SdkMessage
import org.hyperledger.identus.walletsdk.edgeagent.DIDCOMM1

class IdentusJWTProtocol(
    override val protocolId: String,
    override val connectionManager: ConnectionManager<SdkMessage>,
    override val contactManager: ContactManager,
    override val credentialManager: CredentialManager<SdkCredential, SdkMessage>,
) : Protocol<SdkCredential, SdkMessage>() {
    private val messages = MutableLiveData<List<SdkMessage>>()

    override suspend fun startConnection() {
        // Start sdk
        connectionManager.start()
        connectionManager.receiveMessage { msg ->
            messages.value = messages.value?.plus(msg) ?: emptyList()
            credentialManager.handleInbound(
                msg,
                connectionManager
            )
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
        fun getInstance(application: Application): IdentusJWTProtocol =
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