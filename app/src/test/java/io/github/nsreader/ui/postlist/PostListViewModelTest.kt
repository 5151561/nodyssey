package io.github.nsreader.ui.postlist

import androidx.paging.testing.asSnapshot
import io.github.nsreader.core.net.JsonSource
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.data.CategoryRepository
import io.github.nsreader.data.FakePostRemoteDataSource
import io.github.nsreader.data.MutableClock
import io.github.nsreader.data.OfflineFirstPostRepository
import io.github.nsreader.data.inMemoryDatabase
import io.github.nsreader.data.local.NodeSeekDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
 * The ViewModel is now a thin state holder: the list belongs to Room, and Paging owns loading and
 * error state. What is left to test is board mirroring, board selection, and that rows really do
 * reach the UI through the database.
 *
 * Three tests that used to live here — "does not start a second page while one is in flight",
 * "discards a response that arrives after the board changed" and "cancelling an in-flight load does
 * not surface an error" — now live in `FeedRemoteMediatorTest`. They were not dropped; the
 * hand-rolled code they guarded was.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PostListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val remote = FakePostRemoteDataSource()
    private val clock = MutableClock()
    private lateinit var database: NodeSeekDatabase

    /** Fails on purpose, so the repository falls back to the offline board list. */
    private val failingJson =
        object : JsonSource {
            override suspend fun getJson(
                path: String,
                referer: String,
            ): String = throw NodeSeekException(NodeSeekError.Network)
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = inMemoryDatabase(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    private fun viewModel() =
        PostListViewModel(
            repository = OfflineFirstPostRepository(database, remote, clock),
            categoryRepository = CategoryRepository(failingJson, database.boardDao(), clock),
        )

    @Test
    fun `starts on the front page with the front page tab selected`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            assertEquals(null, vm.uiState.value.categorySlug)
            assertEquals(0, vm.uiState.value.selectedBoardIndex)
        }

    @Test
    fun `mirrors the board list the repository owns`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            val boards = vm.uiState.value.boards
            assertEquals(CategoryRepository.FRONT_PAGE, boards.first())
            // The API call failed, so this is the offline fallback list — still more than nothing.
            assertTrue("expected fallback boards, got $boards", boards.size > 1)
        }

    @Test
    fun `selecting a board updates the state and the selected tab`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            val target =
                vm.uiState.value.boards
                    .first { it.slug != null }
            vm.selectCategory(target.slug)
            advanceUntilIdle()

            assertEquals(target.slug, vm.uiState.value.categorySlug)
            assertEquals(
                vm.uiState.value.boards
                    .indexOf(target),
                vm.uiState.value.selectedBoardIndex,
            )
        }

    @Test
    fun `re-selecting the current board is a no-op`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            vm.selectCategory("tech")
            advanceUntilIdle()
            val before = vm.uiState.value

            vm.selectCategory("tech")
            advanceUntilIdle()

            // Same instance, so nothing downstream — the pager included — was rebuilt.
            assertTrue(before === vm.uiState.value)
        }

    @Test
    fun `the challenge url follows the selected board`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            assertTrue(vm.challengeUrl().startsWith("https://www.nodeseek.com"))

            vm.selectCategory("tech")
            advanceUntilIdle()

            assertTrue("got ${vm.challengeUrl()}", vm.challengeUrl().contains("tech"))
        }

    /** End to end through Room: the network writes, the pager reads, the rows come out in order. */
    @Test
    fun `posts reach the ui through the database`() =
        runTest(dispatcher) {
            remote.listResult = { slug, page ->
                FakePostRemoteDataSource.page(slug, page, firstId = 10, count = 3, hasNextPage = false)
            }
            val vm = viewModel()
            advanceUntilIdle()

            val titles = vm.feed.asSnapshot().map { it.summary.title }

            assertEquals(listOf("post 10", "post 11", "post 12"), titles)
        }
}
