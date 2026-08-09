package io.github.bbs1.ui.home

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.bbs1.data.InstanceRepository
import io.github.bbs1.net.ApiForum
import io.github.bbs1.net.ApiTopicSummary
import io.github.bbs1.net.ApiTopicsPage
import io.github.bbs1.net.Bbs1ApiException
import io.github.bbs1.net.FakeBbs1Api
import io.github.bbs1.ui.common.ApiErrorUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun topic(id: Long, title: String = "t$id") = ApiTopicSummary(id = id, title = title)

    private class Fixture(
        val repository: InstanceRepository,
        val viewModel: HomeViewModel,
        val scope: CoroutineScope,
    )

    private fun TestScope.newFixture(api: FakeBbs1Api): Fixture {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val scope = CoroutineScope(dispatcher + Job())
        val store =
            PreferenceDataStoreFactory.create(scope = scope) {
                File(tmp.root, "instances.preferences_pb")
            }
        var next = 0
        val repository = InstanceRepository(store) { "id-${next++}" }
        val viewModel = HomeViewModel(repository, api)
        // The init collector subscribes eagerly, so tests drive it through repository writes alone.
        return Fixture(repository, viewModel, scope)
    }

    @Test
    fun `loads forums and first page when a site becomes current`() = runTest {
        val api = FakeBbs1Api().apply {
            forumsResult = { listOf(ApiForum(id = 1, name = "水区")) }
            topicsResult = { _, _, _ -> ApiTopicsPage(topics = listOf(topic(7)), hasNextPage = true) }
        }
        val f = newFixture(api)

        f.repository.add("https://bbs1.org", "站")
        advanceUntilIdle()

        val state = f.viewModel.uiState.value
        assertEquals("站", state.instance?.name)
        assertFalse(state.loading)
        assertEquals(listOf(ApiForum(id = 1, name = "水区")), state.forums)
        assertEquals(listOf(7L), state.topics.map { it.id })
        assertTrue(state.hasNextPage)
        f.scope.cancel()
    }

    @Test
    fun `loadMore appends the next page and stops at the last`() = runTest {
        val api = FakeBbs1Api().apply {
            topicsResult = { _, _, page ->
                when (page) {
                    1 -> ApiTopicsPage(topics = listOf(topic(1)), hasNextPage = true)
                    else -> ApiTopicsPage(topics = listOf(topic(2)), page = 2, hasNextPage = false)
                }
            }
        }
        val f = newFixture(api)
        f.repository.add("https://bbs1.org", null)
        advanceUntilIdle()

        f.viewModel.loadMore()
        advanceUntilIdle()

        val state = f.viewModel.uiState.value
        assertEquals(listOf(1L, 2L), state.topics.map { it.id })
        assertFalse(state.hasNextPage)

        f.viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L), f.viewModel.uiState.value.topics.map { it.id })
        f.scope.cancel()
    }

    @Test
    fun `selecting a forum reloads topics with the filter and keeps forums`() = runTest {
        val requestedForums = mutableListOf<Long?>()
        val api = FakeBbs1Api().apply {
            forumsResult = { listOf(ApiForum(id = 3, name = "公告")) }
            topicsResult = { _, forumId, _ ->
                requestedForums += forumId
                ApiTopicsPage(topics = listOf(topic(if (forumId == null) 1 else 30)))
            }
        }
        val f = newFixture(api)
        f.repository.add("https://bbs1.org", null)
        advanceUntilIdle()

        f.viewModel.selectForum(3)
        advanceUntilIdle()

        val state = f.viewModel.uiState.value
        assertEquals(listOf(null, 3L), requestedForums)
        assertEquals(listOf(30L), state.topics.map { it.id })
        assertEquals(3L, state.selectedForumId)
        // forums were fetched once, not per filter
        assertEquals(1, api.calls.count { it == "forums" })
        f.scope.cancel()
    }

    @Test
    fun `a failed first load surfaces the error and retry recovers`() = runTest {
        var fail = true
        val api = FakeBbs1Api().apply {
            topicsResult = { _, _, _ ->
                if (fail) throw Bbs1ApiException.Server("站点已关闭") else ApiTopicsPage(topics = listOf(topic(1)))
            }
        }
        val f = newFixture(api)
        f.repository.add("https://bbs1.org", null)
        advanceUntilIdle()

        assertEquals(ApiErrorUi.Server("站点已关闭"), f.viewModel.uiState.value.error)
        assertTrue(f.viewModel.uiState.value.topics.isEmpty())

        fail = false
        f.viewModel.refresh()
        advanceUntilIdle()

        assertNull(f.viewModel.uiState.value.error)
        assertEquals(listOf(1L), f.viewModel.uiState.value.topics.map { it.id })
        f.scope.cancel()
    }

    @Test
    fun `switching sites swaps the content out`() = runTest {
        val api = FakeBbs1Api().apply {
            topicsResult = { baseUrl, _, _ ->
                ApiTopicsPage(topics = listOf(topic(if ("second" in baseUrl) 2 else 1)))
            }
        }
        val f = newFixture(api)
        f.repository.add("https://first.org", null)
        advanceUntilIdle()
        assertEquals(listOf(1L), f.viewModel.uiState.value.topics.map { it.id })

        f.repository.add("https://second.org", null)
        advanceUntilIdle()

        val state = f.viewModel.uiState.value
        assertEquals("https://second.org", state.instance?.baseUrl)
        assertEquals(listOf(2L), state.topics.map { it.id })
        f.scope.cancel()
    }
}
