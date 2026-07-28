package io.github.nsreader.ui.composer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.core.AppClock
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.core.runCatchingExceptCancellation
import io.github.nsreader.data.Board
import io.github.nsreader.data.ProfileRepository
import io.github.nsreader.data.composer.ImageAttachment
import io.github.nsreader.data.composer.ImageUploadQueue
import io.github.nsreader.data.composer.ImageUploader
import io.github.nsreader.data.composer.PickedImage
import io.github.nsreader.data.composer.PostComposerRepository
import io.github.nsreader.data.composer.PostDraft
import io.github.nsreader.data.composer.PostPermission
import io.github.nsreader.data.composer.PostSubmission
import io.github.nsreader.data.composer.UploadFailure
import io.github.nsreader.data.composer.UploadStatus
import io.github.nsreader.data.session.SessionState
import io.github.nsreader.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
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
) : ViewModel() {
    private val _uiState = MutableStateFlow(PostComposerUiState())
    val uiState: StateFlow<PostComposerUiState> = _uiState.asStateFlow()

    private val uploads = ImageUploadQueue(viewModelScope, uploader)

    private var saveJob: Job? = null
    private var publishJob: Job? = null
    private var authorJob: Job? = null

    init {
        boards
            .onEach { boards ->
                _uiState.update {
                    it.copy(boards = boards.filter { board -> board.slug != null && !board.adminOnly })
                }
            }.launchIn(viewModelScope)
        session
            .onEach { session -> _uiState.update { it.copy(isSignedIn = session.isSignedIn) } }
            .launchIn(viewModelScope)
        uploads.attachments
            .onEach { attachments -> _uiState.update { it.copy(attachments = attachments) } }
            .launchIn(viewModelScope)
        // An upload lands while typing continues, so its Markdown goes to the end of the body
        // rather than wherever the caret happens to be sitting.
        uploads.uploaded
            .onEach { attachment ->
                val markdown = attachment.markdown ?: return@onEach
                mutate { it.copy(body = appendBlock(it.body, markdown)) }
            }.launchIn(viewModelScope)

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

    fun updateTitle(title: String) = mutate { it.copy(title = title.take(MAX_TITLE_LENGTH)) }

    fun updateBody(body: String) = mutate { it.copy(body = body) }

    fun selectBoard(board: Board) = mutate { it.copy(boardSlug = board.slug, boardTitle = board.title) }

    fun selectPermission(permission: PostPermission) = mutate { it.copy(permission = permission) }

    fun setViewMode(mode: ComposerViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
        if (mode != ComposerViewMode.CONTENT) loadAuthorName()
    }

    fun continueDraft() {
        val draft = _uiState.value.pendingDraft ?: return
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
        mutate { it.copy(body = removeBlock(it.body, markdown)) }
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
                        title = state.title,
                        body = state.body,
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
                        publishError = (throwable as? NodeSeekException)?.error ?: NodeSeekError.Unknown,
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
     * The preview's byline needs a name, and nothing else in the composer does — so it is fetched
     * the first time a preview is opened rather than on entry, and a failure just leaves the byline
     * one field shorter.
     */
    private fun loadAuthorName() {
        val repository = profileRepository ?: return
        if (_uiState.value.authorName != null || authorJob?.isActive == true) return
        authorJob = viewModelScope.launch {
            runCatchingExceptCancellation { repository.profile() }
                .onSuccess { profile -> _uiState.update { it.copy(authorName = profile.name) } }
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
    val isPublishing: Boolean = false,
    val publishError: NodeSeekError? = null,
    val publishErrorDetail: String? = null,
    val savedAtMillis: Long? = null,
    val pendingDraft: PostDraft? = null,
    val draftDecisionMade: Boolean = false,
    val attachments: List<ImageAttachment> = emptyList(),
    val authorName: String? = null,
) {
    val hasContent: Boolean get() = title.isNotBlank() || body.isNotBlank()

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
