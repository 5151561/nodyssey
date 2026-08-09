package io.github.nodyssey.ui.composer

import io.github.nodyssey.data.Board
import io.github.nodyssey.data.MutableClock
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.UserProfile
import io.github.nodyssey.data.VoteRepository
import io.github.nodyssey.data.composer.ImageAttachment
import io.github.nodyssey.data.composer.ImageUploader
import io.github.nodyssey.data.composer.PickedImage
import io.github.nodyssey.data.composer.PostComposerRepository
import io.github.nodyssey.data.composer.PostDraft
import io.github.nodyssey.data.composer.PostPermission
import io.github.nodyssey.data.composer.PostSubmission
import io.github.nodyssey.data.composer.UploadStatus
import io.github.nodyssey.data.session.SessionState
import io.github.nodyssey.model.Vote
import io.github.nodyssey.ui.ViewModels
import io.github.nodyssey.ui.typeText
import io.github.nodyssey.ui.vote.VoteCreationState
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class PostComposerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeComposerRepository()
    private val clock = MutableClock()
    private val boards = MutableStateFlow(listOf(Board("tech", "技术", null)))
    private val uploader = FakeImageUploader()
    private val session = MutableStateFlow(SessionState(isSignedIn = true))
    private val viewModels = ViewModels()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        viewModels.clear(dispatcher.scheduler)
        Dispatchers.resetMain()
    }

    private fun viewModel(
        profiles: ProfileRepository? = null,
        votes: VoteRepository? = null,
    ) = viewModels.track(
        PostComposerViewModel(
            repository,
            boards,
            session,
            clock,
            uploader,
            profiles,
            voteRepository = votes,
        ),
    )

    // --- 插入投票 ------------------------------------------------------------

    /**
     * The vote has to exist server-side before the body can name it, so the id in the marker is the
     * one the site handed back — never a guess.
     */
    @Test
    fun `creating a vote splices the site's own id into the body`() = runTest(dispatcher) {
        val votes = FakeVoteRepository(newId = 3001)
        val viewModel = viewModel(votes = votes)
        advanceUntilIdle()
        viewModel.bodyState.edit { replace(0, length, "正文") }
        var inserted = false

        viewModel.createVote("标题", multiple = false, isPublic = true, items = listOf("甲", "乙")) {
            inserted = true
        }
        advanceUntilIdle()

        assertTrue(inserted)
        assertTrue(viewModel.bodyState.text.contains("nsapp://vote?id=3001"))
        assertEquals(
            listOf(VoteCreationRequest("标题", multiple = false, isPublic = true, items = listOf("甲", "乙"))),
            votes.created,
        )
        assertEquals(VoteCreationState.Idle, viewModel.uiState.value.voteCreation)
    }

    /** The mirrored `body` has to see it too, or the draft would autosave the text without the vote. */
    @Test
    fun `the inserted marker reaches the mirrored body state`() = runTest(dispatcher) {
        val viewModel = viewModel(votes = FakeVoteRepository(newId = 3001))
        advanceUntilIdle()

        viewModel.createVote("标题", multiple = false, isPublic = true, items = listOf("甲", "乙")) {}
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.body.contains("nsapp://vote?id=3001"))
    }

    /** A failed creation must leave the post exactly as it was — no marker pointing at nothing. */
    @Test
    fun `a refused creation keeps the body untouched and holds the site's sentence`() = runTest(dispatcher) {
        val votes =
            FakeVoteRepository(
                newId = 3001,
                failure = SiteException(SiteError.Unknown, detail = "没有发起投票的权限"),
            )
        val viewModel = viewModel(votes = votes)
        advanceUntilIdle()
        viewModel.bodyState.edit { replace(0, length, "正文") }
        var inserted = false

        viewModel.createVote("标题", multiple = false, isPublic = true, items = listOf("甲")) { inserted = true }
        advanceUntilIdle()

        assertFalse(inserted)
        assertEquals("正文", viewModel.bodyState.text.toString())
        assertEquals(
            VoteCreationState.Failed("没有发起投票的权限"),
            viewModel.uiState.value.voteCreation,
        )

        viewModel.dismissVoteCreation()
        assertEquals(VoteCreationState.Idle, viewModel.uiState.value.voteCreation)
    }

    /** A build that never wired the repository does nothing rather than reporting a create it never sent. */
    @Test
    fun `an unwired composer cannot create a vote`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        var inserted = false

        viewModel.createVote("标题", multiple = false, isPublic = true, items = listOf("甲")) { inserted = true }
        advanceUntilIdle()

        assertFalse(inserted)
        assertEquals("", viewModel.bodyState.text.toString())
    }

    @Test
    fun `read permission offers every level up to the account's own`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeProfileRepository(rank = 3))
        advanceUntilIdle()

        assertEquals(
            listOf(0, 1, 2, 3, 255),
            viewModel.uiState.value.permissionOptions.map { it.wireValue },
        )
    }

    @Test
    fun `read permission falls back to Lv1 when the profile never arrives`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeProfileRepository(rank = 3, fails = true))
        advanceUntilIdle()

        assertEquals(
            listOf(0, 1, 255),
            viewModel.uiState.value.permissionOptions.map { it.wireValue },
        )
    }

    @Test
    fun `a restored draft keeps a level the account has outgrown the menu`() = runTest(dispatcher) {
        repository.draftState.value =
            PostDraft(title = "标题", body = "正文", permission = PostPermission(4))
        val viewModel = viewModel(FakeProfileRepository(rank = 2))
        advanceUntilIdle()
        viewModel.continueDraft()

        assertEquals(PostPermission(4), viewModel.uiState.value.permission)
        assertEquals(
            listOf(0, 1, 2, 4, 255),
            viewModel.uiState.value.permissionOptions.map { it.wireValue },
        )
    }

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
    }

    @Test
    fun `editing autosaves without blocking each keystroke`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.titleState.typeText("新标题")
        viewModel.bodyState.typeText("正文")
        runCurrent()
        advanceTimeBy(749)
        assertNull(repository.savedDraft)

        advanceTimeBy(2)
        advanceUntilIdle()

        assertEquals("新标题", repository.savedDraft?.title)
        assertEquals("正文", repository.savedDraft?.body)
        assertEquals(clock.nowMillis(), viewModel.uiState.value.savedAtMillis)
    }

    @Test
    fun `clearing the editor deletes the stored draft instead of resurrecting it`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.titleState.typeText("标题")
        viewModel.bodyState.typeText("正文")
        runCurrent()
        advanceUntilIdle()
        assertEquals("标题", repository.savedDraft?.title)

        viewModel.titleState.typeText("")
        viewModel.bodyState.typeText("")
        runCurrent()
        advanceUntilIdle()

        assertEquals(1, repository.deleteCount)
        assertNull(repository.draftState.value)
    }

    @Test
    fun `publish failure keeps the draft and exposes a retryable error`() = runTest(dispatcher) {
        repository.publishError = SiteException(SiteError.Network)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.titleState.typeText("标题")
        viewModel.bodyState.typeText("正文")
        runCurrent()
        advanceUntilIdle()
        viewModel.selectBoard(boards.value.single())

        viewModel.publish { error("must not publish") }
        advanceUntilIdle()

        assertEquals(SiteError.Network, viewModel.uiState.value.publishError)
        assertFalse(viewModel.uiState.value.isPublishing)
        assertEquals(0, repository.deleteCount)
        assertEquals("正文", viewModel.uiState.value.body)
    }

    @Test
    fun `successful retry clears the draft and returns the post id`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.titleState.typeText("标题")
        viewModel.bodyState.typeText("正文")
        runCurrent()
        advanceUntilIdle()
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
        viewModel.titleState.typeText("标题")
        viewModel.bodyState.typeText("正文")
        runCurrent()
        advanceUntilIdle()
        viewModel.selectBoard(boards.value.single())
        var callbackInvoked = false

        viewModel.publish { callbackInvoked = true }
        advanceUntilIdle()

        assertTrue(callbackInvoked)
        assertEquals(1, repository.deleteCount)
        assertNull(viewModel.uiState.value.publishError)
    }

    @Test
    fun `an uploaded image is appended to the body and removed with its cell`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.bodyState.typeText("正文")
        runCurrent()
        advanceUntilIdle()

        viewModel.addImages(listOf(PickedImage("content://pick/1", "screenshot.png")))
        advanceUntilIdle()

        val attachment = viewModel.uiState.value.attachments.single()
        assertEquals(UploadStatus.UPLOADED, attachment.status)
        assertEquals("正文\n\n![screenshot.png](https://cdn.nodeimage.com/i/fake.webp)", viewModel.uiState.value.body)

        viewModel.removeAttachment(attachment)
        advanceUntilIdle()

        assertEquals("正文", viewModel.uiState.value.body)
        assertTrue(viewModel.uiState.value.attachments.isEmpty())
    }

    @Test
    fun `a failed upload is retryable and blocks publishing until it settles`() = runTest(dispatcher) {
        uploader.failures = 1
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.titleState.typeText("标题")
        viewModel.bodyState.typeText("正文")
        runCurrent()
        advanceUntilIdle()
        viewModel.selectBoard(boards.value.single())

        viewModel.addImages(listOf(PickedImage("content://pick/1", "a.png")))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.failedUploadCount)
        // A failed upload is settled: nothing more is coming, so publishing is allowed again.
        assertTrue(viewModel.uiState.value.canPublish)

        viewModel.retryFailedUploads()
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.failedUploadCount)
        assertEquals(UploadStatus.UPLOADED, viewModel.uiState.value.attachments.single().status)
    }
}

