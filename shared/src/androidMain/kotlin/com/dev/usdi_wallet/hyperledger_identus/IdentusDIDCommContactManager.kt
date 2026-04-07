package com.dev.usdi_wallet.hyperledger_identus

import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.contact.Contact
import com.dev.usdi_wallet.contact.ContactManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.hyperledger.identus.walletsdk.domain.models.DIDPair
import org.hyperledger.identus.walletsdk.edgeagent.DIDCOMM1
import org.hyperledger.identus.walletsdk.edgeagent.protocols.ProtocolType
import org.hyperledger.identus.walletsdk.edgeagent.protocols.issueCredential.OfferCredential
import org.hyperledger.identus.walletsdk.edgeagent.protocols.outOfBand.ConnectionlessCredentialOffer
import org.hyperledger.identus.walletsdk.edgeagent.protocols.outOfBand.ConnectionlessRequestPresentation
import org.hyperledger.identus.walletsdk.edgeagent.protocols.outOfBand.OutOfBandInvitation
import org.hyperledger.identus.walletsdk.edgeagent.protocols.outOfBand.PrismOnboardingInvitation

class IdentusDIDCommContactManager : ContactManager {

//    init {
//        scope.launch {
//            val pluto = HyperledgerIdentusSdk.getInstance().pluto
//            pluto.getAllDidPairs().collect {
//
//            }
//        }
//    }

    override fun canHandle(invitation: String): Boolean {
        return when {
            invitation.contains(ProtocolType.Didcomminvitation.value) ||
                 invitation.contains("_oob") -> true
            else -> false
        }
    }

    override suspend fun parseInvitation(invitation: String) {
        HyperledgerIdentusSdk.getInstance().agent.let { agent ->
            try {
                Logger.d(this::class.toString()) {"Parsing invitation..."}

                when (val invitation = agent.parseInvitation(invitation)) {
                    is OutOfBandInvitation -> {
                        agent.acceptOutOfBandInvitation(invitation)
                    }

                    is PrismOnboardingInvitation -> {
                        agent.acceptInvitation(invitation)
                    }

                    is ConnectionlessCredentialOffer -> {
                        val offer = OfferCredential.fromMessage(invitation.offerCredential.makeMessage())
                        val subjectDID = agent.createNewPrismDID()
                        val request = agent.prepareRequestCredentialWithIssuer(subjectDID, offer)
                        agent.sendMessage(request.makeMessage())
                    }

                    is ConnectionlessRequestPresentation -> {
                        agent.pluto.storeMessage(invitation.requestPresentation.makeMessage())
                    }
                }

                Logger.d(this::class.toString()) {"Invitation accepted"}
            } catch (e: Exception) {
                Logger.e(this::class.toString()) {"Error while parsing invitation $invitation: ${e.message}"}
            }
        }
    }

    override fun getContacts(): Flow<List<Contact>> =
        HyperledgerIdentusSdk.getInstance().pluto
            .getAllDidPairs()
            .map { pairs -> pairs.map { toUsdiContact(it) } }

    override fun removeContact(contact: Contact) {
        TODO("Not yet implemented")
    }

    fun toUsdiContact(didPair: DIDPair): Contact =
        Contact(
            holder = didPair.holder.toString(),
            name = didPair.name ?: "Unknown",
        )
}