package io.github.nodyssey.ui.composer

import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException
import io.github.nodyssey.data.MutableClock
import io.github.nodyssey.data.composer.CommentComposerRepository
import io.github.nodyssey.data.composer.CommentDraft
import io.github.nodyssey.data.composer.CommentSubmission
import io.github.nodyssey.data.composer.ImageAttachment
import io.github.nodyssey.data.composer.ImageUploader
import io.github.nodyssey.ui.ViewModels
import io.github.nodyssey.ui.typeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
        viewModels.clear(dispatcher.scheduler)
        Dispatchers.resetMain()
    }

    private val viewModels = ViewModels()

    private fun viewModel(postId: Long = 1L) =
        viewModels.track(ReplyComposerViewModel(postId, repository, clock, uploader))

    @Test
    fun `closing the sheet keeps the reply, and reopening restores the stored draft`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.open()
        advanceUntilIdle()
        viewModel.bodyState.typeText("写到一半")
        runCurrent()
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
        first.bodyState.typeText("第一帖")
        runCurrent()
        advanceUntilIdle()

        val second = viewModel(postId = 2L)
        second.open()
        advanceUntilIdle()

        assertEquals("", second.uiState.value.body)
    }

    /**
     * The exact shape matters, and it is not ours: it was read off a quote the site's own editor
     * produced (sandbox thread, 2026-07-28). The header line names the author and links the floor,
     * the quoted text follows as blockquote lines, and the reply starts after a blank line —
     * `@name` at the *end* of the blockquote would render as part of the quotation instead.
     */
    @Test
    fun `replying to a floor quotes it, and the quote is sent as a blockquote`() = runTest(dispatcher) {
        val viewModel = viewModel(postId = 841108L)
        viewModel.open(
            ReplyQuote(
                floor = 12,
                author = "nssk",
                excerpt = "还没有这个功能",
                postedAt = "2026/7/28 12:01:29",
            ),
        )
        advanceUntilIdle()
        viewModel.bodyState.typeText("赞成用星辰兑换改名")
        runCurrent()
        advanceUntilIdle()

        viewModel.publish {}
        advanceUntilIdle()

        val submission = repository.submission
        assertEquals(12, submission?.quotedFloor)
        assertEquals(
            "> @nssk [#12](https://www.nodeseek.com/post-841108-1#12) 发布于2026/7/28 12:01:29\n" +
                "> 还没有这个功能\n" +
                "\n" +
                "赞成用星辰兑换改名",
            submission?.body,
        )
    }

    /** Most floors carry a timestamp; a comment parsed without one must not print "发布于null". */
    @Test
    fun `a quote with no timestamp omits the 发布于 clause`() = runTest(dispatcher) {
        val viewModel = viewModel(postId = 7L)
        viewModel.open(ReplyQuote(floor = 3, author = "nssk", excerpt = "占位"))
        advanceUntilIdle()
        viewModel.bodyState.typeText("好")
        runCurrent()
        advanceUntilIdle()

        viewModel.publish {}
        advanceUntilIdle()

        assertEquals(
            "> @nssk [#3](https://www.nodeseek.com/post-7-1#3)\n> 占位\n\n好",
            repository.submission?.body,
        )
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
        viewModel.bodyState.typeText("这条要留住")
        runCurrent()
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
    fun `a bracketed display name cannot break the image markdown`() {
        val attachment = ImageAttachment(
            id = "1",
            source = "content://pick/1",
            name = "shot [v2] (final).png",
            remoteUrl = "https://cdn.nodeimage.com/i/x.webp",
        )

        assertEquals("![shot  v2   final .png](https://cdn.nodeimage.com/i/x.webp)", attachment.markdown)
    }

    @Test
    fun `clearing the reply removes the stored draft`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.open()
        advanceUntilIdle()
        viewModel.bodyState.typeText("先写点")
        runCurrent()
        advanceUntilIdle()
        assertEquals("先写点", repository.saved[1L]?.body)

        viewModel.bodyState.typeText("")
        runCurrent()
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
