package com.dev.usdi_wallet.credential

import kotlinx.coroutines.flow.Flow

interface CredentialManager {
    fun getCredentials(): Flow<List<Credential>>
    suspend fun getCredential(id: String): Credential?
    suspend fun saveCredential(credential: Credential)
    suspend fun removeCredential(id: String)
}