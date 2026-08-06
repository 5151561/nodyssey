package io.github.nodyssey.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadMarkDao {
    @Upsert
    suspend fun upsert(mark: ReadMarkEntity)

    @Query("SELECT * FROM post_read_marks WHERE postId = :postId")
    suspend fun find(postId: Long): ReadMarkEntity?

    @Query("DELETE FROM post_read_marks")
    suspend fun clearAll()

    /**
     * Most recently read first — the browsing history, in the order a reader would look for it.
     *
     * Deliberately not a join against `posts`: the snapshot columns exist so that threads which were
     * never in any feed still have a title here, and joining would put them back to blank rows.
     */
    @Query("SELECT * FROM post_read_marks ORDER BY lastReadAtMillis DESC LIMIT :limit")
    fun observeHistory(limit: Int): Flow<List<ReadMarkEntity>>

    @Query("DELETE FROM post_read_marks WHERE postId = :postId")
    suspend fun delete(postId: Long)

    /**
     * Keeps the [keep] most recently read threads and drops the rest.
     *
     * The table gains a row for every thread ever opened and had nothing bounding it before the
     * history screen gave it a second job. Dropping the oldest also drops their unread baselines,
     * which is correct: a thread nobody has opened in hundreds of reads is one whose "4 new replies"
     * nobody is waiting on.
     */
    @Query(
        """
        DELETE FROM post_read_marks
        WHERE postId NOT IN (
            SELECT postId FROM post_read_marks ORDER BY lastReadAtMillis DESC LIMIT :keep
        )
        """,
    )
    suspend fun trimTo(keep: Int)

    /**
     * Records that the thread was read at [nowMillis] with [commentCount] comments visible.
     *
     * The seen count only ever grows. Opening page 1 of a thread the user had already read to the
     * end must not reset the baseline and make old replies look new again.
     *
     * The snapshot fields follow the same rule for the same reason: a read that arrives without a
     * title — page 3 of a thread whose page 1 was never cached — must not blank out the title an
     * earlier read did capture.
     */
    suspend fun markRead(
        postId: Long,
        commentCount: Int,
        nowMillis: Long,
        title: String? = null,
        authorName: String? = null,
        authorUid: Long? = null,
        categoryTitle: String? = null,
        totalComments: Int? = null,
    ) {
        val previous = find(postId)
        upsert(
            ReadMarkEntity(
                postId = postId,
                lastReadAtMillis = nowMillis,
                lastSeenCommentCount = maxOf(previous?.lastSeenCommentCount ?: 0, commentCount),
                title = title?.ifBlank { null } ?: previous?.title,
                authorName = authorName?.ifBlank { null } ?: previous?.authorName,
                authorUid = authorUid ?: previous?.authorUid,
                categoryTitle = categoryTitle?.ifBlank { null } ?: previous?.categoryTitle,
                commentCount = totalComments ?: previous?.commentCount,
            ),
        )
    }
}
