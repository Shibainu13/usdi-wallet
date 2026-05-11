package com.dev.usdi_wallet.eudi

import com.dev.usdi_wallet.domain.contact.Contact
import com.dev.usdi_wallet.domain.contact.ContactManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class EudiContactManager : ContactManager {
    private val sdk = EudiSdk.getInstance()
    private val protocolId = "OPENID4VC"

    override fun canHandle(invitation: String): Boolean =
        invitation.startsWith("openid-credential-offer://") ||
        invitation.startsWith("openid4vp://") ||
        invitation.startsWith("mdoc-openid4vp://") ||
        invitation.contains("credential_offer=")

    override suspend fun parseInvitation(invitation: String) {
        sdk.processInvitation(invitation)
    }

    override fun getContacts(): Flow<List<Contact>> = emptyFlow()

    override fun removeContact(contact: Contact) {
        TODO("Not yet implemented")
    }
}