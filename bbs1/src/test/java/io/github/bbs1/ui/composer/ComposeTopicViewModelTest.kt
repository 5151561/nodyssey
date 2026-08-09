package io.github.bbs1.ui.composer

import io.github.bbs1.data.InstanceRepository
import io.github.bbs1.data.newTestInstanceRepository
import io.github.bbs1.model.InstanceSession
import io.github.bbs1.net.ApiForum
import io.github.bbs1.net.ApiTopicCreated
import io.github.bbs1.net.Bbs1ApiException
import io.github.bbs1.net.FakeBbs1Api
import io.github.bbs1.ui.common.ApiErrorUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ComposeTopicViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Fixture(
        val repository: InstanceRepository,
        val viewModel: ComposeTopicViewModel,
        val scope: CoroutineScope,
    )

    private suspend fun TestScope.newFixture(api: FakeBbs1Api, preferredForumId: Long? = null): Fixture {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val scope = CoroutineScope(dispatcher + Job())
        val repository = newTestInstanceRepository(scope, tmp.root) { "site" }
        repository.add("https://bbs1.org", "站")
        repository.saveSession(
            "site",
            InstanceSession(token = "tok", expiresAt = 0, userId = 2, username = "alice"),
        )
        val viewModel =
            ComposeTopicViewModel(api, repository, "site", "https://bbs1.org", preferredForumId)
        return Fixture(repository, viewModel, scope)
    }

    private fun forums() =
        listOf(
            ApiForum(id = 1, name = "公告", canPost = false, canReply = true),
            ApiForum(id = 2, name = "水区", canPost = true, canReply = true),
            ApiForum(id = 3, name = "技术", canPost = true, canReply = true),
        )

    @Test
    fun `the picker offers only the boards this account may post in`() = runTest {
        val api = FakeBbs1Api().apply { forumsResult = { forums() } }
        val f = newFixture(api)
        advanceUntilIdle()

        val state = f.viewModel.uiState.value
        assertEquals(listOf(2L, 3L), state.forums.map { it.id })
        assertEquals(2L, state.selectedForumId)
        assertEquals(listOf("tok"), api.tokens)
        f.scope.cancel()
    }

    @Test
    fun `the board the feed was filtered to starts selected when it is postable`() = runTest {
        val api = FakeBbs1Api().apply { forumsResult = { forums() } }
        val f = newFixture(api, preferredForumId = 3)
        advanceUntilIdle()

        assertEquals(3L, f.viewModel.uiState.value.selectedForumId)
        f.scope.cancel()
    }

    @Test
    fun `a filter the account cannot post in falls back to the first one it can`() = runTest {
        val api = FakeBbs1Api().apply { forumsResult = { forums() } }
        val f = newFixture(api, preferredForumId = 1)
        advanceUntilIdle()

        assertEquals(2L, f.viewModel.uiState.value.selectedForumId)
        f.scope.cancel()
    }

    @Test
    fun `publishing sends the trimmed post and answers with the new thread`() = runTest {
        val api = FakeBbs1Api().apply {
            forumsResult = { forums() }
            createTopicResult = { _, _, _ -> ApiTopicCreated(topicId = 42) }
        }
        val f = newFixture(api)
        advanceUntilIdle()

        f.viewModel.selectForum(3)
        f.viewModel.submit("  标题  ", "  正文  ")
        advanceUntilIdle()

        assertEquals(42L, f.viewModel.uiState.value.createdTopicId)
        assertEquals("createTopic(3, 标题)", api.calls.last())
        f.scope.cancel()
    }

    @Test
    fun `a refused post keeps the composer open with the reason`() = runTest {
        val api = FakeBbs1Api().apply {
            forumsResult = { forums() }
            createTopicResult = { _, _, _ -> throw Bbs1ApiException.Server("标题太长") }
        }
        val f = newFixture(api)
        advanceUntilIdle()

        f.viewModel.submit("标题", "正文")
        advanceUntilIdle()

        val state = f.viewModel.uiState.value
        assertEquals(ApiErrorUi.Server("标题太长"), state.error)
        assertNull(state.createdTopicId)
        f.scope.cancel()
    }

    @Test
    fun `a credential the server rejects while publishing is dropped`() = runTest {
        val api = FakeBbs1Api().apply {
            forumsResult = { forums() }
            createTopicResult = { _, _, _ -> throw Bbs1ApiException.Unauthorized("登录凭证无效或已过期") }
        }
        val f = newFixture(api)
        advanceUntilIdle()

        f.viewModel.submit("标题", "正文")
        advanceUntilIdle()

        assertEquals(ApiErrorUi.Unauthorized, f.viewModel.uiState.value.error)
        assertNull(f.repository.session("site").first())
        f.scope.cancel()
    }
}
