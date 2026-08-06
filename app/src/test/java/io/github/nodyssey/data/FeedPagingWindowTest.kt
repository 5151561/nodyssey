package io.github.nodyssey.data

import androidx.paging.AsyncPagingDataDiffer
import androidx.paging.Pager
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * How the feed numbers its rows, across the reloads Room forces while a reader is in it.
 *
 * This guards the oldest regression in the app: opening a thread and coming back landed near the top
 * of the list. It was never navigation, which is why hoisting and saving the `LazyListState` never
 * fixed it. Opening a thread writes its read mark; the feed query joins `post_read_marks`; Room
 * invalidates the paging source, and Paging answers by reloading one window and handing the UI a new
 * list. Whether that costs the reader their place comes down to whether the pager counts the rows
 * outside that window — see [OfflineFirstPostRepository.FEED_PAGING_CONFIG].
 *
 * The source here is a fake that counts, rather than the real Room one, so that the assertions are
 * about the config and not about Room's threading. The config itself is the production object: a
 * `PagingConfig` written out in the test would keep passing after the flag was flipped back, which
 * is the whole failure this file exists to prevent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedPagingWindowTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the feed presents rows no page has loaded yet`() =
        runTest(dispatcher) {
            val differ = presentFeed(rows = 120)

            /*
             * 50 — one initialLoadSize — is what a pager without placeholders reports, and it is the
             * whole bug: the row the reader is on is renumbered every time Room forces a reload, and
             * `LazyColumn`, which can only follow an item that moved a few dozen places, clamps to
             * the end of the window and drops them near the top of the feed.
             */
            assertEquals(120, differ.itemCount)
            assertNull("row 100 should be counted but not loaded", differ.peek(100))
        }

    /** Runs [rows] rows through the pager the app actually builds and returns its presenter. */
    private fun TestScope.presentFeed(rows: Int): AsyncPagingDataDiffer<Int> {
        val differ =
            AsyncPagingDataDiffer(
                diffCallback = IdentityDiff,
                updateCallback = IgnoredUpdates,
                mainDispatcher = dispatcher,
                workerDispatcher = dispatcher,
            )
        val pager =
            Pager(
                config = OfflineFirstPostRepository.FEED_PAGING_CONFIG,
                pagingSourceFactory = { CountingSource(rows) },
            )
        backgroundScope.launch { pager.flow.collectLatest { differ.submitData(it) } }
        return differ
    }
}

/**
 * A page source that knows how many rows it has, exactly as a Room `PagingSource` does.
 *
 * Rows are their own index, so an assertion about numbering reads as one.
 */
private class CountingSource(private val rows: Int) : PagingSource<Int, Int>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Int> {
        val start = (params.key ?: 0).coerceIn(0, rows)
        val end = (start + params.loadSize).coerceAtMost(rows)
        return LoadResult.Page(
            data = (start until end).toList(),
            prevKey = if (start == 0) null else (start - params.loadSize).coerceAtLeast(0),
            nextKey = if (end == rows) null else end,
            itemsBefore = start,
            itemsAfter = rows - end,
        )
    }

    /** The reader's position, which is what a reload has to come back to. */
    override fun getRefreshKey(state: PagingState<Int, Int>): Int? = state.anchorPosition
}

private object IdentityDiff : DiffUtil.ItemCallback<Int>() {
    override fun areItemsTheSame(
        oldItem: Int,
        newItem: Int,
    ) = oldItem == newItem

    override fun areContentsTheSame(
        oldItem: Int,
        newItem: Int,
    ) = oldItem == newItem
}

/** The differ insists on one; nothing here asserts on the change events themselves. */
private object IgnoredUpdates : ListUpdateCallback {
    override fun onInserted(
        position: Int,
        count: Int,
    ) = Unit

    override fun onRemoved(
        position: Int,
        count: Int,
    ) = Unit

    override fun onMoved(
        fromPosition: Int,
        toPosition: Int,
    ) = Unit

    override fun onChanged(
        position: Int,
        count: Int,
        payload: Any?,
    ) = Unit
}
