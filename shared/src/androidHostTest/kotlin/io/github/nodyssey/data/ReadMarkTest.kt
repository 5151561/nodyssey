package io.github.nodyssey.data

import androidx.paging.testing.asSnapshot
import io.github.nodyssey.data.local.FeedPositionEntity
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

    private suspend fun feedRows(): List<FeedPost> = repository.feed(null, FeedSort.LAST_REPLY).asSnapshot()

    @Test
    fun `a published reply advances only this account's own part of the baseline`() =
        runTest {
            givenPost(postId = 7, commentCount = 10)
            repository.markThreadRead(7)

            repository.noteOwnReplyPublished(7)
            // The next feed refresh includes our floor and one floor written by somebody else.
            givenPost(postId = 7, commentCount = 12)

            val feedPost = feedRows().first { it.summary.postId == 7L }
            assertEquals(11, database.readMarkDao().find(7)?.lastSeenCommentCount)
            assertEquals(1, feedPost.newCommentCount)
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

    /** The same comparison the badge draws, asked as a question the cache window has to answer to. */
    @Test
    fun `a post the list has moved past reports unread replies`() =
        runTest {
            givenPost(postId = 7, commentCount = 10)
            repository.markThreadRead(7)
            givenPost(postId = 7, commentCount = 14)

            assertTrue(repository.hasUnreadReplies(7))
        }

    @Test
    fun `a post read at the count the list still shows reports none`() =
        runTest {
            givenPost(postId = 7, commentCount = 10)
            repository.markThreadRead(7)

            assertFalse(repository.hasUnreadReplies(7))
        }

    @Test
    fun `a thread no feed carries reports none`() =
        runTest {
            repository.markThreadRead(999)

            assertFalse(repository.hasUnreadReplies(999))
        }
}
