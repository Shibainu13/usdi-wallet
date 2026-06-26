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
                error = "Invalid passphrase or corrupted backup",
            )
        val backup = runCatching { Json.decodeFromString<UnifiedBackup>(json) }.getOrNull()
            ?: return RestoreResult(
                succeeded = emptyList(),
                failed = emptyList(),
                skipped = emptyList(),
                error = "Backup file is corrupted",
            )
        return restoreAll(backup)
    }

    private suspend fun restoreAll(backup: UnifiedBackup): RestoreResult {
        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val skipped = backup.nonRecoverable.toMutableList()

        protocols.forEach { protocol ->
            val manager = protocol.walletBackupManager
            val payload = backup.recoverable[protocol.protocolId]

            when {
                manager == null -> skipped.add(protocol.protocolId)
                payload == null -> skipped.add(protocol.protocolId)
                else -> {
                    val success = manager.restore(payload)
                    if (success) succeeded.add(protocol.protocolId)
                    else failed.add(protocol.protocolId)
                }
            }
        }

        return RestoreResult(succeeded = succeeded, failed = failed, skipped = skipped)
    }

    companion object {
        private var _instance: UnifiedBackupService? = null

        fun getInstance(protocols: List<Protocol<*, *>>): UnifiedBackupService =
            _instance ?: UnifiedBackupService(protocols).also { _instance = it }

        fun getInstance(): UnifiedBackupService =
            _instance ?: error("UnifiedBackupService not initialized — call getInstance(protocols) first")
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