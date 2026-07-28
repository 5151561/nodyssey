package io.github.nodyssey.ui.settings

import io.github.nodyssey.data.CommunityRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AboutCommunityViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `successful load publishes the real member count`() =
        runTest(dispatcher) {
            val viewModel = AboutCommunityViewModel(CommunityRepository { 70_123L })

            advanceUntilIdle()

            assertEquals(CommunityStatsUiState.Content(70_123L), viewModel.uiState.value)
        }

    @Test
    fun `load failure publishes a retryable error`() =
        runTest(dispatcher) {
            val viewModel =
                AboutCommunityViewModel(
                    CommunityRepository { throw IllegalStateException("offline") },
                )

            advanceUntilIdle()

            assertSame(CommunityStatsUiState.Error, viewModel.uiState.value)
        }

    @Test
    fun `retry replaces an error with the latest member count`() =
        runTest(dispatcher) {
            var calls = 0
            val viewModel =
                AboutCommunityViewModel(
                    CommunityRepository {
                        calls += 1
                        if (calls == 1) throw IllegalStateException("offline")
                        70_124L
                    },
                )
            advanceUntilIdle()
            assertSame(CommunityStatsUiState.Error, viewModel.uiState.value)

            viewModel.retry()
            advanceUntilIdle()

            assertEquals(CommunityStatsUiState.Content(70_124L), viewModel.uiState.value)
            assertEquals(2, calls)
        }

    @Test
    fun `cancellation is not rendered as a load error`() =
        runTest(dispatcher) {
            val viewModel =
                AboutCommunityViewModel(
                    CommunityRepository { throw CancellationException("screen left") },
                )

            advanceUntilIdle()

            assertSame(CommunityStatsUiState.Loading, viewModel.uiState.value)
        }
}
