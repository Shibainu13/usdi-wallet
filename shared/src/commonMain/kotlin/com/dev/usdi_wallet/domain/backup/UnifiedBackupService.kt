package com.dev.usdi_wallet.domain.backup

import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.domain.protocol.Protocol
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

class UnifiedBackupService private constructor(
    private val protocols: List<Protocol<*, *>>,
    private val onPreRestored: suspend () -> Unit = {},
    private val onRestoreCompleted: suspend () -> Unit = {},
) {
    private val backupCrypto = BackupCrypto()

    suspend fun exportEncrypted(passphrase: String): String {
        val backup = exportAll()
        Logger.d(UnifiedBackupService::class.toString()) {
            "Backing up wallet: $backup"
        }
        Logger.d(UnifiedBackupService::class.toString()) {
            "encoding backup object to json..."
        }
        val json = Json.encodeToString(backup)
        Logger.d(UnifiedBackupService::class.toString()) {
            "backup json: $json"
        }
        val key = backupCrypto.deriveKey(passphrase)
        Logger.d(UnifiedBackupService::class.toString()) {
            "Backup key: ${key.contentToString()}"
        }
        val encrypted = backupCrypto.encrypt(json, key)
        Logger.d(UnifiedBackupService::class.toString()) {
            "Encrypted: $encrypted"
        }
        return encrypted
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
        Logger.d(UnifiedBackupService::class.toString()) {
            "Restoring backup file"
        }
        val key = backupCrypto.deriveKey(passphrase)
        Logger.d(UnifiedBackupService::class.toString()) {
            "Key: ${key.contentToString()}"
        }
        val json = backupCrypto.decrypt(encrypted, key)
            ?: return RestoreResult(
                succeeded = emptyList(),
                failed = emptyList(),
                skipped = emptyList(),
                error = "Invalid passphrase or corrupted backup",
            )
        Logger.d(UnifiedBackupService::class.toString()) {
            "Back up json: $json"
        }
        val backup = runCatching { Json.decodeFromString<UnifiedBackup>(json) }.getOrNull()
            ?: return RestoreResult(
                succeeded = emptyList(),
                failed = emptyList(),
                skipped = emptyList(),
                error = "Backup file is corrupted",
            )
        Logger.d(UnifiedBackupService::class.toString()) {
            "Back up: $backup"
        }
        val result = restoreAll(backup)

        if (result.error == null && result.failed.isEmpty()) {
            runCatching { onRestoreCompleted() }.onFailure { e ->
                Logger.w(UnifiedBackupService::class.toString()) {
                    "onRestoreCompleted hook failed: $e"
                }
            }
        }

        return result
    }

    private suspend fun restoreAll(backup: UnifiedBackup): RestoreResult {
        runCatching { onPreRestored() }.onFailure { e ->
            Logger.w(UnifiedBackupService::class.toString()) {
                "Pre-restore hook failed: $e"
            }
        }
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

        fun getInstance(
            protocols: List<Protocol<*, *>>,
            onPreRestored: suspend () -> Unit = {},
            onRestoreCompleted: suspend () -> Unit = {},
        ): UnifiedBackupService =
            _instance ?: UnifiedBackupService(protocols, onPreRestored, onRestoreCompleted).also { _instance = it }

        fun getInstance(): UnifiedBackupService =
            _instance ?: error("UnifiedBackupService not initialized — call getInstance(protocols) first")
    }
}

@Serializable
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