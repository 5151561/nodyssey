package io.github.nodyssey.data

import androidx.paging.PagingSource
import androidx.paging.testing.asSnapshot
import io.github.nodyssey.data.local.FeedPositionEntity
import io.github.nodyssey.data.local.FeedPostRow
import io.github.nodyssey.data.local.FeedRemoteKeyEntity
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.local.toEntity
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.PostSummary
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Read marks and the "N new replies" badge derived from them. */
@RunWith(RobolectricTestRunner::class)
class ReadMarkTest {
    private lateinit var database: NodeSeekDatabase
    private val remote = FakePostRemoteDataSource()
    private val clock = MutableClock()
    private lateinit var repository: OfflineFirstPostRepository

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        repository = OfflineFirstPostRepository(database, remote, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * Puts one post in the front-page feed with the given reply count.
     *
     * The remote key is written too, and deliberately: it marks the feed as freshly refreshed, so
     * reading through [PostRepository.feed] takes the `SKIP_INITIAL_REFRESH` path and the mediator
     * does not clear the fixture out from under the assertions.
     */
    private suspend fun givenPost(
        postId: Long,
        commentCount: Int,
    ) {
        val summary =
            PostSummary(
                postId = postId,
                title = "post $postId",
                authorName = "tester",
                authorUid = 1,
                avatarUrl = null,
                categoryTitle = null,
                categorySlug = null,
                viewCount = 0,
                commentCount = commentCount,
                lastActiveText = null,
                lastActiveTitle = null,
            )
        database.feedDao().upsertPosts(listOf(summary.toEntity(clock.nowMillis())))
        database.feedDao().insertPositions(
            listOf(
                FeedPositionEntity(
                    feedKey = FRONT_PAGE_FEED_KEY,
                    postId = postId,
                    sortIndex = 0,
                ),
            ),
        )
        database.feedDao().upsertRemoteKey(
            FeedRemoteKeyEntity(
                feedKey = FRONT_PAGE_FEED_KEY,
                nextPage = null,
                refreshedAtMillis = clock.nowMillis(),
            ),
        )
    }

    private suspend fun row(postId: Long): FeedPostRow {
        val result =
            database
                .feedDao()
                .pagingSource(FRONT_PAGE_FEED_KEY)
                .load(PagingSource.LoadParams.Refresh(null, 50, false))
        return (result as PagingSource.LoadResult.Page).data.first { it.post.postId == postId }
    }

    private suspend fun feedRows(): List<FeedPost> = repository.feed(null, FeedSort.LAST_REPLY).asSnapshot()

    @Test
    fun `an unopened post has no read state`() =
        runTest {
            givenPost(postId = 7, commentCount = 10)

            val row = row(7)
            assertEquals(null, row.lastReadAtMillis)
            assertEquals(null, row.lastSeenCommentCount)
        }

    @Test
    fun `opening a post records the reply count the list was showing`() =
        runTest {
            givenPost(postId = 7, commentCount = 10)

            repository.markThreadRead(7)

            val mark = requireNotNull(database.readMarkDao().find(7))
            assertEquals(10, mark.lastSeenCommentCount)
            assertEquals(clock.nowMillis(), mark.lastReadAtMillis)
        }

    @Test
    fun `replies arriving after the read are counted as new`() =
        runTest {
            givenPost(postId = 7, commentCount = 10)
            repository.markThreadRead(7)

            // A later refresh brings the post back with more replies.
            givenPost(postId = 7, commentCount = 14)

            val row = row(7)
            assertEquals(10, row.lastSeenCommentCount)
            assertEquals(14, row.post.commentCount)
        }

    /** Re-opening a thread the user had already read to the end must not reset the baseline. */
    @Test
    fun `the seen count never goes backwards`() =
        runTest {
            givenPost(postId = 7, commentCount = 10)
            repository.markThreadRead(7)

            givenPost(postId = 7, commentCount = 4)
            repository.markThreadRead(7)

            assertEquals(10, database.readMarkDao().find(7)?.lastSeenCommentCount)
        }

    @Test
    fun `a deep linked post with no cached row reads as fully unseen`() =
        runTest {
            repository.markThreadRead(999)

            assertEquals(0, database.readMarkDao().find(999)?.lastSeenCommentCount)
        }

    @Test
    fun `the feed exposes read state so the list need not query per row`() =
        runTest {
            givenPost(postId = 7, commentCount = 10)
            repository.markThreadRead(7)
            givenPost(postId = 7, commentCount = 13)

            val feedPost = feedRows().first { it.summary.postId == 7L }

            assertTrue(feedPost.isRead)
            assertEquals(3, feedPost.newCommentCount)
        }

    /** A deleted comment can push the live count below the seen one; that is zero new, not negative. */
    @Test
    fun `a shrinking thread reports no new replies rather than a negative count`() =
        runTest {
            givenPost(postId = 7, commentCount = 10)
            repository.markThreadRead(7)
            givenPost(postId = 7, commentCount = 6)

            val feedPost = feedRows().first { it.summary.postId == 7L }

            assertEquals(0, feedPost.newCommentCount)
            assertTrue(feedPost.isRead)
        }

    @Test
    fun `an unread post reports no new replies even with comments`() =
        runTest {
            givenPost(postId = 8, commentCount = 12)

            val feedPost = feedRows().first { it.summary.postId == 8L }

            assertFalse(feedPost.isRead)
            assertEquals(0, feedPost.newCommentCount)
        }
}
