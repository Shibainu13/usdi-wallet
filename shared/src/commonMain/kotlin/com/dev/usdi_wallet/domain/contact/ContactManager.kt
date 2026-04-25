package com.dev.usdi_wallet.domain.contact

import kotlinx.coroutines.flow.Flow

interface ContactManager {
    fun canHandle(invitation: String): Boolean
    suspend fun parseInvitation(invitation: String)
    fun getContacts(): Flow<List<Contact>>
    fun removeContact(contact: Contact)
}