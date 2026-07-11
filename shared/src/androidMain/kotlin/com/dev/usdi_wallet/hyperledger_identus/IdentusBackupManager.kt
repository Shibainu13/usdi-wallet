package com.dev.usdi_wallet.hyperledger_identus

import android.content.Context
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.domain.backup.WalletBackupManager

class IdentusBackupManager(
    private val context: Context,
) : WalletBackupManager {
    private val sdk = HyperledgerIdentusSdk.getInstance()

    override suspend fun export(): String {
        val backup = sdk.agent.backupWallet()
        Logger.d(IdentusBackupManager::class.toString()) { "Backup payload: $backup" }
        return backup
    }

    override suspend fun restore(payload: String): Boolean = runCatching {
//        Logger.d(IdentusBackupManager::class.toString()) { "Stopping agent for restore..." }
//        sdk.stopAgent()
//
//        clearPlutoDatabase()
//
//        Logger.d(IdentusBackupManager::class.toString()) { "Recovering wallet..." }
        sdk.agent.recoverWallet(payload)

//        Logger.d(IdentusBackupManager::class.toString()) { "Restarting agent..." }
//        sdk.startAgent(IdentusDIDCommConfig.MEDIATOR_DID, context)

        true
    }.getOrElse { error ->
        Logger.e(IdentusBackupManager::class.toString()) { "Failed to restore backup: $error" }
        false
    }

    private fun clearPlutoDatabase() {
        runCatching {
            listOf(
                "hyperledger_identus.db",
                "hyperledger_identus.db-journal",
            ).forEach { filename ->
                val file = context.getDatabasePath(filename)
                if (file.exists()) {
                    file.delete()
                    Logger.d(IdentusBackupManager::class.toString()) { "Deleted $filename" }
                }
            }
        }.onFailure { e ->
            Logger.w(IdentusBackupManager::class.toString()) { "Failed to clear Pluto database: ${e.message}" }
        }
    }
}