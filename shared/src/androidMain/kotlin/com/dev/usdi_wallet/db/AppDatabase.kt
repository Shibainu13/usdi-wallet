package com.dev.usdi_wallet.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.dev.usdi_wallet.db.data.MessageReadStatus
import com.dev.usdi_wallet.db.data.MessageReadStatusDao
import com.dev.usdi_wallet.db.data.PendingProofRequest
import com.dev.usdi_wallet.db.data.PendingProofRequestDao

@Database(
    entities = [
        MessageReadStatus::class,
        PendingProofRequest::class
    ],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageReadStatusDao(): MessageReadStatusDao
    abstract fun pendingProofRequestDao(): PendingProofRequestDao

    companion object {
        private const val DATABASE_NAME = "usdi_wallet.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).build().also { instance = it }
            }
    }
}