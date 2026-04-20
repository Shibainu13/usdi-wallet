package com.dev.usdi_wallet.db.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageReadStatus (
    @PrimaryKey
    val messageId: String,
    val isRead: Boolean,
)