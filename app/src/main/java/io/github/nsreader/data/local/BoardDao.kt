package io.github.nsreader.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BoardDao {
    @Query("SELECT * FROM boards ORDER BY sortIndex ASC")
    fun observeBoards(): Flow<List<BoardEntity>>

    @Query("SELECT COUNT(*) FROM boards")
    suspend fun count(): Int

    /**
     * Replaces the whole board list in one transaction.
     *
     * A board removed upstream has to disappear locally too — this project has already been bitten
     * by a stale board (`meaningless`) surviving in a hardcoded list. Upserting without deleting
     * would reintroduce exactly that bug, so the delete is part of the same transaction as the
     * insert and observers never see an empty strip in between.
     */
    @Transaction
    suspend fun replaceAll(boards: List<BoardEntity>) {
        deleteAll()
        upsertAll(boards)
    }

    @Upsert
    suspend fun upsertAll(boards: List<BoardEntity>)

    @Query("DELETE FROM boards")
    suspend fun deleteAll()
}
