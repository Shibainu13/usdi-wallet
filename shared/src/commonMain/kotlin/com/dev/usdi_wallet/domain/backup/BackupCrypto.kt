package com.dev.usdi_wallet.domain.backup

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlin.io.encoding.Base64

class BackupCrypto {
    private val provider = CryptographyProvider.Default

    suspend fun deriveKey(passPhrase: String): ByteArray {
        val pbkdf2 = provider.get(PBKDF2)
        return pbkdf2.secretDerivation(
            digest = SHA256,
            iterations = 100_000,
            outputSize = 256.bits,
            salt = "usdi-wallet-backup".encodeToByteArray(),
        ).deriveSecret(passPhrase.encodeToByteArray()).toByteArray()
    }
    suspend fun encrypt(payload: String, key: ByteArray): String {
        val secretKey = provider.get(AES.GCM)
            .keyDecoder()
            .decodeFromByteArray(AES.Key.Format.RAW, key)
        val cipher = secretKey.cipher()
        val encrypted = cipher.encrypt(payload.encodeToByteArray())
        return Base64.encode(encrypted)
    }
    suspend fun decrypt(encrypted: String, key: ByteArray): String? = runCatching {
        val secretKey = provider.get(AES.GCM)
            .keyDecoder()
            .decodeFromByteArray(AES.Key.Format.RAW, key)
        val cipher = secretKey.cipher()
        val decrypted = cipher.decrypt(Base64.decode(encrypted))
        decrypted.decodeToString()
    }.getOrNull()
}