package io.github.nsreader.ui.account

import io.github.nsreader.core.net.JsonSource
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.data.CategoryRepository
import io.github.nsreader.data.MutableClock
import io.github.nsreader.data.inMemoryDatabase
import io.github.nsreader.data.local.NodeSeekDatabase
import io.github.nsreader.data.settings.SettingsRepository
import io.github.nsreader.data.testSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 首页版块 is the one part of 账号设置 that writes something real, so its storage contract is the part
 * worth pinning down: "everything" must never be stored as a list of today's slugs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeBoardsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = MutableClock()
    private lateinit var database: NodeSeekDatabase

    /** No network in these tests: [CategoryRepository] falls back to its static board list. */
    private val failingJson =
        object : JsonSource {
            override suspend fun getJson(path: String, referer: String): String =
                throw NodeSeekException(NodeSeekError.Network)
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = inMemoryDatabase(dispatcher)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(settings: SettingsRepository) =
        HomeBoardsViewModel(
            settings = settings,
            categories = CategoryRepository(failingJson, database.boardDao(), clock),
        )

    /**
     * `advanceUntilIdle` cannot wait for a save.
     *
     * DataStore does its file write on a real `Dispatchers.IO` that the test scheduler does not own,
     * so the scheduler goes idle while the write is still in flight and an assertion placed after
     * `advanceUntilIdle()` reads the store before it has been touched. Waiting for the `saved` flag
     * waits for the effect itself, which is the thing the test is actually about.
     */
    private suspend fun TestScope.saveAndAwait(viewModel: HomeBoardsViewModel) {
        viewModel.save()
        advanceUntilIdle()
        withTimeout(SAVE_TIMEOUT_MILLIS) { viewModel.uiState.first { it.saved } }
    }

    @Test
    fun `opens with every board ticked when no preference is stored`() =
        runTest(dispatcher) {
            val vm = viewModel(testSettingsRepository(backgroundScope))
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.boards.isNotEmpty())
            assertEquals(state.boards.mapNotNull { it.slug }.toSet(), state.selected)
        }

    @Test
    fun `saving every board stores no restriction at all`() =
        runTest(dispatcher) {
            val settings = testSettingsRepository(backgroundScope)
            val vm = viewModel(settings)
            advanceUntilIdle()

            saveAndAwait(vm)

            // Storing the full list would pin the strip to today's boards and hide any added later.
            assertEquals(emptySet<String>(), settings.settings.first().homeBoards)
        }

    @Test
    fun `saving a subset stores exactly that subset`() =
        runTest(dispatcher) {
            val settings = testSettingsRepository(backgroundScope)
            val vm = viewModel(settings)
            advanceUntilIdle()

            vm.uiState.value.boards.mapNotNull { it.slug }.drop(2).forEach(vm::toggle)
            val kept = vm.uiState.value.selected
            saveAndAwait(vm)

            assertEquals(kept, settings.settings.first().homeBoards)
        }

    @Test
    fun `the last board cannot be unticked`() =
        runTest(dispatcher) {
            val vm = viewModel(testSettingsRepository(backgroundScope))
            advanceUntilIdle()

            val slugs = vm.uiState.value.boards.mapNotNull { it.slug }
            slugs.forEach(vm::toggle)

            assertEquals(setOf(slugs.last()), vm.uiState.value.selected)
            assertTrue(vm.uiState.value.message is AccountMessage.Info)
        }

    @Test
    fun `reset returns to every board`() =
        runTest(dispatcher) {
            val vm = viewModel(testSettingsRepository(backgroundScope))
            advanceUntilIdle()

            vm.uiState.value.boards.mapNotNull { it.slug }.drop(1).forEach(vm::toggle)
            vm.reset()

            assertEquals(
                vm.uiState.value.boards.mapNotNull { it.slug }.toSet(),
                vm.uiState.value.selected,
            )
        }

    @Test
    fun `a stored preference survives a reopen`() =
        runTest(dispatcher) {
            val settings = testSettingsRepository(backgroundScope)
            val first = viewModel(settings)
            advanceUntilIdle()
            first.uiState.value.boards.mapNotNull { it.slug }.drop(3).forEach(first::toggle)
            val expected = first.uiState.value.selected
            saveAndAwait(first)

            val second = viewModel(settings)
            advanceUntilIdle()

            assertEquals(expected, second.uiState.value.selected)
        }

    private companion object {
        /** Real milliseconds: the store's write is on a real dispatcher, not the test scheduler. */
        const val SAVE_TIMEOUT_MILLIS = 5_000L
    }
}
