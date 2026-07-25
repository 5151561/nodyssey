package io.github.nsreader.ui.postlist

import io.github.nsreader.core.net.JsonSource
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.data.CategoryRepository
import io.github.nsreader.data.FakePostRepository
import io.github.nsreader.model.PostListPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakePostRepository()

    /** Fails on purpose, so the repository falls back to the offline board list. */
    private val categoryRepository = CategoryRepository(
        client = object : JsonSource {
            override suspend fun getJson(path: String, referer: String): String =
                throw NodeSeekException(NodeSeekError.Network)
        },
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = PostListViewModel(repository, categoryRepository)

    @Test
    fun `loads the front page on creation`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.posts.size)
        assertEquals(null, state.categorySlug)
        assertFalse(state.isLoading)
        assertEquals(null, state.error)
    }

    @Test
    fun `appends the next page instead of replacing`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.loadNextPage()
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.posts.size)
        assertEquals(2, vm.uiState.value.page)
    }

    /** Regression: a slow response for the previous board must not leak into the new one. */
    @Test
    fun `discards a response that arrives after the board changed`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        repository.listResult = { slug, page ->
            PostListPage(listOf(FakePostRepository.post(slug, page)), page, hasNextPage = true)
        }

        vm.selectCategory("tech")
        // While "tech" is still in flight the user jumps to "trade".
        vm.selectCategory("trade")
        gate.complete(Unit)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("trade", state.categorySlug)
        assertTrue(
            "leaked posts from another board: ${state.posts.map { it.categorySlug }}",
            state.posts.all { it.categorySlug == "trade" },
        )
    }

    @Test
    fun `switching boards clears the previous list`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.loadNextPage()
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.posts.size)

        vm.selectCategory("daily")
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.posts.size)
        assertEquals(1, vm.uiState.value.page)
    }

    @Test
    fun `surfaces a typed error rather than a message string`() = runTest(dispatcher) {
        repository.listError = NodeSeekException(NodeSeekError.LoginRequired)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(NodeSeekError.LoginRequired, vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `an unclassified failure becomes Unknown, not a crash`() = runTest(dispatcher) {
        repository.listError = IllegalStateException("boom")
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(NodeSeekError.Unknown, vm.uiState.value.error)
    }

    /**
     * Cancelling a coroutine throws CancellationException, and `runCatching` catches it like any
     * other failure — so a cancelled load can report an error for a request nobody is waiting on.
     */
    @Test
    fun `cancelling an in-flight load does not surface an error`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        vm.loadNextPage()
        // Let it actually start and park on the gate — otherwise there is nothing to cancel.
        advanceUntilIdle()

        // The user pulls to refresh while that page is still in flight.
        repository.gate = null
        vm.refresh()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.isAppending)
    }

    @Test
    fun `does not start a second page while one is in flight`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val before = repository.requestedSlugs.size

        vm.loadNextPage()
        vm.loadNextPage()
        vm.loadNextPage()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.requestedSlugs.size - before)
    }
}
