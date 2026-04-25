package com.dev.usdi_wallet.db.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface MessageReadStatusDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: MessageReadStatus)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateMessage(message: MessageReadStatus)

    @Query("SELECT isRead FROM messages WHERE messageId = :messageId")
    suspend fun isMessageRead(messageId: String): Boolean

    @Query("SELECT messageId FROM messages WHERE isRead = TRUE")
    suspend fun getReadMessages(): List<String>
}