package com.dev.usdi_wallet.hyperledger_identus

import com.dev.usdi_wallet.credential.Credential
import com.dev.usdi_wallet.credential.CredentialManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.hyperledger.identus.walletsdk.edgeagent.DIDCOMM1
import org.hyperledger.identus.walletsdk.domain.models.Credential as SdkCredential

class IdentusJWTCredentialManager : CredentialManager {

    override fun getCredentials(): Flow<List<Credential>> =
        HyperledgerIdentusSdk.getInstance().agent.let {
            it.getAllCredentials().map { list ->
               list.map { toUsdiCredential(it) }
            }
        }

    override suspend fun getCredential(id: String): Credential? {
        TODO("Not yet implemented")
    }

    override suspend fun saveCredential(credential: Credential) {
        TODO("Not yet implemented")
    }

    override suspend fun removeCredential(id: String) {
        TODO("Not yet implemented")
    }

    fun toUsdiCredential(sdkCredential: SdkCredential): Credential =
        Credential(
            id = sdkCredential.id,
            issuer = sdkCredential.issuer,
            subject = sdkCredential.subject,
            claims = sdkCredential.claims.map { claim -> "${claim.key}: ${claim.value}"}.toString(),
            raw = sdkCredential.toString(),
            protocol = DIDCOMM1
        )
}