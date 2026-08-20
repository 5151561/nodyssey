package io.github.nodyssey.data

import androidx.paging.testing.asSnapshot
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.model.FeedSort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Post search read as a feed.
 *
 * These are the promises the old search pipeline broke, which is why they are pinned here rather
 * than left to the shared feed tests: one request per page, one board sent to the server instead of
 * filtered locally, a cache window that makes coming back free, and a stored search that does not
 * accumulate one entry per query ever typed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SearchFeedTest {
    /*
     * Every other test that drives a real Pager hands this dispatcher to Room as well, and this one
     * has to do the same. Left on Room's own background executors, the write the mediator makes and
     * the invalidation Paging waits for happen on threads the test scheduler does not know about, so
     * `asSnapshot` is free to settle on the still-empty table — an assertion that reads
     * `expected:<[100, 101, 102]> but was:<[]>` and passes on the next run.
     */
    private val dispatcher = StandardTestDispatcher()
    private lateinit var database: NodeSeekDatabase
    private val remote = FakePostRemoteDataSource()
    private val clock = MutableClock()
    private lateinit var repository: OfflineFirstPostRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = inMemoryDatabase(dispatcher)
        repository = OfflineFirstPostRepository(database, remote, clock)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    /** Every fixture below serves a single last page, so one snapshot is the whole result list. */
    private suspend fun search(
        query: String = "android",
        board: String? = null,
        sort: FeedSort = FeedSort.LAST_REPLY,
    ): List<Long> =
        repository
            .searchFeed(query, board, sort)
            .asSnapshot()
            .map { it.summary.postId }

    @Test
    fun `one page of results costs exactly one request`() =
        runTest {
            remote.searchResult = { request ->
                FakePostRemoteDataSource.page(null, request.page, firstId = 100, count = 3, hasNextPage = false)
            }

            assertEquals(listOf(100L, 101L, 102L), search())
            assertEquals(1, remote.searchRequests.size)
        }

    @Test
    fun `the selected board and order go to the server, not to a local filter`() =
        runTest {
            remote.searchResult = { request ->
                FakePostRemoteDataSource.page("tech", request.page, firstId = 700, count = 2, hasNextPage = false)
            }

            search(query = " android ", board = "tech", sort = FeedSort.POST_TIME)

            assertEquals(
                FakePostRemoteDataSource.SearchRequest(
                    query = "android",
                    page = 1,
                    categorySlug = "tech",
                    sort = FeedSort.POST_TIME,
                ),
                remote.searchRequests.single(),
            )
        }

    /** The acceptance criterion the boards already meet: coming back inside the window is free. */
    @Test
    fun `re-reading the same search inside the cache window issues no request`() =
        runTest {
            remote.searchResult = { request ->
                FakePostRemoteDataSource.page(null, request.page, firstId = 100, count = 3, hasNextPage = false)
            }
            search()
            val before = remote.searchRequests.size

            clock.advanceBy(FeedRemoteMediator.CACHE_TTL_MILLIS - 1)

            assertEquals(listOf(100L, 101L, 102L), search())
            assertEquals(before, remote.searchRequests.size)
        }

    @Test
    fun `a different board is a different search rather than an append onto the last one`() =
        runTest {
            remote.searchResult = { request ->
                val first = if (request.categorySlug == "tech") 700L else 100L
                FakePostRemoteDataSource.page(request.categorySlug, request.page, first, count = 2, hasNextPage = false)
            }

            assertEquals(listOf(100L, 101L), search(board = null))
            assertEquals(listOf(700L, 701L), search(board = "tech"))
        }

    /**
     * Board feeds are fifteen and stay; search feeds are one per query typed. Without the sweep the
     * two feed tables would grow with the search history for as long as the app is installed.
     */
    @Test
    fun `running a new search drops the previous one from the cache`() =
        runTest {
            remote.searchResult = { request ->
                FakePostRemoteDataSource.page(null, request.page, firstId = 100, count = 2, hasNextPage = false)
            }
            search(query = "first")
            val firstKey = searchFeedKeyFor("first", null, FeedSort.LAST_REPLY)
            assertEquals(2, database.feedDao().nextSortIndex(firstKey))

            search(query = "second")

            assertEquals(0, database.feedDao().nextSortIndex(firstKey))
            assertEquals(null, database.feedDao().remoteKey(firstKey))
            // The board feeds are untouched by the sweep — only search keys carry the prefix.
            assertTrue(searchFeedKeyFor("second", null, FeedSort.LAST_REPLY).startsWith(SEARCH_FEED_KEY_PREFIX))
        }

    @Test
    fun `an empty query asks the site nothing`() =
        runTest {
            assertEquals(emptyList<Long>(), search(query = "   "))
            assertEquals(0, remote.searchRequests.size)
        }
}
