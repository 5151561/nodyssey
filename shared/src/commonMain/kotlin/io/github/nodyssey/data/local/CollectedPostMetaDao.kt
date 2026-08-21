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

    /**
     * The collection itself, in collection order — the rows a walk of `list-collection` marked.
     *
     * This is what 收藏 draws, online and off. Reading the list from here rather than from whatever
     * the last request returned is what makes the screen open to content with the network down, and
     * it costs nothing when the network is up: a successful walk writes here first, so the flow
     * emits the site's own answer a moment later either way.
     */
    @Query("SELECT * FROM collected_post_meta WHERE listedOrder IS NOT NULL ORDER BY listedOrder ASC")
    fun observeCollection(): Flow<List<CollectedPostMetaEntity>>

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
                    // Never written from here: what a source knows about a thread and whether the
                    // thread is on the list are different facts, learned by different routes.
                    listedOrder = old?.listedOrder,
                    updatedAtMillis = nowMillis,
                )
            },
        )
    }

    /**
     * Records a complete walk of `list-collection` as *the* collection.
     *
     * Wholesale rather than additive, and that is the point: a thread un-collected on the web has to
     * leave this device's list too, and the only evidence of that is its absence from a walk. So the
     * marks come off everything first and go back onto exactly what the walk returned. The details
     * themselves are never cleared — [remember] still coalesces — because an un-collected thread's
     * board and author are just as true as they were, and re-collecting it should not cost the row
     * everything it knew.
     *
     * [rows] must be the whole walk. Handing this one page would silently truncate the collection to
     * that page, which is why nothing but the loader that walks to the end calls it.
     */
    @Transaction
    suspend fun replaceCollection(
        rows: List<CollectedPostMetaEntity>,
        nowMillis: Long,
    ) {
        remember(rows, nowMillis)
        replaceOrder(rows.map { it.postId })
    }

    /**
     * Makes exactly these threads the list, in this order.
     *
     * Also how a removal the site refused is undone: the rows went off the list optimistically and
     * that write is on disk, so putting them back is a matter of restating the order they were in
     * rather than of reloading — which is no help at all when the refusal *was* the network.
     */
    @Transaction
    suspend fun replaceOrder(postIds: List<Long>) {
        clearOrders()
        postIds.forEachIndexed { index, postId -> setOrder(postId, index) }
    }

    /** Takes threads off the list without forgetting them — what un-collecting one does here. */
    @Query("UPDATE collected_post_meta SET listedOrder = NULL WHERE postId IN (:postIds)")
    suspend fun unlist(postIds: List<Long>)

    @Query("UPDATE collected_post_meta SET listedOrder = :order WHERE postId = :postId")
    suspend fun setOrder(
        postId: Long,
        order: Int,
    )

    @Query("UPDATE collected_post_meta SET listedOrder = NULL")
    suspend fun clearOrders()

    @Upsert
    suspend fun upsert(rows: List<CollectedPostMetaEntity>)

    /**
     * Keeps the [keep] most recently touched rows *of the ones not on the list*.
     *
     * The list is exempt because trimming it would be deleting content rather than a cache: a row
     * with a [CollectedPostMetaEntity.listedOrder] is a row 收藏 is currently drawing, and dropping
     * it would take a thread out of the collection on this device alone. The bound is there for the
     * other kind of row — threads collected and un-collected over years — and those are exactly the
     * ones without a mark.
     */
    @Query(
        """
        DELETE FROM collected_post_meta
        WHERE listedOrder IS NULL
        AND postId NOT IN (
            SELECT postId FROM collected_post_meta
            WHERE listedOrder IS NULL
            ORDER BY updatedAtMillis DESC LIMIT :keep
        )
        """,
    )
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM collected_post_meta")
    suspend fun clearAll()
}
