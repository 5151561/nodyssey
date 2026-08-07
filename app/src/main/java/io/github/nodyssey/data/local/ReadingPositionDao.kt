package io.github.nodyssey.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ReadingPositionDao {
    @Upsert
    suspend fun upsert(position: ReadingPositionEntity)

    @Query("SELECT * FROM post_reading_positions WHERE postId = :postId")
    suspend fun find(postId: Long): ReadingPositionEntity?

    @Query("SELECT COUNT(*) FROM post_reading_positions")
    suspend fun count(): Int

    @Query("DELETE FROM post_reading_positions WHERE postId = :postId")
    suspend fun delete(postId: Long)

    @Query("DELETE FROM post_reading_positions")
    suspend fun clearAll()

    /**
     * Keeps the [keep] most recently written places and drops the rest.
     *
     * Deliberately not run on every write: a place is written whenever the reader stops scrolling,
     * and a full-table `DELETE` on each of those would be the one expensive statement in a path that
     * is otherwise a single upsert. It runs alongside the read marks' own trim instead — same number,
     * same moment, and that moment is opening a thread rather than scrolling one.
     */
    @Query(
        """
        DELETE FROM post_reading_positions
        WHERE postId NOT IN (
            SELECT postId FROM post_reading_positions ORDER BY updatedAtMillis DESC LIMIT :keep
        )
        """,
    )
    suspend fun trimTo(keep: Int)
}