private class FakeProfileRepository(
    private val rank: Int?,
    private val fails: Boolean = false,
) : ProfileRepository {
    override suspend fun profile(refresh: Boolean): UserProfile {
        if (fails) throw SiteException(SiteError.Network)
        return UserProfile(uid = 1L, name = "我", avatarUrl = "", rank = rank)
    }

    override suspend fun profile(uid: Long): UserProfile = profile(refresh = false)
}

private class FakeImageUploader : ImageUploader {
    var failures = 0

    override suspend fun upload(
        attachment: ImageAttachment,
        onProgress: (Float) -> Unit,
    ): String {
        if (failures > 0) {
            failures--
            throw SiteException(SiteError.Network)
        }
        onProgress(1f)
        return "https://cdn.nodeimage.com/i/fake.webp"
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

private data class VoteCreationRequest(
    val title: String,
    val multiple: Boolean,
    val isPublic: Boolean,
    val items: List<String>,
)

private class FakeVoteRepository(
    private val newId: Long,
    private val failure: Throwable? = null,
) : VoteRepository {
    val created = mutableListOf<VoteCreationRequest>()

    override suspend fun info(voteId: Long): Vote = error("not used")

    override suspend fun submit(voteId: Long, itemIds: List<Long>) = error("not used")

    override suspend fun create(
        title: String,
        multiple: Boolean,
        isPublic: Boolean,
        items: List<String>,
    ): Long {
        failure?.let { throw it }
        created += VoteCreationRequest(title, multiple, isPublic, items)
        return newId
    }

    override suspend fun setLocked(voteId: Long, locked: Boolean) = error("not used")

    override suspend fun delete(voteId: Long) = error("not used")

    override suspend fun voters(itemId: Long, page: Int): List<Long> = error("not used")
}
