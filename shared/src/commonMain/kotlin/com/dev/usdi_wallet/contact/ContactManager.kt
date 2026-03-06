package com.dev.usdi_wallet.contact

import kotlinx.coroutines.flow.Flow

interface ContactManager {
    val id: String
    fun canHandle(invitation: String): Boolean
    fun parseInvitation(invitation: String)
    fun getContacts(): Flow<List<Contact>>
    fun removeContact(contact: Contact)
}