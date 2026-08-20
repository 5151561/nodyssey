package io.github.nodyssey.data

import io.github.nodyssey.data.local.FeedPositionEntity
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.local.toEntity
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.PostSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Where 首页翻页栏 is told a page begins.
 *
 * The number decides whether stepping a page is a scroll or a fetch, and it has to come from the
 * database rather than from the pager: the feed runs with placeholders on and Room re-windows it on
 * every write, so the rows of the page one step away are placeholders almost all the time. Asking
 * the loaded window called them absent and refetched a page the reader had just scrolled through —
 * on every step, in both directions.
 */
@RunWith(RobolectricTestRunner::class)
class FeedPageRowIndexTest {
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
        blocked: Boolean = false,
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

    /**
     * Three of the site's pages, four rows each, in the order the mediator wrote them.
     *
     * [blockedIds] are stored blocked rather than dropped, which is what the app does with the site's
     * block marks — and what makes the row count and the page boundaries disagree.
     */
    private suspend fun givenPages(blockedIds: Set<Long> = emptySet()) {
        val rows = (1..12).map { it.toLong() }
        database.feedDao().upsertPosts(
            rows.map { summary(it, blocked = it in blockedIds).toEntity(clock.nowMillis()) },
        )
        database.feedDao().insertPositions(
            rows.mapIndexed { index, postId ->
                FeedPositionEntity(
                    feedKey = FRONT_PAGE_FEED_KEY,
                    postId = postId,
                    sortIndex = index,
                    page = index / 4 + 1,
                )
            },
        )
    }

    private suspend fun indexOf(page: Int): Int? =
        repository.feedRowIndexOfPage(categorySlug = null, sort = FeedSort.LAST_REPLY, page = page)

    @Test
    fun `a stored page reports the row it starts at`() =
        runTest {
            givenPages()

            assertEquals(0, indexOf(1))
            assertEquals(4, indexOf(2))
            assertEquals(8, indexOf(3))
        }

    /** The reply to "is this a scroll or a fetch": no rows of it, so there is nothing to scroll to. */
    @Test
    fun `a page the feed does not hold reports nothing`() =
        runTest {
            givenPages()

            assertNull(indexOf(4))
        }

    /**
     * Blocked rows are not in the list, so they are not in the count either. Counting them puts the
     * page two rows further down than it is, which lands the reader mid-page with the bar insisting
     * they arrived.
     */
    @Test
    fun `hidden rows above a page do not count towards where it starts`() =
        runTest {
            givenPages(blockedIds = setOf(2L, 3L))

            assertEquals(2, indexOf(2))
        }

    /** 临时显示被屏蔽内容 puts those rows back in the list, and back in the count with it. */
    @Test
    fun `revealing blocked rows puts them back into the count`() =
        runTest {
            givenPages(blockedIds = setOf(2L, 3L))
            showBlocked.value = true

            assertEquals(4, indexOf(2))
        }

    /**
     * A page whose every row is blocked has nothing on screen to scroll to, so it is a fetch — the
     * count would otherwise name the row after it and read as "already here".
     */
    @Test
    fun `a page with nothing showing reports nothing`() =
        runTest {
            givenPages(blockedIds = setOf(5L, 6L, 7L, 8L))

            assertNull(indexOf(2))
        }
}
