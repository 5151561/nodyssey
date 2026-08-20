package io.github.nodyssey.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectedPostMetaDao {
    @Query("SELECT * FROM collected_post_meta")
    fun observeAll(): Flow<List<CollectedPostMetaEntity>>

    @Query("SELECT * FROM collected_post_meta WHERE postId IN (:postIds)")
    suspend fun find(postIds: List<Long>): List<CollectedPostMetaEntity>

    /**
     * Folds what was just learned into what was already known.
     *
     * Null means "this source could not say", never "this is empty": a feed row knows the board and
     * the reply count and nothing about when the thread was posted, while a thread body knows the
     * author and the time and nothing about the board. Coalescing rather than replacing is what lets
     * the two of them together produce a complete row, in either order.
     *
     * One transaction, because the read and the write are one decision — two collectors learning
     * about the same thread at once would otherwise each write back a copy of what they read.
     */
    @Transaction
    suspend fun remember(
        rows: List<CollectedPostMetaEntity>,
        nowMillis: Long,
    ) {
        if (rows.isEmpty()) return
        val stored = find(rows.map { it.postId }).associateBy { it.postId }
        upsert(
            rows.map { fresh ->
                val old = stored[fresh.postId]
                CollectedPostMetaEntity(
                    postId = fresh.postId,
                    title = fresh.title ?: old?.title,
                    categoryTitle = fresh.categoryTitle ?: old?.categoryTitle,
                    categorySlug = fresh.categorySlug ?: old?.categorySlug,
                    authorName = fresh.authorName ?: old?.authorName,
                    avatarUrl = fresh.avatarUrl ?: old?.avatarUrl,
                    authorUid = fresh.authorUid ?: old?.authorUid,
                    commentCount = fresh.commentCount ?: old?.commentCount,
                    createdAtText = fresh.createdAtText ?: old?.createdAtText,
                    updatedAtMillis = nowMillis,
                )
            },
        )
    }

    @Upsert
    suspend fun upsert(rows: List<CollectedPostMetaEntity>)

    /** Keeps the [keep] most recently touched rows. Collections themselves are untouched. */
    @Query(
        """
        DELETE FROM collected_post_meta
        WHERE postId NOT IN (
            SELECT postId FROM collected_post_meta ORDER BY updatedAtMillis DESC LIMIT :keep
        )
        """,
    )
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM collected_post_meta")
    suspend fun clearAll()
}
