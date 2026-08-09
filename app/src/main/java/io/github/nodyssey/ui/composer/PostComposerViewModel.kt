package io.github.nodyssey.ui.composer

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.core.StardustReceiveMarkup
import io.github.nodyssey.core.VoteMarkup
import io.github.nodyssey.data.Board
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.VoteRepository
import io.github.nodyssey.data.composer.ImageAttachment
import io.github.nodyssey.data.composer.ImageUploadQueue
import io.github.nodyssey.data.composer.ImageUploader
import io.github.nodyssey.data.composer.PickedImage
import io.github.nodyssey.data.composer.PostComposerRepository
import io.github.nodyssey.data.composer.PostDraft
import io.github.nodyssey.data.composer.PostPermission
import io.github.nodyssey.data.composer.PostSubmission
import io.github.nodyssey.data.composer.UploadFailure
import io.github.nodyssey.data.composer.UploadStatus
import io.github.nodyssey.data.session.SessionState
import io.github.nodyssey.data.settings.ComposerSurface
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.vote.VoteCreationState
import io.github.plaza.core.AppClock
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.runCatchingExceptCancellation
import io.github.plaza.designsys.editor.EditorAction
import io.github.plaza.designsys.editor.ToolbarCustomizeSheet
import io.github.plaza.designsys.editor.ToolbarLayout
import io.github.plaza.designsys.editor.appendBlock
import io.github.plaza.designsys.editor.editFromViewModel
import io.github.plaza.designsys.editor.insertText
import io.github.plaza.designsys.editor.removeBlock
import io.github.plaza.designsys.editor.toolbarLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PostComposerViewModel(
    private val repository: PostComposerRepository,
    boards: Flow<List<Board>>,
    session: StateFlow<SessionState>,
    private val clock: AppClock,
    uploader: ImageUploader,
    private val profileRepository: ProfileRepository? = null,
    /**
     * Null in tests and wherever the strip is not editable; the editor then shows
     * [EditorActions.Post] and the wrench does nothing worth opening, so it is not offered.
     */
    private val settings: SettingsRepository? = null,
    /**
     * Null wherever 插入投票 is not offered — tests, and any build that has not wired it.
     *
     * Creating a vote lives here rather than in a dialog of its own because the id has to land at
     * this editor's caret: the site creates the vote first and only then is there a marker to insert.
     */
    private val voteRepository: VoteRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PostComposerUiState())
    val uiState: StateFlow<PostComposerUiState> = _uiState.asStateFlow()

    /**
     * The editor's own text and selection, owned here so an upload landing mid-sentence can splice
     * its Markdown in without disturbing the caret.
     *
     * [uiState] still carries `title`/`body` as plain strings — publish validation, the preview and
     * the draft all read them — but it mirrors these rather than competing with them. That is the
     * whole point: one writer, one direction.
     */
    val titleState = TextFieldState()
    val bodyState = TextFieldState()

    private val uploads = ImageUploadQueue(viewModelScope, uploader)

    private var saveJob: Job? = null
    private var publishJob: Job? = null
    private var authorJob: Job? = null
    private var profileLoaded = false

    init {
        boards
            .onEach { boards ->
                _uiState.update {
                    it.copy(boards = boards.filter { board -> board.slug != null && !board.adminOnly })
                }
            }.launchIn(viewModelScope)
        session
            .onEach { session ->
                _uiState.update { it.copy(isSignedIn = session.isSignedIn) }
                if (session.isSignedIn) loadSelfProfile()
            }.launchIn(viewModelScope)
        uploads.attachments
            .onEach { attachments -> _uiState.update { it.copy(attachments = attachments) } }
            .launchIn(viewModelScope)
        settings?.settings
            ?.map { it.postToolbarActions }
            ?.distinctUntilChanged()
            ?.onEach { stored ->
                _uiState.update { it.copy(toolbar = toolbarLayout(stored, EditorActions.Post)) }
            }?.launchIn(viewModelScope)
        // An upload lands while typing continues, so its Markdown goes to the end of the body
        // rather than wherever the caret happens to be sitting — and, because this edits the buffer
        // instead of replacing the whole string, the caret stays wherever the user left it.
        uploads.uploaded
            .onEach { attachment ->
                val markdown = attachment.markdown ?: return@onEach
                bodyState.editFromViewModel { appendBlock(markdown) }
            }.launchIn(viewModelScope)

        snapshotFlow { titleState.text.toString() }
            .onEach { title -> if (title != _uiState.value.title) mutate { it.copy(title = title) } }
            .launchIn(viewModelScope)
        snapshotFlow { bodyState.text.toString() }
            .onEach { body -> if (body != _uiState.value.body) mutate { it.copy(body = body) } }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val draft = repository.draft.first()
            _uiState.update { current ->
                if (current.draftDecisionMade) {
                    current
                } else {
                    current.copy(
                        pendingDraft = draft?.takeIf(PostDraft::hasContent),
                        draftDecisionMade = draft?.hasContent() != true,
                    )
                }
            }
        }
    }

    fun selectBoard(board: Board) = mutate { it.copy(boardSlug = board.slug, boardTitle = board.title) }

    fun selectPermission(permission: PostPermission) = mutate { it.copy(permission = permission) }

    fun setViewMode(mode: ComposerViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
        if (mode != ComposerViewMode.CONTENT) loadSelfProfile()
    }

    fun continueDraft() {
        val draft = _uiState.value.pendingDraft ?: return
        titleState.editFromViewModel {
            replace(0, length, draft.title)
            placeCursorAtEnd()
        }
        bodyState.editFromViewModel {
            replace(0, length, draft.body)
            placeCursorAtEnd()
        }
        _uiState.update {
            it.copy(
                title = draft.title,
                body = draft.body,
                boardSlug = draft.boardSlug,
                boardTitle = draft.boardTitle,
                permission = draft.permission,
                savedAtMillis = draft.savedAtMillis,
                pendingDraft = null,
                draftDecisionMade = true,
            )
        }
    }

    fun discardDraft() {
        viewModelScope.launch { repository.deleteDraft() }
        _uiState.update { it.copy(pendingDraft = null, draftDecisionMade = true) }
    }

    // --- Attachments --------------------------------------------------------

    fun addImages(images: List<PickedImage>) = uploads.enqueue(images)

    fun retryUpload(attachment: ImageAttachment) = uploads.retry(attachment.id)

    fun retryFailedUploads() = uploads.retryFailed()

    /** Dismissing a finished upload also takes its Markdown out of the body it was written into. */
    fun removeAttachment(attachment: ImageAttachment) {
        val removed = uploads.remove(attachment.id) ?: return
        val markdown = removed.markdown ?: return
        bodyState.editFromViewModel { removeBlock(markdown) }
    }

    // --- Publishing ---------------------------------------------------------

    fun publish(onPublished: (Long?) -> Unit) {
        val state = _uiState.value
        if (!state.canPublish || publishJob?.isActive == true) return
        publishJob = viewModelScope.launch {
            _uiState.update { it.copy(isPublishing = true, publishError = null, publishErrorDetail = null) }
            runCatchingExceptCancellation {
                repository.publish(
                    PostSubmission(
                        // Straight from the fields, not from the mirrored copy: the mirror runs a
                        // frame behind, and what is published must be exactly what is on screen.
                        title = titleState.text.toString(),
                        body = bodyState.text.toString(),
                        boardSlug = requireNotNull(state.boardSlug),
                        permission = state.permission,
                    ),
                )
            }.onSuccess { postId ->
                saveJob?.cancel()
                repository.deleteDraft()
                _uiState.update { it.copy(isPublishing = false) }
                onPublished(postId)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isPublishing = false,
                        publishError = (throwable as? SiteException)?.error ?: SiteError.Unknown,
                        publishErrorDetail = throwable.message,
                    )
                }
            }
        }
    }

    fun clearPublishError() {
        _uiState.update { it.copy(publishError = null, publishErrorDetail = null) }
    }

    /**
     * The signed-in account, for the 阅读权限 menu's ceiling and the preview's byline.
     *
     * On entry rather than on demand, because the 权限 chip is on screen from the first frame and
     * its menu has to know the account's level before it can be opened. The byline used to pull
     * this by itself when a preview was first opened; it now rides along for free.
     *
     * A failure costs the higher levels and one byline field, so it is swallowed: the fallback set
     * in [PostPermission.options] still posts.
     */
    private fun loadSelfProfile() {
        val repository = profileRepository ?: return
        if (profileLoaded || authorJob?.isActive == true) return
        authorJob = viewModelScope.launch {
            runCatchingExceptCancellation { repository.profile() }
                .onSuccess { profile ->
                    profileLoaded = true
                    _uiState.update { it.copy(authorName = profile.name, selfRank = profile.rank) }
                }
        }
    }

    private fun mutate(transform: (PostComposerUiState) -> PostComposerUiState) {
        _uiState.update { transform(it).copy(draftDecisionMade = true, pendingDraft = null) }
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MILLIS)
            val state = _uiState.value
            if (!state.draftDecisionMade) return@launch
            if (!state.hasContent) {
                // Emptying the editor is how a draft is abandoned; keeping the old one stored would
                // resurrect it in the recovery dialog on the next visit.
                repository.deleteDraft()
                return@launch
            }
            repository.saveDraft(state.toDraft())
            _uiState.update { it.copy(savedAtMillis = clock.nowMillis()) }
        }
    }

    /** Writes the strip through on every edit in the wrench panel; see `ToolbarCustomizeSheet`. */
    fun setToolbar(actions: List<EditorAction>) {
        val repository = settings ?: return
        viewModelScope.launch {
            repository.setComposerToolbar(ComposerSurface.POST, actions.map(EditorAction::name))
        }
    }

    /** Clears the stored arrangement, which is what makes the defaults come back. */
    fun resetToolbar() {
        val repository = settings ?: return
        viewModelScope.launch { repository.setComposerToolbar(ComposerSurface.POST, emptyList()) }
    }

    /**
     * Creates a vote and splices its marker in at the caret.
     *
     * Two steps that must not come apart: the vote exists on the server before the body mentions it,
     * so a failure here leaves the post exactly as it was rather than carrying a marker pointing at
     * nothing. Only a success reaches [bodyState], and [onInserted] runs only then — the dialog stays
     * open on failure with the site's own sentence still in it.
     */
    fun createVote(
        title: String,
        multiple: Boolean,
        isPublic: Boolean,
        items: List<String>,
        onInserted: () -> Unit,
    ) {
        val votes = voteRepository ?: return
        if (_uiState.value.voteCreation is VoteCreationState.InFlight) return
        _uiState.update { it.copy(voteCreation = VoteCreationState.InFlight) }
        viewModelScope.launch {
            runCatchingExceptCancellation { votes.create(title, multiple, isPublic, items) }
                .onSuccess { voteId ->
                    // `editFromViewModel`, not `edit`: this runs off-frame, and without the apply
                    // notification the body's snapshotFlow mirror never sees the marker — the draft
                    // would autosave the text as it was and the post would publish without the vote.
                    bodyState.editFromViewModel { insertText(VoteMarkup.marker(voteId)) }
                    _uiState.update { it.copy(voteCreation = VoteCreationState.Idle) }
                    onInserted()
                }.onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            voteCreation =
                            VoteCreationState.Failed((throwable as? SiteException)?.detail),
                        )
                    }
                }
        }
    }

    /** Puts the creation state back to idle — the dialog closing without having created anything. */
    fun dismissVoteCreation() {
        _uiState.update { it.copy(voteCreation = VoteCreationState.Idle) }
    }

    /**
     * The uid a 收款码 would collect for, or null when the app does not know it yet.
     *
     * Read at the moment the dialog opens rather than held in [uiState], because it is a session fact
     * that may still be arriving — the same reason `VoteViewModel` re-reads it on every load.
     */
    fun receiveCodePayeeUid(): Long? = profileRepository?.selfUid

    /**
     * Splices a 收款码 in at the caret.
     *
     * No request, unlike [createVote]: the marker is the whole code, and the site is not told about it
     * until somebody pays. Which is why this returns nothing to wait for and cannot fail — the only
     * way it declines is a missing [receiveCodePayeeUid], and the dialog says so before offering 插入.
     */
    fun insertReceiveCode(
        amount: Int,
        refId: Long,
        description: String,
        onetime: Boolean,
    ) {
        val payee = profileRepository?.selfUid ?: return
        // `editFromViewModel` for the same reason [createVote] uses it: without the apply notification
        // the body's snapshotFlow mirror never sees the marker and the draft autosaves the old text.
        bodyState.editFromViewModel {
            insertText(
                StardustReceiveMarkup.marker(
                    memberId = payee,
                    refId = refId,
                    amount = amount,
                    description = description,
                    onetime = onetime,
                ),
            )
        }
    }

    companion object {
        const val MAX_TITLE_LENGTH = 60
        private const val AUTOSAVE_DELAY_MILLIS = 750L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PostComposerViewModel(
                    repository = container.postComposerRepository,
                    boards = container.categoryRepository.boards,
                    session = container.sessionRepository.state,
                    clock = container.clock,
                    uploader = container.imageUploader,
                    profileRepository = container.profileRepository,
                    settings = container.settingsRepository,
                    voteRepository = container.voteRepository,
                )
            }
        }
    }
}

