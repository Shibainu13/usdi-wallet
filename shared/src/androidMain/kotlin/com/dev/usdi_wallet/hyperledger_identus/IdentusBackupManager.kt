package com.dev.usdi_wallet.hyperledger_identus

import com.dev.usdi_wallet.domain.backup.WalletBackupManager

class IdentusBackupManager : WalletBackupManager {
    private val sdk = HyperledgerIdentusSdk.getInstance()

    override suspend fun export(): String {
        return sdk.agent.backupWallet()
    }

    override suspend fun restore(payload: String): Boolean = runCatching {
        sdk.agent.recoverWallet(payload)
        true
    }.getOrElse { false }
}