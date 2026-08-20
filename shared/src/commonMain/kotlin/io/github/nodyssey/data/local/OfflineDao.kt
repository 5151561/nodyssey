package io.github.nodyssey.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * One row of the per-thread state the 收藏 list draws, without the body.
 *
 * A projection rather than the entity because [OfflineDao.observeStates] emits on every progress
 * write: handing back whole entities would deserialize every stored opening post from JSON each
 * time a download ticked a percent forward.
 */
data class OfflineStateRow(
    val postId: Long,
    val status: Int,
    val progress: Float?,
    val failure: Int?,
    val storedCommentCount: Int,
    val remoteCommentCount: Int?,
    /** Text plus the images this thread owns — the number the row shows once it is stored. */
    val bytes: Long,
)

/** The three numbers 离线管理 draws, read in one query so they can never disagree. */
data class OfflineUsageRow(
    val posts: Int,
    val textBytes: Long,
    val imageBytes: Long,
)

@Dao
interface OfflineDao {
    // --- state ---------------------------------------------------------------------------------

    @Query(
        """
        SELECT t.postId AS postId, t.status AS status, t.progress AS progress, t.failure AS failure,
               t.storedCommentCount AS storedCommentCount, t.remoteCommentCount AS remoteCommentCount,
               t.textBytes + IFNULL(
                   (SELECT SUM(i.bytes) FROM offline_images i WHERE i.postId = t.postId), 0
               ) AS bytes
        FROM offline_threads t
        """,
    )
    fun observeStates(): Flow<List<OfflineStateRow>>

    /**
     * Sizes, counting each image *file* once.
     *
     * Two threads that embed the same picture share the file on disk, so summing the rows would
     * bill the reader twice for bytes they are only holding once.
     */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM offline_threads WHERE status = 2) AS posts,
               (SELECT IFNULL(SUM(textBytes), 0) FROM offline_threads) AS textBytes,
               (SELECT IFNULL(SUM(bytes), 0) FROM
                   (SELECT DISTINCT fileName, bytes FROM offline_images)) AS imageBytes
        """,
    )
    fun observeUsage(): Flow<OfflineUsageRow>

    /**
     * What a stored thread of this account's has weighed on average, or null before there is one.
     *
     * The only honest basis for 「约 4.6 MB」: how big a thread is depends on how this reader's
     * boards are written, and a constant per post would be a number the app made up.
     */
    @Query(
        """
        SELECT AVG(bytes) FROM (
            SELECT t.textBytes + IFNULL(
                (SELECT SUM(i.bytes) FROM offline_images i WHERE i.postId = t.postId), 0
            ) AS bytes
            FROM offline_threads t WHERE t.status = 2
        )
        """,
    )
    suspend fun averageStoredBytes(): Double?

    // --- threads -------------------------------------------------------------------------------

    @Query("SELECT * FROM offline_threads WHERE postId = :postId")
    suspend fun find(postId: Long): OfflineThreadEntity?

    /** Oldest first, so a queue drains in the order the reader filled it. */
    @Query("SELECT * FROM offline_threads WHERE status IN (0, 1) ORDER BY queuedAtMillis LIMIT 1")
    suspend fun nextQueued(): OfflineThreadEntity?

    @Query("SELECT COUNT(*) FROM offline_threads WHERE status IN (0, 1)")
    suspend fun queuedCount(): Int

    @Query("SELECT postId FROM offline_threads WHERE status = 2")
    suspend fun storedIds(): List<Long>

    /**
     * Threads whose copy is older than [beforeMillis] — what 保留期限 sweeps.
     *
     * Queued and failed rows are excluded by the `downloadedAtMillis` test itself: they have none.
     */
    @Query("SELECT postId FROM offline_threads WHERE downloadedAtMillis IS NOT NULL AND downloadedAtMillis < :beforeMillis")
    suspend fun expiredIds(beforeMillis: Long): List<Long>

    @Upsert
    suspend fun upsert(thread: OfflineThreadEntity)

    @Query("UPDATE offline_threads SET progress = :progress WHERE postId = :postId")
    suspend fun setProgress(
        postId: Long,
        progress: Float?,
    )

    @Query("UPDATE offline_threads SET remoteCommentCount = :count WHERE postId = :postId")
    suspend fun setRemoteCommentCount(
        postId: Long,
        count: Int,
    )

    @Query("DELETE FROM offline_threads WHERE postId = :postId")
    suspend fun delete(postId: Long)

    @Query("DELETE FROM offline_threads")
    suspend fun deleteAll()

    // --- comments ------------------------------------------------------------------------------

    @Query("SELECT * FROM offline_comments WHERE postId = :postId AND page = :page ORDER BY position")
    suspend fun comments(
        postId: Long,
        page: Int,
    ): List<OfflineCommentEntity>

    @Query("SELECT COUNT(*) FROM offline_comments WHERE postId = :postId")
    suspend fun commentCount(postId: Long): Int

    /**
     * What this thread's stored replies weigh, measured on the stored JSON itself.
     *
     * `CAST(... AS BLOB)` is not decoration: `LENGTH` on TEXT counts characters, and a thread
     * written in Chinese would be billed at a third of the bytes it actually occupies.
     *
     * Measured rather than accumulated because a catch-up rewrites the last stored page: adding the
     * new copy's size to a running total would bill that page twice, and subtracting the old one
     * would mean having kept a per-page tally nothing else needs.
     */
    @Query("SELECT IFNULL(SUM(LENGTH(CAST(content AS BLOB))), 0) FROM offline_comments WHERE postId = :postId")
    suspend fun commentBytes(postId: Long): Long

    @Upsert
    suspend fun upsertComments(comments: List<OfflineCommentEntity>)

    @Query("DELETE FROM offline_comments WHERE postId = :postId AND page >= :fromPage")
    suspend fun deleteCommentsFrom(
        postId: Long,
        fromPage: Int,
    )

    // --- images --------------------------------------------------------------------------------

    @Query("SELECT * FROM offline_images WHERE postId = :postId")
    suspend fun images(postId: Long): List<OfflineImageEntity>

    /** Any row for this file, whichever thread owns it — an already-fetched picture is not fetched twice. */
    @Query("SELECT * FROM offline_images WHERE fileName = :fileName LIMIT 1")
    suspend fun imageByFile(fileName: String): OfflineImageEntity?

    @Upsert
    suspend fun upsertImages(images: List<OfflineImageEntity>)

    @Query("DELETE FROM offline_images WHERE postId = :postId")
    suspend fun deleteImagesOf(postId: Long)

    /** Every file still spoken for, so the sweep can delete the ones that are not. */
    @Query("SELECT DISTINCT fileName FROM offline_images")
    suspend fun liveFileNames(): List<String>
}
