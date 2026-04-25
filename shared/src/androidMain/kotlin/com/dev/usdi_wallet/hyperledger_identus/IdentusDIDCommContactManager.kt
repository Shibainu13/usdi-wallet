package com.dev.usdi_wallet.hyperledger_identus

import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.domain.contact.Contact
import com.dev.usdi_wallet.domain.contact.ContactManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.hyperledger.identus.walletsdk.apollo.utils.Secp256k1KeyPair
import org.hyperledger.identus.walletsdk.domain.models.Curve
import org.hyperledger.identus.walletsdk.domain.models.DIDPair
import org.hyperledger.identus.walletsdk.domain.models.KeyCurve
import org.hyperledger.identus.walletsdk.domain.models.KeyPurpose
import org.hyperledger.identus.walletsdk.edgeagent.DIDCOMM1
import org.hyperledger.identus.walletsdk.edgeagent.protocols.ProtocolType
import org.hyperledger.identus.walletsdk.edgeagent.protocols.issueCredential.OfferCredential
import org.hyperledger.identus.walletsdk.edgeagent.protocols.outOfBand.ConnectionlessCredentialOffer
import org.hyperledger.identus.walletsdk.edgeagent.protocols.outOfBand.ConnectionlessRequestPresentation
import org.hyperledger.identus.walletsdk.edgeagent.protocols.outOfBand.OutOfBandInvitation
import org.hyperledger.identus.walletsdk.edgeagent.protocols.outOfBand.PrismOnboardingInvitation

class IdentusDIDCommContactManager : ContactManager {
    private val sdk = HyperledgerIdentusSdk.getInstance()

    override fun canHandle(invitation: String): Boolean {
        return when {
            invitation.contains(ProtocolType.Didcomminvitation.value) ||
                 invitation.contains("_oob") -> true
            else -> false
        }
    }

    override suspend fun parseInvitation(invitation: String) {
        try {
            Logger.d(this::class.toString()) {"Parsing invitation..."}

            when (val invitation = sdk.agent.parseInvitation(invitation)) {
                is OutOfBandInvitation -> {
                    sdk.agent.acceptOutOfBandInvitation(invitation)
                }

                is PrismOnboardingInvitation -> {
                    sdk.agent.acceptInvitation(invitation)
                }

                is ConnectionlessCredentialOffer -> {
                    sdk.agent.pluto.storeMessage(invitation.offerCredential.makeMessage())
                }

                is ConnectionlessRequestPresentation -> {
                    sdk.agent.pluto.storeMessage(invitation.requestPresentation.makeMessage())
                }
            }

            Logger.d(this::class.toString()) {"Invitation accepted"}
        } catch (e: Exception) {
            Logger.e(this::class.toString()) {"Error while parsing invitation $invitation: ${e.message}"}
        }
    }

    override fun getContacts(): Flow<List<Contact>> =
        sdk.pluto.getAllDidPairs().map { pairs ->
            pairs.map { toUsdiContact(it) }
        }

    override fun removeContact(contact: Contact) {
        TODO("Not yet implemented")
    }

    fun toUsdiContact(didPair: DIDPair): Contact =
        Contact(
            holder = didPair.holder.toString(),
            name = didPair.name ?: "Unknown",
            protocol = DIDCOMM1,
        )
}