package io.github.nsreader.ui.composer

import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.data.Board
import io.github.nsreader.data.MutableClock
import io.github.nsreader.data.composer.PostComposerRepository
import io.github.nsreader.data.composer.PostDraft
import io.github.nsreader.data.composer.PostSubmission
import io.github.nsreader.data.session.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostComposerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeComposerRepository()
    private val clock = MutableClock()
    private val boards = MutableStateFlow(listOf(Board("tech", "技术", null)))
    private val session = MutableStateFlow(SessionState(isSignedIn = true))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = PostComposerViewModel(repository, boards, session, clock)

    @Test
    fun `offers a saved draft and restores every field`() = runTest(dispatcher) {
        repository.draftState.value =
            PostDraft(
                title = "旧标题",
                body = "旧正文",
                boardSlug = "tech",
                boardTitle = "技术",
                savedAtMillis = 123L,
            )
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals("旧标题", viewModel.uiState.value.pendingDraft?.title)

        viewModel.continueDraft()

        assertEquals("旧标题", viewModel.uiState.value.title)
        assertEquals("旧正文", viewModel.uiState.value.body)
        assertEquals("tech", viewModel.uiState.value.boardSlug)
        assertTrue(viewModel.uiState.value.showRuleReminder)
    }

    @Test
    fun `editing autosaves without blocking each keystroke`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.updateTitle("新标题")
        viewModel.updateBody("正文")
        advanceTimeBy(749)
        assertNull(repository.savedDraft)

        advanceTimeBy(2)
        advanceUntilIdle()

        assertEquals("新标题", repository.savedDraft?.title)
        assertEquals("正文", repository.savedDraft?.body)
        assertEquals(clock.nowMillis(), viewModel.uiState.value.savedAtMillis)
    }

    @Test
    fun `publish failure keeps the draft and exposes a retryable error`() = runTest(dispatcher) {
        repository.publishError = NodeSeekException(NodeSeekError.Network)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.updateTitle("标题")
        viewModel.updateBody("正文")
        viewModel.selectBoard(boards.value.single())

        viewModel.publish { error("must not publish") }
        advanceUntilIdle()

        assertEquals(NodeSeekError.Network, viewModel.uiState.value.publishError)
        assertFalse(viewModel.uiState.value.isPublishing)
        assertEquals(0, repository.deleteCount)
        assertEquals("正文", viewModel.uiState.value.body)
    }

    @Test
    fun `successful retry clears the draft and returns the post id`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.updateTitle("标题")
        viewModel.updateBody("正文")
        viewModel.selectBoard(boards.value.single())
        var publishedId: Long? = null

        viewModel.publish { publishedId = it }
        advanceUntilIdle()

        assertEquals(456L, publishedId)
        assertEquals(1, repository.deleteCount)
        assertEquals("tech", repository.submission?.boardSlug)
    }

    @Test
    fun `success without an id clears the draft instead of offering a duplicate retry`() = runTest(dispatcher) {
        repository.publishedId = null
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.updateTitle("标题")
        viewModel.updateBody("正文")
        viewModel.selectBoard(boards.value.single())
        var callbackInvoked = false

        viewModel.publish { callbackInvoked = true }
        advanceUntilIdle()

        assertTrue(callbackInvoked)
        assertEquals(1, repository.deleteCount)
        assertNull(viewModel.uiState.value.publishError)
    }
}

private class FakeComposerRepository : PostComposerRepository {
    val draftState = MutableStateFlow<PostDraft?>(null)
    override val draft: Flow<PostDraft?> = draftState
    var savedDraft: PostDraft? = null
    var submission: PostSubmission? = null
    var publishError: Throwable? = null
    var publishedId: Long? = 456L
    var deleteCount = 0

    override suspend fun saveDraft(draft: PostDraft) {
        savedDraft = draft
        draftState.value = draft
    }

    override suspend fun deleteDraft() {
        deleteCount++
        draftState.value = null
    }

    override suspend fun publish(submission: PostSubmission): Long? {
        this.submission = submission
        publishError?.let { throw it }
        return publishedId
    }
}
