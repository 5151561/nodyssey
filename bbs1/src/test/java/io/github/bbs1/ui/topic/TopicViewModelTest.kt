package io.github.bbs1.ui.topic

import io.github.bbs1.net.ApiReply
import io.github.bbs1.net.ApiTopicDetail
import io.github.bbs1.net.ApiTopicPage
import io.github.bbs1.net.Bbs1ApiException
import io.github.bbs1.net.FakeBbs1Api
import io.github.bbs1.ui.common.ApiErrorUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TopicViewModelTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun reply(id: Long, floor: Int) = ApiReply(id = id, floor = floor)

    private fun page(vararg replies: ApiReply, page: Int, total: Int) =
        ApiTopicPage(
            topic = ApiTopicDetail(id = 7, title = "标题", body = "正文"),
            replies = replies.toList(),
            page = page,
            pageSize = replies.size.coerceAtLeast(1),
            replyCount = total,
        )

    @Test
    fun `loads the topic and pages replies to the end`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val api = FakeBbs1Api().apply {
            topicResult = { _, _, p ->
                when (p) {
                    1 -> page(reply(1, 1), page = 1, total = 2)
                    else -> page(reply(2, 2), page = 2, total = 2)
                }
            }
        }
        val viewModel = TopicViewModel(api, "https://bbs1.org", 7)
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals("标题", state.topic?.title)
        assertEquals(listOf(1L), state.replies.map { it.id })
        assertTrue(state.hasNextPage)

        viewModel.loadMore()
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(listOf(1L, 2L), state.replies.map { it.id })
        assertFalse(state.hasNextPage)
        assertEquals(listOf("topic(7, p=1)", "topic(7, p=2)"), api.calls)
    }

    @Test
    fun `a failed load shows the error and refresh recovers`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        var fail = true
        val api = FakeBbs1Api().apply {
            topicResult = { _, _, _ ->
                if (fail) throw Bbs1ApiException.Server("你访问的帖子可能已经删除") else page(page = 1, total = 0)
            }
        }
        val viewModel = TopicViewModel(api, "https://bbs1.org", 7)
        advanceUntilIdle()

        assertEquals(ApiErrorUi.Server("你访问的帖子可能已经删除"), viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.topic)

        fail = false
        viewModel.refresh()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertEquals("标题", viewModel.uiState.value.topic?.title)
    }

    @Test
    fun `a failed append keeps the loaded thread and retryAppend resumes`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
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
        val viewModel = TopicViewModel(api, "https://bbs1.org", 7)
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(ApiErrorUi.Network, state.error)
        assertEquals(listOf(1L), state.replies.map { it.id })

        failAppend = false
        viewModel.retryAppend()
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertNull(state.error)
        assertEquals(listOf(1L, 2L), state.replies.map { it.id })
    }
}
