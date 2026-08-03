package io.github.nodyssey.data

import androidx.paging.testing.asSnapshot
import io.github.nodyssey.data.local.FeedPositionEntity
import io.github.nodyssey.data.local.FeedRemoteKeyEntity
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.local.toEntity
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.PostSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What the app does with the site's block marks.
 *
 * The marks are stored, not filtered on arrival, so 临时显示被屏蔽内容 can reveal a row without going
 * back to the network. The default is the site's: hidden.
 */
@RunWith(RobolectricTestRunner::class)
class BlockedFeedTest {
    private lateinit var database: NodeSeekDatabase
    private val remote = FakePostRemoteDataSource()
    private val clock = MutableClock()
    private val showBlocked = MutableStateFlow(false)
    private lateinit var repository: OfflineFirstPostRepository

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        repository =
            OfflineFirstPostRepository(
                database = database,
                remote = remote,
                clock = clock,
                showBlockedContent = showBlocked,
            )
    }

    @After
    fun tearDown() = database.close()

    private fun summary(
        postId: Long,
        blocked: Boolean,
    ) = PostSummary(
        postId = postId,
        title = "post $postId",
        authorName = "tester",
        authorUid = 1,
        avatarUrl = null,
        categoryTitle = null,
        categorySlug = null,
        viewCount = 0,
        commentCount = 0,
        lastActiveText = null,
        lastActiveTitle = null,
        isBlocked = blocked,
    )

    /** Two rows in the front-page feed, the first one blocked, and the feed marked fresh. */
    private suspend fun givenFeed() {
        val posts = listOf(summary(1, blocked = true), summary(2, blocked = false))
        database.feedDao().upsertPosts(posts.map { it.toEntity(clock.nowMillis()) })
        database.feedDao().insertPositions(
            posts.mapIndexed { index, post ->
                FeedPositionEntity(FRONT_PAGE_FEED_KEY, post.postId, index)
            },
        )
        database.feedDao().upsertRemoteKey(
            FeedRemoteKeyEntity(FRONT_PAGE_FEED_KEY, nextPage = null, refreshedAtMillis = clock.nowMillis()),
        )
    }

    private suspend fun feedIds(): List<Long> =
        repository.feed(null, FeedSort.LAST_REPLY).asSnapshot().map { it.summary.postId }

    @Test
    fun `a blocked row is kept out of the feed`() =
        runTest {
            givenFeed()

            assertEquals(listOf(2L), feedIds())
        }

    @Test
    fun `revealing shows the same rows without a re-fetch`() =
        runTest {
            givenFeed()
            assertEquals(listOf(2L), feedIds())

            showBlocked.value = true

            assertEquals(listOf(1L, 2L), feedIds())
            // The reveal is a re-query, not a request: the fake data source was never asked at all.
            assertEquals(emptyList<Pair<String?, Int>>(), remote.listRequests)
        }

    @Test
    fun `local search hides blocked rows too`() =
        runTest {
            givenFeed()

            assertEquals(listOf(2L), repository.search("post").first().map { it.summary.postId })

            showBlocked.value = true

            assertEquals(
                listOf(1L, 2L),
                repository.search("post").first().map { it.summary.postId }.sorted(),
            )
        }
}
