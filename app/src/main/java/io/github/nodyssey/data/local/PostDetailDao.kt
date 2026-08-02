package io.github.nodyssey.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import io.github.nodyssey.model.PostContent
import io.github.nodyssey.model.PostReactions
import io.github.nodyssey.model.ThreadSnapshot
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
        firstLoadedPage = detail.firstLoadedPage,
        lastLoadedPage = detail.lastLoadedPage,
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
     * - **[replacesWindow] decides what survives.** A *refresh* — the first read of a thread, a retry,
     *   or a jump to a page nowhere near the cached ones — makes [page] the whole of the cache. Every
     *   other page goes, because a deleted comment shifts every floor after it up by one and the
     *   pages either side of it now describe content that has moved. An *extend* — the next page as
     *   the reader scrolls, or the previous one as they page back — replaces only itself.
     * - **[PostDetailEntity.firstLoadedPage]..[PostDetailEntity.lastLoadedPage] stays contiguous.**
     *   An extend may only widen the window by adjoining it; a page with a gap between it and the
     *   window is not an extend at all and is stored as a refresh, or the list would read as one
     *   scroll while silently skipping the floors in between.
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
        replacesWindow: Boolean,
    ) {
        val existing = findDetail(postId)
        val replaces =
            replacesWindow ||
                existing == null ||
                page < existing.firstLoadedPage - 1 ||
                page > existing.lastLoadedPage + 1
        upsertDetail(
            PostDetailEntity(
                postId = postId,
                title = title.ifBlank { existing?.title.orEmpty() },
                body = body ?: existing?.body,
                totalPages = totalPages,
                firstLoadedPage = if (replaces) page else minOf(existing.firstLoadedPage, page),
                lastLoadedPage = if (replaces) page else maxOf(existing.lastLoadedPage, page),
                cachedAtMillis = nowMillis,
            ),
        )
        if (replaces) deleteAllComments(postId) else deleteCommentPage(postId, page)
        upsertComments(comments)
    }

    /**
     * Applies [transform] to one floor's tallies, in a transaction.
     *
     * Read-modify-write rather than a targeted `UPDATE`: a floor is stored as a serialized
     * [PostContent] blob, so there is no tally column to set and no `commentId` column to match on.
     * The transaction is what keeps two reactions sent in quick succession from each writing back a
     * copy of the row they read, dropping whichever landed first.
     *
     * The opening post is a floor too — it lives on [PostDetailEntity.body] and carries its own
     * `commentId`, so it is checked before the comment rows. A [commentId] matching neither is a
     * no-op: the thread was re-fetched or trimmed while the request was in flight, and the answer we
     * are holding no longer describes anything on screen.
     */
    @Transaction
    suspend fun updateReactions(
        postId: Long,
        commentId: Long,
        transform: (PostReactions?) -> PostReactions,
    ) {
        val detail = findDetail(postId) ?: return
        val body = detail.body
        if (body != null && body.commentId == commentId) {
            upsertDetail(detail.copy(body = body.copy(reactions = transform(body.reactions))))
            return
        }
        val row = findComments(postId).firstOrNull { it.content.commentId == commentId } ?: return
        upsertComments(
            listOf(row.copy(content = row.content.copy(reactions = transform(row.content.reactions)))),
        )
    }

    @Query("SELECT * FROM post_comments WHERE postId = :postId")
    suspend fun findComments(postId: Long): List<CommentEntity>

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
