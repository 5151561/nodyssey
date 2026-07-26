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
import io.github.nsreader.data.composer.PostComposerRepository
import io.github.nsreader.data.composer.PostDraft
import io.github.nsreader.data.composer.PostPermission
import io.github.nsreader.data.composer.PostSubmission
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
) : ViewModel() {
    private val _uiState = MutableStateFlow(PostComposerUiState())
    val uiState: StateFlow<PostComposerUiState> = _uiState.asStateFlow()

    private var saveJob: Job? = null
    private var publishJob: Job? = null

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

    fun selectBoard(board: Board) = mutate {
        it.copy(
            boardSlug = board.slug,
            boardTitle = board.title,
            showRuleReminder = board.slug == TECH_BOARD && !it.ruleReminderDismissed,
        )
    }

    fun selectPermission(permission: PostPermission) = mutate { it.copy(permission = permission) }

    fun showPreview() {
        _uiState.update { it.copy(mode = ComposerMode.PREVIEW) }
    }

    fun showEditor() {
        _uiState.update { it.copy(mode = ComposerMode.EDIT) }
    }

    fun dismissRuleReminder() {
        _uiState.update { it.copy(showRuleReminder = false, ruleReminderDismissed = true) }
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
                showRuleReminder = draft.boardSlug == TECH_BOARD,
            )
        }
    }

    fun discardDraft() {
        viewModelScope.launch { repository.deleteDraft() }
        _uiState.update { it.copy(pendingDraft = null, draftDecisionMade = true) }
    }

    fun publish(onPublished: (Long) -> Unit) {
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

    private fun mutate(transform: (PostComposerUiState) -> PostComposerUiState) {
        _uiState.update { transform(it).copy(draftDecisionMade = true, pendingDraft = null) }
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MILLIS)
            val state = _uiState.value
            if (!state.hasContent || !state.draftDecisionMade) return@launch
            repository.saveDraft(state.toDraft())
            _uiState.update { it.copy(savedAtMillis = clock.nowMillis()) }
        }
    }

    companion object {
        const val MAX_TITLE_LENGTH = 60
        private const val TECH_BOARD = "tech"
        private const val AUTOSAVE_DELAY_MILLIS = 750L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PostComposerViewModel(
                    repository = container.postComposerRepository,
                    boards = container.categoryRepository.boards,
                    session = container.sessionRepository.state,
                    clock = container.clock,
                )
            }
        }
    }
}

enum class ComposerMode { EDIT, PREVIEW }

data class PostComposerUiState(
    val isSignedIn: Boolean = false,
    val boards: List<Board> = emptyList(),
    val title: String = "",
    val body: String = "",
    val boardSlug: String? = null,
    val boardTitle: String? = null,
    val permission: PostPermission = PostPermission.PUBLIC,
    val mode: ComposerMode = ComposerMode.EDIT,
    val isPublishing: Boolean = false,
    val publishError: NodeSeekError? = null,
    val publishErrorDetail: String? = null,
    val savedAtMillis: Long? = null,
    val pendingDraft: PostDraft? = null,
    val draftDecisionMade: Boolean = false,
    val showRuleReminder: Boolean = false,
    val ruleReminderDismissed: Boolean = false,
) {
    val hasContent: Boolean get() = title.isNotBlank() || body.isNotBlank()
    val canPublish: Boolean
        get() = title.isNotBlank() && body.isNotBlank() && boardSlug != null && !isPublishing
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