/** The site's three editor views (§1.8). 对照 stacks them, which is the only way it fits a phone. */
enum class ComposerViewMode { CONTENT, PREVIEW, COMPARE }

data class PostComposerUiState(
    val isSignedIn: Boolean = false,
    val boards: List<Board> = emptyList(),
    val title: String = "",
    val body: String = "",
    val boardSlug: String? = null,
    val boardTitle: String? = null,
    val permission: PostPermission = PostPermission.PUBLIC,
    val viewMode: ComposerViewMode = ComposerViewMode.CONTENT,
    /** The formatting strip's keys and the wrench panel's pool. Defaults until settings arrive. */
    val toolbar: ToolbarLayout = toolbarLayout(emptyList(), EditorActions.Post),
    val isPublishing: Boolean = false,
    val publishError: SiteError? = null,
    val publishErrorDetail: String? = null,
    val savedAtMillis: Long? = null,
    val pendingDraft: PostDraft? = null,
    val draftDecisionMade: Boolean = false,
    val attachments: List<ImageAttachment> = emptyList(),
    val authorName: String? = null,
    /** The signed-in account's level; null until the profile lands, or if it never does. */
    val selfRank: Int? = null,
    /** How 插入投票 is going. Idle whenever the dialog is closed or has nothing to report. */
    val voteCreation: VoteCreationState = VoteCreationState.Idle,
) {
    val hasContent: Boolean get() = title.isNotBlank() || body.isNotBlank()

    /**
     * What the 阅读权限 menu offers. A draft restored with a level the current [selfRank] does not
     * reach — saved before the profile was known, say — keeps its own choice in the list, so the
     * menu can always show what the chip is displaying.
     */
    val permissionOptions: List<PostPermission>
        get() = PostPermission.options(selfRank).let { options ->
            if (permission in options) options else (options + permission).sortedBy { it.wireValue }
        }

    val failedUploadCount: Int get() = attachments.count { it.status == UploadStatus.FAILED }

    /** The first failure's reason, for the snackbar; retrying clears it along with the status. */
    val uploadFailure: UploadFailure?
        get() = attachments.firstOrNull { it.status == UploadStatus.FAILED }?.failure

    val uploadErrorDetail: String?
        get() = attachments.firstOrNull { it.status == UploadStatus.FAILED }?.errorDetail

    /**
     * Publishing is blocked while an upload is still moving: the Markdown for a pending image is
     * written only when it lands, so posting early silently drops the picture from the thread.
     */
    val canPublish: Boolean
        get() = title.isNotBlank() &&
            body.isNotBlank() &&
            boardSlug != null &&
            !isPublishing &&
            attachments.none { it.status == UploadStatus.UPLOADING || it.status == UploadStatus.WAITING }
}

private fun PostComposerUiState.toDraft() = PostDraft(
    title = title,
    body = body,
    boardSlug = boardSlug,
    boardTitle = boardTitle,
    permission = permission,
    savedAtMillis = savedAtMillis ?: 0L,
)

private fun PostDraft.hasContent(): Boolean = title.isNotBlank() || body.isNotBlank()
