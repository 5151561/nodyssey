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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
