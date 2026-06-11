package com.dev.usdi_wallet.domain.backup

import com.dev.usdi_wallet.domain.protocol.Protocol
import kotlinx.serialization.json.Json
import kotlin.time.Clock

class UnifiedBackupService private constructor(
    private val protocols: List<Protocol<*, *>>,
) {
    private val backupCrypto = BackupCrypto()

    suspend fun exportEncrypted(passphrase: String): String {
        val backup = exportAll()
        val json = Json.encodeToString(backup)
        val key = backupCrypto.deriveKey(passphrase)
        return backupCrypto.encrypt(json, key)
    }

    private suspend fun exportAll(): UnifiedBackup {
        val recoverable = mutableMapOf<String, String>()
        val nonRecoverable = mutableListOf<String>()

        protocols.forEach { protocol ->
            val manager = protocol.walletBackupManager
            if (manager != null) {
                val payload = manager.export()
                if (payload != null) {
                    recoverable[protocol.protocolId] = payload
                } else {
                    nonRecoverable.add(protocol.protocolId)
                }
            } else {
                nonRecoverable.add(protocol.protocolId)
            }
        }

        return UnifiedBackup(
            createdAt = Clock.System.now().toEpochMilliseconds(),
            recoverable = recoverable,
            nonRecoverable = nonRecoverable,
        )
    }

    suspend fun restoreEncrypted(encrypted: String, passphrase: String): RestoreResult {
        val key = backupCrypto.deriveKey(passphrase)
        val json = backupCrypto.decrypt(encrypted, key)
            ?: return RestoreResult(
                succeeded = emptyList(),
                failed = emptyList(),
                skipped = emptyList(),
                error = "Invalid recovery phase or corrupted backup"
            )
        val backup = Json.decodeFromString<UnifiedBackup>(json)
        return restoreAll(backup)
    }

    private suspend fun restoreAll(backup: UnifiedBackup): RestoreResult {
        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        protocols.forEach { protocol ->
            val manager = protocol.walletBackupManager
            val payload = backup.recoverable[protocol.protocolId]

            when {
                manager == null -> skipped.add(protocol.protocolId)
                payload == null -> skipped.add(protocol.protocolId)
                else -> {
                    val success = manager.restore(payload)
                    if (success) succeeded.add(payload)
                    else failed.add(payload)
                }
            }
        }

        return RestoreResult(
            succeeded = succeeded,
            failed = failed,
            skipped = skipped,
        )
    }

    companion object {
        private var _instance: UnifiedBackupService? = null

        fun getInstance(protocols: List<Protocol<*, *>>): UnifiedBackupService {
            return _instance ?: UnifiedBackupService(protocols).also { _instance = it }
        }
    }
}

data class UnifiedBackup(
    val createdAt: Long,
    val recoverable: Map<String, String>,
    val nonRecoverable: List<String>,
)

data class RestoreResult(
    val succeeded: List<String>,
    val failed: List<String>,
    val skipped: List<String>,
    val error: String? = null,
)