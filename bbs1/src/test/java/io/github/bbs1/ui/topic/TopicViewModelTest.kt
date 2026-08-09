package io.github.bbs1.ui.topic

import io.github.bbs1.data.InstanceRepository
import io.github.bbs1.data.newTestInstanceRepository
import io.github.bbs1.model.InstanceSession
import io.github.bbs1.net.ApiReply
import io.github.bbs1.net.ApiReplyCreated
import io.github.bbs1.net.ApiTopicDetail
import io.github.bbs1.net.ApiTopicPage
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class TopicViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun reply(id: Long, floor: Int) = ApiReply(id = id, floor = floor)

    private fun page(
        vararg replies: ApiReply,
        page: Int,
        total: Int,
        canReply: Boolean = false,
        replyOrder: Int = 0,
    ) = ApiTopicPage(
        topic = ApiTopicDetail(id = 7, title = "标题", body = "正文", replyOrder = replyOrder),
        replies = replies.toList(),
        page = page,
        pageSize = replies.size.coerceAtLeast(1),
        replyCount = total,
        canReply = canReply,
    )

    private class Fixture(
        val repository: InstanceRepository,
        val viewModel: TopicViewModel,
        val scope: CoroutineScope,
    )

    /** A repository holding one site, so the view model has an instance to read a credential from. */
    private suspend fun TestScope.newFixture(api: FakeBbs1Api, session: InstanceSession? = null): Fixture {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val scope = CoroutineScope(dispatcher + Job())
        val repository = newTestInstanceRepository(scope, tmp.root) { "site" }
        repository.add("https://bbs1.org", "站")
        if (session != null) repository.saveSession("site", session)
        val viewModel = TopicViewModel(api, repository, "site", "https://bbs1.org", 7)
        return Fixture(repository, viewModel, scope)
    }

    private fun session(token: String = "t") =
        InstanceSession(token = token, expiresAt = 0, userId = 2, username = "alice")

    @Test
    fun `loads the topic and pages replies to the end`() = runTest {
        val api = FakeBbs1Api().apply {
            topicResult = { _, _, p ->
                when (p) {
                    1 -> page(reply(1, 1), page = 1, total = 2)
                    else -> page(reply(2, 2), page = 2, total = 2)
                }
            }
        }
        val f = newFixture(api)
        advanceUntilIdle()

        var state = f.viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals("标题", state.topic?.title)
        assertEquals(listOf(1L), state.replies.map { it.id })
        assertTrue(state.hasNextPage)

        f.viewModel.loadMore()
        advanceUntilIdle()

        state = f.viewModel.uiState.value
        assertEquals(listOf(1L, 2L), state.replies.map { it.id })
        assertFalse(state.hasNextPage)
        assertEquals(listOf("topic(7, p=1)", "topic(7, p=2)"), api.calls)
        f.scope.cancel()
    }

    @Test
    fun `a failed load shows the error and refresh recovers`() = runTest {
        var fail = true
        val api = FakeBbs1Api().apply {
            topicResult = { _, _, _ ->
                if (fail) throw Bbs1ApiException.Server("你访问的帖子可能已经删除") else page(page = 1, total = 0)
            }
        }
        val f = newFixture(api)
        advanceUntilIdle()

        assertEquals(ApiErrorUi.Server("你访问的帖子可能已经删除"), f.viewModel.uiState.value.error)
        assertNull(f.viewModel.uiState.value.topic)

        fail = false
        f.viewModel.refresh()
        advanceUntilIdle()

        assertNull(f.viewModel.uiState.value.error)
        assertEquals("标题", f.viewModel.uiState.value.topic?.title)
        f.scope.cancel()
    }

    @Test
    fun `a failed append keeps the loaded thread and retryAppend resumes`() = runTest {
        var failAppend = true
        val api = FakeBbs1Api().apply {
            topicResult = { _, _, p ->
                when {
                    p == 1 -> page(reply(1, 1), page = 1, total = 3)
                    failAppend -> throw Bbs1ApiException.Network(java.io.IOException("boom"))
                    else -> page(reply(2, 2), page = 2, total = 3)
                }
            }
        }
        val f = newFixture(api)
        advanceUntilIdle()

        f.viewModel.loadMore()
        advanceUntilIdle()

        var state = f.viewModel.uiState.value
        assertEquals(ApiErrorUi.Network, state.error)
        assertEquals(listOf(1L), state.replies.map { it.id })

        failAppend = false
        f.viewModel.retryAppend()
        advanceUntilIdle()

        state = f.viewModel.uiState.value
        assertNull(state.error)
        assertEquals(listOf(1L, 2L), state.replies.map { it.id })
        f.scope.cancel()
    }

    @Test
    fun `the stored credential rides along and signing in reloads the thread`() = runTest {
        val api = FakeBbs1Api().apply { topicResult = { _, _, _ -> page(page = 1, total = 0, canReply = true) } }
        val f = newFixture(api)
        advanceUntilIdle()

        assertFalse(f.viewModel.uiState.value.signedIn)
        assertEquals(listOf(null), api.tokens)

        f.repository.saveSession("site", session("tok"))
        advanceUntilIdle()

        assertTrue(f.viewModel.uiState.value.signedIn)
        assertTrue(f.viewModel.uiState.value.canReply)
        assertEquals(listOf(null, "tok"), api.tokens)
        f.scope.cancel()
    }

    @Test
    fun `a posted reply lands at the end of an oldest-first thread`() = runTest {
        val api = FakeBbs1Api().apply {
            topicResult = { _, _, _ -> page(reply(1, 1), page = 1, total = 1, canReply = true) }
            createReplyResult = { topicId, body ->
                ApiReplyCreated(replyId = 9, topicId = topicId, reply = ApiReply(id = 9, body = body))
            }
        }
        val f = newFixture(api, session("tok"))
        advanceUntilIdle()

        f.viewModel.submitReply("  新回复  ")
        advanceUntilIdle()

        val state = f.viewModel.uiState.value
        assertEquals(listOf(1L, 9L), state.replies.map { it.id })
        // Floor 2 of 2: the server numbers from the total, and the new reply is the newest.
        assertEquals(listOf(1, 2), state.replies.map { it.floor })
        assertEquals(2, state.replyCount)
        assertEquals("新回复", state.replies.last().body)
        assertTrue(state.replyPosted)
        assertFalse(state.replySubmitting)

        f.viewModel.consumeReplyPosted()
        assertFalse(f.viewModel.uiState.value.replyPosted)
        f.scope.cancel()
    }

    @Test
    fun `a posted reply lands at the top of a newest-first thread`() = runTest {
        val api = FakeBbs1Api().apply {
            topicResult = { _, _, _ ->
                page(reply(1, 1), page = 1, total = 1, canReply = true, replyOrder = 1)
            }
            createReplyResult = { topicId, body ->
                ApiReplyCreated(replyId = 9, topicId = topicId, reply = ApiReply(id = 9, body = body))
            }
        }
        val f = newFixture(api, session("tok"))
        advanceUntilIdle()

        f.viewModel.submitReply("新回复")
        advanceUntilIdle()

        val state = f.viewModel.uiState.value
        assertEquals(listOf(9L, 1L), state.replies.map { it.id })
        assertEquals(listOf(2, 1), state.replies.map { it.floor })
        f.scope.cancel()
    }

    @Test
    fun `a reply the server refuses keeps the draft alive and says why`() = runTest {
        val api = FakeBbs1Api().apply {
            topicResult = { _, _, _ -> page(page = 1, total = 0, canReply = true) }
            createReplyResult = { _, _ -> throw Bbs1ApiException.Server("回复太频繁，请 5 秒后再试") }
        }
        val f = newFixture(api, session("tok"))
        advanceUntilIdle()

        f.viewModel.submitReply("太快了")
        advanceUntilIdle()

        val state = f.viewModel.uiState.value
        assertEquals(ApiErrorUi.Server("回复太频繁，请 5 秒后再试"), state.replyError)
        assertFalse(state.replyPosted)
        assertFalse(state.replySubmitting)
        f.scope.cancel()
    }

    @Test
    fun `a credential the server rejects is dropped rather than retried`() = runTest {
        val api = FakeBbs1Api().apply {
            topicResult = { _, _, _ -> page(page = 1, total = 0, canReply = true) }
            createReplyResult = { _, _ -> throw Bbs1ApiException.Unauthorized("登录凭证无效或已过期") }
        }
        val f = newFixture(api, session("tok"))
        advanceUntilIdle()

        f.viewModel.submitReply("回复")
        advanceUntilIdle()

        assertEquals(ApiErrorUi.Unauthorized, f.viewModel.uiState.value.replyError)
        assertNull(f.repository.session("site").first())
        // And the thread reloaded as an anonymous reader, so the reply bar stops offering the editor.
        assertFalse(f.viewModel.uiState.value.signedIn)
        f.scope.cancel()
    }
}
