package io.github.nodyssey.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface CacheSessionDao {
    @Query("SELECT * FROM cache_session WHERE id = 0")
    suspend fun find(): CacheSessionEntity?

    @Upsert
    suspend fun upsert(state: CacheSessionEntity)
}
