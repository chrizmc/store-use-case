package com.bopis.associate.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface CachedResponseDao {
    @Upsert
    suspend fun put(entry: CachedResponseEntity)

    @Query("SELECT * FROM cached_responses WHERE `key` = :key")
    suspend fun get(key: String): CachedResponseEntity?
}
