package io.github.nsreader.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    /** Emits only the profile belonging to the cookies that are active now. */
    @Query("SELECT * FROM self_profile WHERE sessionFingerprint = :sessionFingerprint")
    fun observe(sessionFingerprint: Int): Flow<SelfProfileEntity?>

    @Query("SELECT * FROM self_profile WHERE sessionFingerprint = :sessionFingerprint")
    suspend fun find(sessionFingerprint: Int): SelfProfileEntity?

    /** Keeps at most one account on disk, so stale private account data cannot accumulate. */
    @Transaction
    suspend fun replace(profile: SelfProfileEntity) {
        deleteAll()
        upsert(profile)
    }

    @Upsert
    suspend fun upsert(profile: SelfProfileEntity)

    @Query("DELETE FROM self_profile")
    suspend fun deleteAll()
}
