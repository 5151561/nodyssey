package io.github.nodyssey.ui.composer

import io.github.nodyssey.data.MutableClock
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.UserProfile
import io.github.nodyssey.data.composer.CommentComposerRepository
import io.github.nodyssey.data.composer.CommentDraft
import io.github.nodyssey.data.composer.CommentSubmission
import io.github.nodyssey.data.composer.ImageAttachment
import io.github.nodyssey.data.composer.ImageUploader
import io.github.nodyssey.ui.ViewModels
import io.github.nodyssey.ui.typeMore
import io.github.nodyssey.ui.typeText
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
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

    private fun viewModel(
        postId: Long = 1L,
        profiles: ProfileRepository? = null,
    ) = viewModels.track(
        ReplyComposerViewModel(
            postId,
            repository,
            clock,
            uploader,
            profileRepository = profiles,
        ),
    )

    /**
     * The reply box carries the APP menu too, because the site's own always has.
     *
     * Same marker as the post editor writes — the two share `StardustReceiveMarkup` precisely so a
     * code inserted from a floor cannot come out shaped differently from one in an opening post.
     */
    @Test
    fun `inserting a receive code from the reply sheet writes the same marker`() = runTest(dispatcher) {
        val viewModel = viewModel(profiles = FakeReplyProfileRepository(selfUid = 52_425))
        viewModel.open()
        advanceUntilIdle()

        viewModel.insertReceiveCode(amount = 5, refId = 7, description = "拼车 第 3 期", onetime = false)
        advanceUntilIdle()

        val body = viewModel.bodyState.text.toString()
        assertTrue(body, body.contains("nsapp://stardust-receive?member_id=52425&ref_id=7"))
        assertTrue(body, body.contains("&onetime=false"))
        assertTrue(viewModel.uiState.value.body.contains("nsapp://stardust-receive"))
    }

    /** No uid, no payee — and the sheet says so rather than writing a code that collects for nobody. */
    @Test
    fun `the reply sheet declines a receive code before the account is known`() = runTest(dispatcher) {
        val viewModel = viewModel(profiles = FakeReplyProfileRepository(selfUid = null))
        viewModel.open()
        advanceUntilIdle()

        assertNull(viewModel.receiveCodePayeeUid())
        viewModel.insertReceiveCode(amount = 5, refId = 7, description = "", onetime = false)
        advanceUntilIdle()

        assertEquals("", viewModel.bodyState.text.toString())
    }

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
     * The shape is the site's, not ours: fixture `post-703863-1.html` floor #8 is
     * `@ipv4 [#7](/post-703863-1#7) 想要十几刀年付的中盘鸡` — mention, floor link and answer on one
     * line. The mention is the whole point of 回复; without it the answer reaches nobody.
     */
    @Test
    fun `回复 opens the comment with a mention of the floor it answers`() = runTest(dispatcher) {
        val viewModel = viewModel(postId = 703863L)
        viewModel.open(
            FloorReference(
                floor = 7,
                author = "ipv4",
                excerpt = "绿云有哪些产品可以 归类为 传家宝 的？",
                postedAt = "2026/4/27 16:00:53",
            ),
        )
        advanceUntilIdle()
        viewModel.bodyState.typeText("想要十几刀年付的中盘鸡")
        runCurrent()
        advanceUntilIdle()

        viewModel.publish {}
        advanceUntilIdle()

        val submission = repository.submission
        assertEquals(7, submission?.quotedFloor)
        // No blockquote and no timestamp: 回复 addresses the floor, it does not reproduce it.
        assertEquals("@ipv4 [#7](/post-703863-1#7) 想要十几刀年付的中盘鸡", submission?.body)
    }

    /**
     * The exact shape matters, and it is not ours either: fixture `post-703863-1.html` floor #7 is a
     * 引用. The header line names the author and links the floor, the quoted text follows as
     * blockquote lines, and the reply starts after a blank line — `@name` at the *end* of the
     * blockquote would render as part of the quotation instead.
     */
    @Test
    fun `引用 drops the floor into the body as a blockquote`() = runTest(dispatcher) {
        val viewModel = viewModel(postId = 841108L)
        viewModel.quote(
            FloorReference(
                floor = 12,
                author = "nssk",
                excerpt = "还没有这个功能",
                postedAt = "2026/7/28 12:01:29",
            ),
        )
        advanceUntilIdle()
        viewModel.bodyState.typeMore("赞成用星辰兑换改名")
        runCurrent()
        advanceUntilIdle()

        viewModel.publish {}
        advanceUntilIdle()

        val submission = repository.submission
        // Nothing is addressed: 引用 reproduces a floor, it does not reply at one.
        assertNull(submission?.quotedFloor)
        assertEquals(
            "> @nssk [#12](/post-841108-1#12) 发布于2026/7/28 12:01:29\n" +
                "> 还没有这个功能\n" +
                "\n" +
                "赞成用星辰兑换改名",
            submission?.body,
        )
    }

    /**
     * The whole point of the redesign: a reader quotes one floor, scrolls on, quotes another. Both
     * have to survive as separate blocks, which is what the blank line between them is for.
     */
    @Test
    fun `每点一次引用就多一段，互不覆盖`() = runTest(dispatcher) {
        val viewModel = viewModel(postId = 7L)
        viewModel.quote(FloorReference(floor = 3, author = "nssk", excerpt = "第一段"))
        advanceUntilIdle()
        viewModel.quote(FloorReference(floor = 9, author = "other", excerpt = "第二段"))
        advanceUntilIdle()
        viewModel.bodyState.typeMore("两位说得都对")
        runCurrent()
        advanceUntilIdle()

        viewModel.publish {}
        advanceUntilIdle()

        assertEquals(
            "> @nssk [#3](/post-7-1#3)\n> 第一段\n" +
                "\n" +
                "> @other [#9](/post-7-1#9)\n> 第二段\n" +
                "\n" +
                "两位说得都对",
            repository.submission?.body,
        )
    }

    /** 回复 and 引用 compose: the mention cannot share a line with a `>` and still be a blockquote. */
    @Test
    fun `一条评论既引用又回复时，@ 独占一行`() = runTest(dispatcher) {
        val viewModel = viewModel(postId = 7L)
        viewModel.open(FloorReference(floor = 3, author = "nssk", excerpt = "占位"))
        advanceUntilIdle()
        viewModel.quote(FloorReference(floor = 9, author = "other", excerpt = "另一层"))
        advanceUntilIdle()
        viewModel.bodyState.typeMore("见上")
        runCurrent()
        advanceUntilIdle()

        viewModel.publish {}
        advanceUntilIdle()

        assertEquals(
            "@nssk [#3](/post-7-1#3)\n" +
                "\n" +
                "> @other [#9](/post-7-1#9)\n> 另一层\n" +
                "\n" +
                "见上",
            repository.submission?.body,
        )
    }

    /** Most floors carry a timestamp; a comment parsed without one must not print "发布于null". */
    @Test
    fun `a quote with no timestamp omits the 发布于 clause`() = runTest(dispatcher) {
        val viewModel = viewModel(postId = 7L)
        viewModel.quote(FloorReference(floor = 3, author = "nssk", excerpt = "占位"))
        advanceUntilIdle()
        viewModel.bodyState.typeMore("好")
        runCurrent()
        advanceUntilIdle()

        viewModel.publish {}
        advanceUntilIdle()

        assertEquals("> @nssk [#3](/post-7-1#3)\n> 占位\n\n好", repository.submission?.body)
    }

    /** The quotes live in the body, so a restored draft gets them back for free — the chip does not. */
    @Test
    fun `a restored draft keeps its quotes and the floor it replies to`() = runTest(dispatcher) {
        val viewModel = viewModel(postId = 7L)
        viewModel.open(FloorReference(floor = 3, author = "nssk", excerpt = "占位"))
        advanceUntilIdle()
        viewModel.quote(FloorReference(floor = 9, author = "other", excerpt = "另一层"))
        advanceUntilIdle()
        viewModel.bodyState.typeMore("见上")
        runCurrent()
        advanceUntilIdle()

        val second = viewModel(postId = 7L)
        second.open()
        advanceUntilIdle()

        assertEquals(3, second.uiState.value.replyTo?.floor)
        assertTrue(second.uiState.value.body.startsWith("> @other [#9](/post-7-1#9)"))
    }

    @Test
    fun `a later floor replaces the reply target instead of answering the old one`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.open(FloorReference(floor = 12, author = "nssk", excerpt = "第一段"))
        advanceUntilIdle()
        viewModel.close()
        viewModel.open(FloorReference(floor = 20, author = "other", excerpt = "第二段"))
        advanceUntilIdle()

        assertEquals(20, viewModel.uiState.value.replyTo?.floor)
    }

    /** The field is not part of the state, so a publish that only reset the state would leak text. */
    @Test
    fun `publishing clears the editor, not just the state`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.open()
        advanceUntilIdle()
        viewModel.bodyState.typeText("发出去的话")
        runCurrent()
        advanceUntilIdle()

        viewModel.publish {}
        advanceUntilIdle()

        assertEquals("", viewModel.bodyState.text.toString())
    }

    @Test
    fun `a failed publish keeps the draft and surfaces the reason`() = runTest(dispatcher) {
        repository.publishError = SiteException(SiteError.Unknown, detail = "评论发布接口尚未接入")
        val viewModel = viewModel()
        viewModel.open()
        advanceUntilIdle()
        viewModel.bodyState.typeText("这条要留住")
        runCurrent()
        advanceUntilIdle()

        viewModel.publish { error("must not report success") }
        advanceUntilIdle()

        assertEquals(SiteError.Unknown, viewModel.uiState.value.publishError)
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

/** Only [selfUid] matters here — the reply sheet asks the profile for nothing else. */
private class FakeReplyProfileRepository(
    override val selfUid: Long?,
) : ProfileRepository {
    override suspend fun profile(refresh: Boolean): UserProfile =
        UserProfile(uid = selfUid ?: 0L, name = "我", avatarUrl = "", rank = 3)

    override suspend fun profile(uid: Long): UserProfile = profile(refresh = false)
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
