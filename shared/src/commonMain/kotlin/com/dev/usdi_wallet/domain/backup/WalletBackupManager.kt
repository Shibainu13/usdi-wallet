package com.dev.usdi_wallet.domain.backup

interface WalletBackupManager {
    suspend fun export(): String?
    suspend fun restore(payload: String): Boolean
}