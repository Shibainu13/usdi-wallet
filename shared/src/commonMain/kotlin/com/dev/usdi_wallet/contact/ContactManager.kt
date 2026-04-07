package com.dev.usdi_wallet.contact

import kotlinx.coroutines.flow.Flow

interface ContactManager {
    fun canHandle(invitation: String): Boolean
    suspend fun parseInvitation(invitation: String)
    fun getContacts(): Flow<List<Contact>>
    fun removeContact(contact: Contact)
}