package io.github.nsreader.ui.composer

import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.data.MutableClock
import io.github.nsreader.data.composer.CommentComposerRepository
import io.github.nsreader.data.composer.CommentDraft
import io.github.nsreader.data.composer.CommentSubmission
import io.github.nsreader.data.composer.ImageAttachment
import io.github.nsreader.data.composer.ImageUploader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
class ReplyComposerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeCommentComposerRepository()
    private val clock = MutableClock()
    private val uploader = ImageUploader { _: ImageAttachment, _: (Float) -> Unit -> "https://cdn.nodeimage.com/i/x.webp" }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(postId: Long = 1L) =
        ReplyComposerViewModel(postId, repository, clock, uploader)

    @Test
    fun `closing the sheet keeps the reply, and reopening restores the stored draft`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.open()
        advanceUntilIdle()
        viewModel.updateBody("写到一半")
        advanceUntilIdle()

        assertEquals("写到一半", repository.saved[1L]?.body)

        viewModel.close()
        assertFalse(viewModel.uiState.value.visible)
        assertEquals("写到一半", viewModel.uiState.value.body)

        // A fresh instance is what a process death leaves behind; the draft has to survive that too.
        val restored = viewModel()
        restored.open()
        advanceUntilIdle()

        assertEquals("写到一半", restored.uiState.value.body)
    }

    @Test
    fun `drafts do not leak between threads`() = runTest(dispatcher) {
        val first = viewModel(postId = 1L)
        first.open()
        advanceUntilIdle()
        first.updateBody("第一帖")
        advanceUntilIdle()

        val second = viewModel(postId = 2L)
        second.open()
        advanceUntilIdle()

        assertEquals("", second.uiState.value.body)
    }

    @Test
    fun `replying to a floor quotes it, and the quote is sent as a blockquote`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.open(ReplyQuote(floor = 12, author = "nssk", excerpt = "还没有这个功能"))
        advanceUntilIdle()
        viewModel.updateBody("赞成用星辰兑换改名")
        advanceUntilIdle()

        viewModel.publish {}
        advanceUntilIdle()

        val submission = repository.submission
        assertEquals(12, submission?.quotedFloor)
        assertEquals("> 还没有这个功能\n\n@nssk 赞成用星辰兑换改名", submission?.body)
    }

    @Test
    fun `a later floor replaces the quote instead of answering the old one`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.open(ReplyQuote(floor = 12, author = "nssk", excerpt = "第一段"))
        advanceUntilIdle()
        viewModel.close()
        viewModel.open(ReplyQuote(floor = 20, author = "other", excerpt = "第二段"))
        advanceUntilIdle()

        assertEquals(20, viewModel.uiState.value.quote?.floor)
    }

    @Test
    fun `a failed publish keeps the draft and surfaces the reason`() = runTest(dispatcher) {
        repository.publishError = NodeSeekException(NodeSeekError.Unknown, detail = "评论发布接口尚未接入")
        val viewModel = viewModel()
        viewModel.open()
        advanceUntilIdle()
        viewModel.updateBody("这条要留住")
        advanceUntilIdle()

        viewModel.publish { error("must not report success") }
        advanceUntilIdle()

        assertEquals(NodeSeekError.Unknown, viewModel.uiState.value.publishError)
        assertEquals("评论发布接口尚未接入", viewModel.uiState.value.publishErrorDetail)
        assertEquals("这条要留住", viewModel.uiState.value.body)
        assertTrue(viewModel.uiState.value.visible)
        assertEquals("这条要留住", repository.saved[1L]?.body)
    }

    @Test
    fun `clearing the reply removes the stored draft`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.open()
        advanceUntilIdle()
        viewModel.updateBody("先写点")
        advanceUntilIdle()
        assertEquals("先写点", repository.saved[1L]?.body)

        viewModel.updateBody("")
        advanceUntilIdle()

        assertNull(repository.saved[1L])
        assertNull(viewModel.uiState.value.savedAtMillis)
    }
}

private class FakeCommentComposerRepository : CommentComposerRepository {
    val saved = mutableMapOf<Long, CommentDraft>()
    var submission: CommentSubmission? = null
    var publishError: Throwable? = null
    private val drafts = mutableMapOf<Long, MutableStateFlow<CommentDraft?>>()

    private fun flow(postId: Long) = drafts.getOrPut(postId) { MutableStateFlow(null) }

    override fun draft(postId: Long): Flow<CommentDraft?> = flow(postId)

    override suspend fun saveDraft(postId: Long, draft: CommentDraft) {
        saved[postId] = draft
        flow(postId).value = draft
    }

    override suspend fun deleteDraft(postId: Long) {
        saved.remove(postId)
        flow(postId).value = null
    }

    override suspend fun publish(submission: CommentSubmission): Int? {
        this.submission = submission
        publishError?.let { throw it }
        return 13
    }
}
