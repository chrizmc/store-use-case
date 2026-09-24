package com.bopis.associate.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingActionDao {
    @Insert
    suspend fun insert(action: PendingActionEntity)

    @Query("SELECT * FROM pending_actions ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingActionEntity>

    @Query("SELECT count(*) FROM pending_actions")
    suspend fun count(): Int

    @Delete
    suspend fun delete(action: PendingActionEntity)
}
