package com.dev.usdi_wallet.db.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_proof_requests")
data class PendingProofRequest(
    @PrimaryKey
    val messageId: String,
    val thid: String,
    val createdAt: Long,
)