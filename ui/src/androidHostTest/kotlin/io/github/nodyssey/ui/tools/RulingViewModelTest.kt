package io.github.nodyssey.ui.tools

import io.github.nodyssey.data.Board
import io.github.nodyssey.data.RulingKind
import io.github.nodyssey.data.RulingPage
import io.github.nodyssey.data.RulingRecord
import io.github.nodyssey.data.RulingRepository
import io.github.nodyssey.data.RulingTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The two halves of 管理记录's paging, which behave differently on purpose.
 *
 * **Appending** extends the loaded slice by exactly one adjoining page and keeps everything already
 * on screen. **Jumping** anywhere else replaces the slice with the target page alone — page 60 is one
 * request, not fifty-nine, and that is the whole reason the window has a first *and* a last page
 * rather than being a prefix of the log.
 *
 * The third thing pinned here is that a page already in the window never re-fetches: it becomes a
 * scroll, because the rows are already there.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RulingViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(repository: RulingRepository = FakeRulingRepository()) =
        RulingViewModel(repository, flowOf(emptyList()))

    @Test
    fun `starts on page one`() =
        runTest(dispatcher) {
            val repository = FakeRulingRepository()

            val state = viewModel(repository).also { advanceUntilIdle() }.uiState.value

            assertEquals(listOf(1), repository.requestedPages)
            assertEquals(20, state.records.size)
            assertEquals(1, state.firstLoadedPage)
            assertEquals(1, state.lastLoadedPage)
            assertTrue(state.hasNextPage)
        }

    @Test
    fun `appends the next page onto the tail rather than replacing the list`() =
        runTest(dispatcher) {
            val repository = FakeRulingRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.loadNextPage()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf(1, 2), repository.requestedPages)
            assertEquals(40, state.records.size)
            // The page column is what the toolbar reads to say which page is on screen.
            assertEquals(List(20) { 1 } + List(20) { 2 }, state.recordPages)
            assertEquals(1, state.firstLoadedPage)
            assertEquals(2, state.lastLoadedPage)
        }

    /**
     * The screen asks on every scroll frame near the foot of the list. Without the guard that is one
     * request per frame, all for the same page.
     */
    @Test
    fun `collapses a burst of append requests into one`() =
        runTest(dispatcher) {
            val repository = FakeRulingRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            repeat(5) { viewModel.loadNextPage() }
            advanceUntilIdle()

            assertEquals(listOf(1, 2), repository.requestedPages)
        }

    @Test
    fun `stops appending at the last page`() =
        runTest(dispatcher) {
            val repository = FakeRulingRepository(totalPages = 1)
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.loadNextPage()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.hasNextPage)
            assertEquals(listOf(1), repository.requestedPages)
        }

    /** A jump is one request. Walking there would be fifty-nine, and the site rate-limits. */
    @Test
    fun `jumps straight to a distant page and replaces the window`() =
        runTest(dispatcher) {
            val repository = FakeRulingRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.loadPage(60)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf(1, 60), repository.requestedPages)
            assertEquals(20, state.records.size)
            assertEquals(60, state.firstLoadedPage)
            assertEquals(60, state.lastLoadedPage)
            assertEquals(60, state.pendingScroll)
        }

    /** The page after the slice is where the reader was heading anyway, so it joins instead. */
    @Test
    fun `treats a jump to the adjoining page as an append`() =
        runTest(dispatcher) {
            val repository = FakeRulingRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.loadPage(2)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(40, state.records.size)
            assertEquals(1, state.firstLoadedPage)
            assertEquals(2, state.lastLoadedPage)
        }

    @Test
    fun `scrolls to a page already loaded instead of fetching it again`() =
        runTest(dispatcher) {
            val repository = FakeRulingRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()
            viewModel.loadNextPage()
            advanceUntilIdle()

            viewModel.loadPage(1)
            advanceUntilIdle()

            assertEquals(listOf(1, 2), repository.requestedPages)
            assertEquals(1, viewModel.uiState.value.pendingScroll)
            assertEquals(40, viewModel.uiState.value.records.size)
        }

    @Test
    fun `clears the pending scroll once the screen reports it handled`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.loadPage(60)
            advanceUntilIdle()

            viewModel.onScrollHandled()

            assertNull(viewModel.uiState.value.pendingScroll)
        }

    @Test
    fun `clamps a jump past the last page`() =
        runTest(dispatcher) {
            val repository = FakeRulingRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.loadPage(9_999)
            advanceUntilIdle()

            assertEquals(listOf(1, 100), repository.requestedPages)
        }

    /** An append that fails must not blank the pages already read. */
    @Test
    fun `keeps the loaded rows when an append fails`() =
        runTest(dispatcher) {
            val repository = FakeRulingRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()
            repository.failNext = true

            viewModel.loadNextPage()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(20, state.records.size)
            assertFalse(state.isAppending)
            assertEquals(1, state.lastLoadedPage)
        }

    /** Retrying re-asks for the page that failed, not the one still on screen from the last success. */
    @Test
    fun `retries the page that failed`() =
        runTest(dispatcher) {
            val repository = FakeRulingRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()
            repository.failNext = true
            viewModel.loadPage(60)
            advanceUntilIdle()

            viewModel.retry()
            advanceUntilIdle()

            assertEquals(listOf(1, 60, 60), repository.requestedPages)
            assertEquals(60, viewModel.uiState.value.firstLoadedPage)
        }

    /** Board titles are the category repository's; this screen only observes them. */
    @Test
    fun `lets an observed board title win over the built-in table`() =
        runTest(dispatcher) {
            val renamed = listOf(Board(slug = "daily", title = "日常闲聊", description = null))

            val viewModel = RulingViewModel(FakeRulingRepository(), flowOf(renamed))
            advanceUntilIdle()

            val titles = viewModel.uiState.value.boardTitles
            assertEquals("日常闲聊", titles["daily"])
            // Boards the endpoint omits still resolve, so an old row never shows a raw slug.
            assertEquals("无意义", titles["meaningless"])
        }
}

private class FakeRulingRepository(
    private val totalPages: Int = 100,
    private val pageSize: Int = 20,
) : RulingRepository {
    val requestedPages = mutableListOf<Int>()
    var failNext = false

    override suspend fun records(page: Int): RulingPage {
        requestedPages += page
        if (failNext) {
            failNext = false
            throw IllegalStateException("boom")
        }
        // Ids descend across pages exactly as the site's do, so a duplicated append would show up as
        // a duplicate list key rather than as a silently longer list.
        val top = 10_000L - (page - 1) * pageSize
        return RulingPage(
            records = List(pageSize) { index -> record(top - index) },
            page = page,
            totalPages = totalPages,
        )
    }

    private fun record(id: Long) =
        RulingRecord(
            id = id,
            targetName = "member$id",
            targetUid = id,
            target = RulingTarget.POST,
            postId = id,
            floor = null,
            reason = null,
            actions = emptyList(),
            moderatorName = "xe",
            createdAtMillis = null,
            kind = RulingKind.PENALTY,
        )
}
