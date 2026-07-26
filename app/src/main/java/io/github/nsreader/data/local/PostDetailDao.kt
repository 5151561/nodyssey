package io.github.nsreader.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import io.github.nsreader.model.PostContent
import io.github.nsreader.model.ThreadSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * A cached thread: the opening post plus every comment page fetched so far.
 *
 * `@Relation` has no ORDER BY, so [comments] arrives unordered and has to be sorted by
 * `(page, position)` — [toSnapshot] is the only place that happens.
 */
data class CachedThread(
    @Embedded val detail: PostDetailEntity,
    @Relation(parentColumn = "postId", entityColumn = "postId")
    val comments: List<CommentEntity>,
)

fun CachedThread.toSnapshot(): ThreadSnapshot {
    val ordered = comments.sortedWith(compareBy({ it.page }, { it.position }))
    return ThreadSnapshot(
        postId = detail.postId,
        title = detail.title,
        body = detail.body,
        comments = ordered.map { it.content },
        commentPages = ordered.map { it.page },
        loadedPages = detail.loadedPages,
        totalPages = detail.totalPages,
        cachedAtMillis = detail.cachedAtMillis,
    )
}

@Dao
interface PostDetailDao {
    @Transaction
    @Query("SELECT * FROM post_details WHERE postId = :postId")
    fun observeThread(postId: Long): Flow<CachedThread?>

    @Query("SELECT * FROM post_details WHERE postId = :postId")
    suspend fun findDetail(postId: Long): PostDetailEntity?

    /**
     * Writes one fetched comment page into the thread.
     *
     * Three rules, each of which was a bug waiting to happen:
     *
     * - **A null [body] preserves the stored one.** NodeSeek renders the opening post on page 1
     *   only, so appending page 2 would otherwise blank out the post the user is reading.
     * - **Page 1 replaces every comment; later pages replace only themselves.** Page 1 arriving means
     *   "this is a fresh read", and threads shrink when a comment is deleted. Appending page 4 must
     *   not disturb pages 1-3 already on screen.
     * - **[PostDetailEntity.loadedPages] describes a contiguous prefix.** Re-reading page 1 deletes
     *   later pages, so the cursor must reset to 1 as well. Keeping the old cursor after deleting the
     *   rows would make the next append skip straight from page 1 to page 4.
     */
    @Transaction
    suspend fun saveThreadPage(
        postId: Long,
        title: String,
        body: PostContent?,
        totalPages: Int,
        page: Int,
        comments: List<CommentEntity>,
        nowMillis: Long,
    ) {
        val existing = findDetail(postId)
        upsertDetail(
            PostDetailEntity(
                postId = postId,
                title = title.ifBlank { existing?.title.orEmpty() },
                body = body ?: existing?.body,
                totalPages = totalPages,
                loadedPages = if (page == 1) 1 else maxOf(existing?.loadedPages ?: 0, page),
                cachedAtMillis = nowMillis,
            ),
        )
        if (page == 1) deleteAllComments(postId) else deleteCommentPage(postId, page)
        upsertComments(comments)
    }

    @Upsert
    suspend fun upsertDetail(detail: PostDetailEntity)

    @Upsert
    suspend fun upsertComments(comments: List<CommentEntity>)

    @Query("DELETE FROM post_comments WHERE postId = :postId")
    suspend fun deleteAllComments(postId: Long)

    @Query("DELETE FROM post_comments WHERE postId = :postId AND page = :page")
    suspend fun deleteCommentPage(
        postId: Long,
        page: Int,
    )

    /** Withdraws the freshness claim on every cached thread, keeping the content readable meanwhile. */
    @Query("UPDATE post_details SET cachedAtMillis = 0")
    suspend fun expireAllThreads()

    @Query("DELETE FROM post_details")
    suspend fun clearAllThreads()

    /** Keeps the [keep] most recently read threads and drops the rest, so the cache stays bounded. */
    @Query(
        """
        DELETE FROM post_details
        WHERE postId NOT IN (
            SELECT postId FROM post_details ORDER BY cachedAtMillis DESC LIMIT :keep
        )
        """,
    )
    suspend fun trimTo(keep: Int)
}
