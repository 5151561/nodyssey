package io.github.nodyssey.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ReadMarkDao {
    @Upsert
    suspend fun upsert(mark: ReadMarkEntity)

    @Query("SELECT * FROM post_read_marks WHERE postId = :postId")
    suspend fun find(postId: Long): ReadMarkEntity?

    @Query("DELETE FROM post_read_marks")
    suspend fun clearAll()

    /**
     * Records that the thread was read at [nowMillis] with [commentCount] comments visible.
     *
     * The seen count only ever grows. Opening page 1 of a thread the user had already read to the
     * end must not reset the baseline and make old replies look new again.
     */
    suspend fun markRead(
        postId: Long,
        commentCount: Int,
        nowMillis: Long,
    ) {
        val previous = find(postId)?.lastSeenCommentCount ?: 0
        upsert(
            ReadMarkEntity(
                postId = postId,
                lastReadAtMillis = nowMillis,
                lastSeenCommentCount = maxOf(previous, commentCount),
            ),
        )
    }
}
