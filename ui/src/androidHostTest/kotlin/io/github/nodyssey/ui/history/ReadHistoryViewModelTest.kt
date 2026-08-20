package io.github.nodyssey.ui.history

import io.github.nodyssey.data.FakePostRemoteDataSource
import io.github.nodyssey.data.MutableClock
import io.github.nodyssey.data.OfflineFirstPostRepository
import io.github.nodyssey.data.inMemoryDatabase
import io.github.nodyssey.data.local.FeedPositionEntity
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.local.toEntity
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.testSettingsRepository
import io.github.nodyssey.model.PostSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReadHistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val remote = FakePostRemoteDataSource()
    private val clock = MutableClock()
    private val limit = MutableStateFlow(SettingsRepository.DEFAULT_READ_HISTORY_LIMIT)
    private lateinit var database: NodeSeekDatabase
    private lateinit var repository: OfflineFirstPostRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = inMemoryDatabase(dispatcher)
        repository = OfflineFirstPostRepository(database, remote, clock, readHistoryLimit = limit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    private suspend fun givenRead(
        postId: Long,
        title: String,
    ) {
        val summary =
            PostSummary(
                postId = postId,
                title = title,
                authorName = "tester",
                authorUid = 1,
                avatarUrl = null,
                categoryTitle = "日常",
                categorySlug = "daily",
                viewCount = 0,
                commentCount = 3,
                lastActiveText = null,
                lastActiveTitle = null,
            )
        database.feedDao().upsertPosts(listOf(summary.toEntity(clock.nowMillis())))
        database.feedDao().insertPositions(
            listOf(FeedPositionEntity(feedKey = "", postId = postId, sortIndex = 0)),
        )
        repository.markThreadRead(postId)
        clock.advanceBy(1_000)
    }

    /**
     * The screen must not draw its empty state on the first frame. An empty list before the database
     * has answered is "we have not looked yet", and telling the reader they have read nothing when
     * they have is worse than a moment of spinner.
     */
    @Test
    fun `starts loading rather than empty`() =
        runTest(dispatcher) {
            val vm = ReadHistoryViewModel(repository, testSettingsRepository(backgroundScope))

            assertTrue(vm.uiState.value.isLoading)
            assertTrue(vm.uiState.value.entries.isEmpty())
        }

    @Test
    fun `emits the history newest first once the database answers`() =
        runTest(dispatcher) {
            givenRead(1, "first")
            givenRead(2, "second")

            val vm = ReadHistoryViewModel(repository, testSettingsRepository(backgroundScope))
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isLoading)
            assertEquals(listOf(2L, 1L), vm.uiState.value.entries.map { it.postId })
        }

    @Test
    fun `removing an entry drops it from the state`() =
        runTest(dispatcher) {
            givenRead(1, "first")
            givenRead(2, "second")
            val vm = ReadHistoryViewModel(repository, testSettingsRepository(backgroundScope))
            advanceUntilIdle()

            vm.remove(vm.uiState.value.entries.first { it.postId == 2L })
            advanceUntilIdle()

            assertEquals(listOf(1L), vm.uiState.value.entries.map { it.postId })
        }

    /** 撤销 on the snackbar. The row comes back where it was, not at the top. */
    @Test
    fun `a removed entry can be restored`() =
        runTest(dispatcher) {
            givenRead(1, "first")
            givenRead(2, "second")
            val vm = ReadHistoryViewModel(repository, testSettingsRepository(backgroundScope))
            advanceUntilIdle()
            val removed = vm.uiState.value.entries.first { it.postId == 2L }

            vm.remove(removed)
            advanceUntilIdle()
            vm.restore(removed)
            advanceUntilIdle()

            assertEquals(listOf(2L, 1L), vm.uiState.value.entries.map { it.postId })
        }

    @Test
    fun `clearing empties the state`() =
        runTest(dispatcher) {
            givenRead(1, "first")
            val vm = ReadHistoryViewModel(repository, testSettingsRepository(backgroundScope))
            advanceUntilIdle()

            vm.clear()
            advanceUntilIdle()

            assertTrue(vm.uiState.value.entries.isEmpty())
            assertFalse(vm.uiState.value.isLoading)
        }

    /** The picker writes the setting, and the list it is looking at re-lengths from the same store. */
    @Test
    fun `choosing a limit stores it and comes back through the state`() =
        runTest(dispatcher) {
            val settings = testSettingsRepository(backgroundScope)
            repository =
                OfflineFirstPostRepository(
                    database,
                    remote,
                    clock,
                    readHistoryLimit = settings.settings.map { it.readHistoryLimit },
                )
            val vm = ReadHistoryViewModel(repository, settings)
            advanceUntilIdle()
            assertEquals(SettingsRepository.DEFAULT_READ_HISTORY_LIMIT, vm.uiState.value.limit)

            vm.setLimit(100)
            advanceUntilIdle()

            assertEquals(100, settings.settings.first().readHistoryLimit)
            assertEquals(100, vm.uiState.value.limit)
        }
}
