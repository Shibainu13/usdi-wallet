package com.dev.usdi_wallet.db.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PendingProofRequestDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPending(request: PendingProofRequest)

    @Query("DELETE FROM pending_proof_requests WHERE messageId = :messageId")
    suspend fun deletePending(messageId: String)

    @Query("SELECT messageId FROM pending_proof_requests")
    suspend fun getAllIds(): List<String>
}