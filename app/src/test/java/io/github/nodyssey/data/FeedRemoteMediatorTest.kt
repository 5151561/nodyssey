package io.github.nodyssey.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException
import io.github.nodyssey.data.local.FeedPostRow
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.model.FeedSort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The mediator is where phase two's promises actually live, so this is where they are pinned down:
 * a returning user issues no request, appends do not reorder what is on screen, and a refresh does
 * not leak rows.
 */
@OptIn(ExperimentalPagingApi::class)
@RunWith(RobolectricTestRunner::class)
class FeedRemoteMediatorTest {
    private lateinit var database: NodeSeekDatabase
    private val remote = FakePostRemoteDataSource()
    private val clock = MutableClock()

    @Before
    fun setUp() {
        database = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun mediator(
        slug: String? = null,
        startPage: Int = FeedRemoteMediator.FIRST_PAGE,
    ) = FeedRemoteMediator(
        feedKey = feedKeyFor(slug),
        database = database,
        clock = clock,
        startPage = startPage,
    ) { page -> remote.loadList(slug, page, FeedSort.LAST_REPLY) }

    private fun emptyPagingState() =
        PagingState<Int, FeedPostRow>(
            pages = emptyList(),
            anchorPosition = null,
            config = PagingConfig(pageSize = 30),
            leadingPlaceholderCount = 0,
        )

    private suspend fun load(
        loadType: LoadType,
        slug: String? = null,
        startPage: Int = FeedRemoteMediator.FIRST_PAGE,
    ) = mediator(slug, startPage).load(loadType, emptyPagingState())

    private suspend fun storedFeed(slug: String? = null): List<Long> =
        database.feedDao().let { dao ->
            // Reading through the paging source is what the UI does, so assert on the same ordering.
            dao.pagingSource(feedKeyFor(slug)).let { source ->
                val result =
                    source.load(
                        androidx.paging.PagingSource.LoadParams.Refresh(
                            key = null,
                            loadSize = 200,
                            placeholdersEnabled = false,
                        ),
                    )
                (result as androidx.paging.PagingSource.LoadResult.Page).data.map { it.post.postId }
            }
        }

    /** The same rows in the same order, but named by the site page each of them came off. */
    private suspend fun storedPages(slug: String? = null): List<Int?> =
        database.feedDao().let { dao ->
            dao.pagingSource(feedKeyFor(slug)).let { source ->
                val result =
                    source.load(
                        androidx.paging.PagingSource.LoadParams.Refresh(
                            key = null,
                            loadSize = 200,
                            placeholdersEnabled = false,
                        ),
                    )
                (result as androidx.paging.PagingSource.LoadResult.Page).data.map { it.feedPage }
            }
        }

    @Test
    fun `refresh writes page one and records the next page`() =
        runTest {
            remote.listResult = { slug, page ->
                FakePostRemoteDataSource.page(slug, page, firstId = 100, count = 3)
            }

            val result = load(LoadType.REFRESH)

            assertTrue(result is RemoteMediator.MediatorResult.Success)
            assertFalse((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
            assertEquals(listOf(100L, 101L, 102L), storedFeed())
            assertEquals(2, database.feedDao().remoteKey(feedKeyFor(null))?.nextPage)
        }

    @Test
    fun `append adds the following page after the existing rows`() =
        runTest {
            remote.listResult = { slug, page ->
                FakePostRemoteDataSource.page(slug, page, firstId = 100L * page, count = 2)
            }

            load(LoadType.REFRESH)
            load(LoadType.APPEND)

            assertEquals(listOf(100L, 101L, 200L, 201L), storedFeed())
            assertEquals(3, database.feedDao().remoteKey(feedKeyFor(null))?.nextPage)
        }

    /**
     * NodeSeek sorts by last activity, so a post from page 1 routinely reappears on page 2 seconds
     * later. If the append moved it, it would jump out from under the reader's finger.
     */
    @Test
    fun `a post repeated on the next page keeps its original position`() =
        runTest {
            remote.listResult = { slug, page ->
                when (page) {
                    1 -> FakePostRemoteDataSource.page(slug, page, firstId = 100, count = 3)

                    // Page 2 re-serves 102 at its top, then genuinely new rows.
                    else -> FakePostRemoteDataSource.page(slug, page, firstId = 102, count = 3)
                }
            }

            load(LoadType.REFRESH)
            load(LoadType.APPEND)

            assertEquals(listOf(100L, 101L, 102L, 103L, 104L), storedFeed())
        }

    @Test
    fun `refresh replaces the feed instead of appending to it`() =
        runTest {
            remote.listResult = { slug, page ->
                FakePostRemoteDataSource.page(slug, page, firstId = 100, count = 3)
            }
            load(LoadType.REFRESH)

            remote.listResult = { slug, page ->
                FakePostRemoteDataSource.page(slug, page, firstId = 500, count = 2)
            }
            load(LoadType.REFRESH)

            assertEquals(listOf(500L, 501L), storedFeed())
        }

    /** Without the orphan sweep the posts table would grow by a whole page on every refresh. */
    @Test
    fun `refresh drops posts no feed points at any more`() =
        runTest {
            remote.listResult = { slug, page ->
                FakePostRemoteDataSource.page(slug, page, firstId = 100, count = 3)
            }
            load(LoadType.REFRESH)

            remote.listResult = { slug, page ->
                FakePostRemoteDataSource.page(slug, page, firstId = 500, count = 2)
            }
            load(LoadType.REFRESH)

            assertEquals(null, database.feedDao().commentCount(100L))
        }

    /** A read post is history: dropping it would silently mark the thread unread again. */
    @Test
    fun `refresh keeps a post the user has read`() =
        runTest {
            remote.listResult = { slug, page ->
                FakePostRemoteDataSource.page(slug, page, firstId = 100, count = 3)
            }
            load(LoadType.REFRESH)
            database.readMarkDao().markRead(postId = 100L, commentCount = 5, nowMillis = clock.nowMillis())

            remote.listResult = { slug, page ->
                FakePostRemoteDataSource.page(slug, page, firstId = 500, count = 2)
            }
            load(LoadType.REFRESH)

            assertEquals(5, database.readMarkDao().find(100L)?.lastSeenCommentCount)
        }

    @Test
    fun `no next page reports end of pagination and stores no key`() =
        runTest {
            remote.listResult = { slug, page ->
                FakePostRemoteDataSource.page(slug, page, firstId = 100, count = 2, hasNextPage = false)
            }

            val refresh = load(LoadType.REFRESH)

            assertTrue((refresh as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
            assertEquals(null, database.feedDao().remoteKey(feedKeyFor(null))?.nextPage)

            // A subsequent append must not fire a request for a page the site said does not exist.
            val before = remote.listRequests.size
            val append = load(LoadType.APPEND)
            assertTrue((append as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
            assertEquals(before, remote.listRequests.size)
        }

    @Test
    fun `boards keep separate feeds`() =
        runTest {
            remote.listResult = { slug, page ->
                FakePostRemoteDataSource.page(slug, page, firstId = if (slug == "tech") 700 else 100, count = 2)
            }

            load(LoadType.REFRESH, slug = null)
            load(LoadType.REFRESH, slug = "tech")

            assertEquals(listOf(100L, 101L), storedFeed(null))
            assertEquals(listOf(700L, 701L), storedFeed("tech"))
        }

    // -----------------------------------------------------------------------------------------
    // 首页翻页栏: a window that need not start at page 1
    // -----------------------------------------------------------------------------------------

    @Test
    fun `every row records the page it arrived on`() =
        runTest {
            remote.listResult = { slug, page ->
                FakePostRemoteDataSource.page(slug, page, firstId = 100L * page, count = 2)
            }

            load(LoadType.REFRESH)
            load(LoadType.APPEND)

            assertEquals(listOf(1, 1, 2, 2), storedPages())
        }

    @Test
    fun `the pager's own total is stored for the bar to draw`() =
        runTest {
            remote.listResult = { slug, page ->
                FakePostRemoteDataSource.page(slug, page, firstId = 100, count = 2, totalPages = 217)
            }

            load(LoadType.REFRESH)

            assertEquals(217, database.feedDao().remoteKey(feedKeyFor(null))?.totalPages)
        }

    @Test
    fun `a jump refreshes from its own page and appends from there`() =
        runTest {
            remote.listResult = { slug, page ->
                FakePostRemoteDataSource.page(slug, page, firstId = 100L * page, count = 2)
            }

            load(LoadType.REFRESH, startPage = 40)
            load(LoadType.APPEND, startPage = 40)

            assertEquals(listOf(4000L, 4001L, 4100L, 4101L), storedFeed())
            assertEquals(listOf(40, 40, 41, 41), storedPages())
        }

    /**
     * The cache window answers "is this list stale", not "is this the list I asked for". A perfectly
     * fresh page 1 is no answer at all to "show me page 40" — nor is a fresh page 40 to "take me back
     * to the top", which is the direction that used to strand the reader.
     */
    @Test
    fun `a jump refreshes even while the stored feed is fresh`() =
        runTest {
            load(LoadType.REFRESH)
            clock.advanceBy(1000)

            assertEquals(
                RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH,
                mediator(startPage = 40).initialize(),
            )
        }

    @Test
    fun `coming back to page one refreshes rather than keeping the jumped-to window`() =
        runTest {
            load(LoadType.REFRESH, startPage = 40)
            clock.advanceBy(1000)

            assertEquals(
                RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH,
                mediator(startPage = 1).initialize(),
            )
        }

    @Test
    fun `returning to the same jumped-to page issues no request`() =
        runTest {
            load(LoadType.REFRESH, startPage = 40)
            clock.advanceBy(FeedRemoteMediator.CACHE_TTL_MILLIS - 1)

            assertEquals(
                RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH,
                mediator(startPage = 40).initialize(),
            )
        }

    // -----------------------------------------------------------------------------------------
    // Staleness: the acceptance criterion for "returning to the list does not re-request"
    // -----------------------------------------------------------------------------------------

    @Test
    fun `an empty database launches an initial refresh`() =
        runTest {
            assertEquals(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH, mediator().initialize())
        }

    @Test
    fun `a feed inside the cache window skips the initial refresh`() =
        runTest {
            load(LoadType.REFRESH)
            clock.advanceBy(FeedRemoteMediator.CACHE_TTL_MILLIS - 1)

            assertEquals(RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH, mediator().initialize())
        }

    @Test
    fun `a stale feed refreshes again`() =
        runTest {
            load(LoadType.REFRESH)
            clock.advanceBy(FeedRemoteMediator.CACHE_TTL_MILLIS + 1)

            assertEquals(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH, mediator().initialize())
        }

    /**
     * Regression guard: if appending refreshed the timestamp, scrolling a long list would keep pushing
     * the staleness window forward and the feed would never refresh again.
     */
    @Test
    fun `appending does not extend the cache window`() =
        runTest {
            load(LoadType.REFRESH)
            val refreshedAt = database.feedDao().remoteKey(feedKeyFor(null))?.refreshedAtMillis

            clock.advanceBy(1000)
            load(LoadType.APPEND)

            assertEquals(refreshedAt, database.feedDao().remoteKey(feedKeyFor(null))?.refreshedAtMillis)
        }

    // -----------------------------------------------------------------------------------------
    // Failures
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a network failure is reported as an error, not a crash`() =
        runTest {
            remote.listError = NodeSeekException(NodeSeekError.Cloudflare)

            val result = load(LoadType.REFRESH)

            assertTrue(result is RemoteMediator.MediatorResult.Error)
            assertEquals(
                NodeSeekError.Cloudflare,
                ((result as RemoteMediator.MediatorResult.Error).throwable as NodeSeekException).error,
            )
        }

    /**
     * The same bug the ViewModels guard against with `runCatchingExceptCancellation`: cancellation is
     * a throwable, and swallowing it here would render a failure the user never caused.
     */
    @Test
    fun `cancellation propagates instead of becoming an error result`() =
        runTest {
            remote.listError = CancellationException("navigated away")

            var propagated = false
            try {
                load(LoadType.REFRESH)
            } catch (e: CancellationException) {
                propagated = true
            }

            assertTrue("cancellation was swallowed into a MediatorResult", propagated)
        }

    @Test
    fun `a failed refresh leaves the previously cached feed readable`() =
        runTest {
            remote.listResult = { slug, page ->
                FakePostRemoteDataSource.page(slug, page, firstId = 100, count = 3)
            }
            load(LoadType.REFRESH)

            remote.listError = NodeSeekException(NodeSeekError.Network)
            load(LoadType.REFRESH)

            // This is what makes aeroplane mode readable rather than blank.
            assertEquals(listOf(100L, 101L, 102L), storedFeed())
        }
}
