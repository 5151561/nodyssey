package io.github.nodyssey.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A feed row joined with the read state the list needs to dim it or badge it.
 *
 * Read marks live in their own table, so this is a `LEFT JOIN` and both columns are nullable: null
 * means never opened.
 */
data class FeedPostRow(
    @Embedded val post: PostEntity,
    val lastReadAtMillis: Long?,
    val lastSeenCommentCount: Int?,
)

@Dao
interface FeedDao {
    /**
     * The list's single source of truth.
     *
     * Returning a [PagingSource] rather than a `Flow<List<…>>` is what makes the database — not the
     * network — the thing the UI reads from. Room invalidates it on any write to the joined tables,
     * so a mediator writing page 3 shows up without anyone telling the UI to reload.
     */
    @Query(
        """
        SELECT p.*, r.lastReadAtMillis AS lastReadAtMillis, r.lastSeenCommentCount AS lastSeenCommentCount
        FROM posts p
        INNER JOIN feed_positions f ON f.postId = p.postId
        LEFT JOIN post_read_marks r ON r.postId = p.postId
        WHERE f.feedKey = :feedKey
        ORDER BY f.sortIndex ASC
        """,
    )
    fun pagingSource(feedKey: String): PagingSource<Int, FeedPostRow>

    /**
     * Searches the posts that have already reached the offline cache.
     *
     * Search is deliberately local-first: NodeSeek has no stable public search API, while this table
     * already contains the threads the reader is most likely trying to find again. [query] is escaped
     * by the repository before it reaches LIKE, so `%` and `_` remain ordinary search characters.
     */
    @Query(
        """
        SELECT p.*, r.lastReadAtMillis AS lastReadAtMillis, r.lastSeenCommentCount AS lastSeenCommentCount
        FROM posts p
        LEFT JOIN post_read_marks r ON r.postId = p.postId
        WHERE p.title LIKE '%' || :query || '%' ESCAPE '\'
           OR p.authorName LIKE '%' || :query || '%' ESCAPE '\'
        ORDER BY p.lastActiveText IS NULL, p.postId DESC
        LIMIT :limit
        """,
    )
    fun search(query: String, limit: Int = 100): Flow<List<FeedPostRow>>

    @Upsert
    suspend fun upsertPosts(posts: List<PostEntity>)

    /**
     * [OnConflictStrategy.IGNORE] on purpose: NodeSeek sorts by last activity, so a post from page 1
     * frequently reappears on page 2 a moment later. Replacing would move it to the bottom under the
     * user's finger. Keeping the first position it was seen at makes appends visually stable.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPositions(positions: List<FeedPositionEntity>)

    @Query("SELECT COALESCE(MAX(sortIndex) + 1, 0) FROM feed_positions WHERE feedKey = :feedKey")
    suspend fun nextSortIndex(feedKey: String): Int

    /**
     * The reply count the list is currently showing for this post.
     *
     * This is the right baseline for "read up to here": it is the same number the badge will later be
     * compared against. Counting how many comments happened to be downloaded would compare a page
     * against a total and report new replies that were only ever unfetched ones.
     */
    @Query("SELECT commentCount FROM posts WHERE postId = :postId")
    suspend fun commentCount(postId: Long): Int?

    @Query("DELETE FROM feed_positions WHERE feedKey = :feedKey")
    suspend fun clearFeed(feedKey: String)

    @Query("SELECT * FROM feed_remote_keys WHERE feedKey = :feedKey")
    suspend fun remoteKey(feedKey: String): FeedRemoteKeyEntity?

    @Upsert
    suspend fun upsertRemoteKey(key: FeedRemoteKeyEntity)

    /**
     * Makes every stored feed count as stale without deleting a row.
     *
     * Rows are kept on purpose: the stale list still paints on the first frame while the refresh runs,
     * which is the offline-first behaviour. Only the *freshness* claim is withdrawn, so the mediator
     * stops answering `SKIP_INITIAL_REFRESH`.
     */
    @Query("UPDATE feed_remote_keys SET refreshedAtMillis = 0")
    suspend fun expireAllFeeds()

    @Query("DELETE FROM feed_positions")
    suspend fun clearAllFeedPositions()

    @Query("DELETE FROM feed_remote_keys")
    suspend fun clearAllRemoteKeys()

    @Query("DELETE FROM posts")
    suspend fun clearAllPosts()

    /**
     * Drops posts that no feed points at any more and that nobody has read.
     *
     * Without this the `posts` table only ever grows: refreshing a feed clears its positions but
     * leaves the rows behind. Read posts are kept regardless, because their read mark is the thing
     * that makes "4 new replies" work and losing it would silently mark old threads unread again.
     */
    @Query(
        """
        DELETE FROM posts
        WHERE postId NOT IN (SELECT postId FROM feed_positions)
          AND postId NOT IN (SELECT postId FROM post_read_marks)
        """,
    )
    suspend fun deleteOrphanedPosts()
}
