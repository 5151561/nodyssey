package io.github.nodyssey.ui.composer

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.core.AppClock
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException
import io.github.nodyssey.core.runCatchingExceptCancellation
import io.github.nodyssey.data.composer.CommentComposerRepository
import io.github.nodyssey.data.composer.CommentDraft
import io.github.nodyssey.data.composer.CommentSubmission
import io.github.nodyssey.data.composer.ImageAttachment
import io.github.nodyssey.data.composer.ImageUploadQueue
import io.github.nodyssey.data.composer.ImageUploader
import io.github.nodyssey.data.composer.PickedImage
import io.github.nodyssey.data.composer.UploadFailure
import io.github.nodyssey.data.composer.UploadStatus
import io.github.nodyssey.data.settings.ComposerSurface
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.common.appendBlock
import io.github.nodyssey.ui.common.editFromViewModel
import io.github.nodyssey.ui.common.removeBlock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

/** Which floor a reply answers, and enough of it to keep the reply readable on its own. */
data class ReplyQuote(
    val floor: Int,
    val author: String,
    val excerpt: String,
    /** The floor's own timestamp, reproduced in the quote header the way the site's does. */
    val postedAt: String? = null,
)

/**
 * The reply editor behind 6d and C4.
 *
 * Scoped to the thread rather than to the sheet: closing the sheet must not throw away what has
 * been typed, and the draft is keyed by post id so two half-written replies in two threads do not
 * overwrite each other.
 */
class ReplyComposerViewModel(
    private val postId: Long,
    private val repository: CommentComposerRepository,
    private val clock: AppClock,
    uploader: ImageUploader,
    /** Null in tests; the sheet then shows [EditorActions.Reply] and offers no wrench. */
    private val settings: SettingsRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReplyComposerUiState(postId = postId))
    val uiState: StateFlow<ReplyComposerUiState> = _uiState.asStateFlow()

    /** The sheet's text and selection. See `PostComposerViewModel.bodyState` for why it lives here. */
    val bodyState = TextFieldState()

    private val uploads = ImageUploadQueue(viewModelScope, uploader)

    private var saveJob: Job? = null
    private var publishJob: Job? = null

    init {
        settings?.settings
            ?.map { it.replyToolbarActions }
            ?.distinctUntilChanged()
            ?.onEach { stored ->
                _uiState.update { it.copy(toolbar = toolbarLayout(stored, EditorActions.Reply)) }
            }?.launchIn(viewModelScope)
        uploads.attachments
            .onEach { attachments -> _uiState.update { it.copy(attachments = attachments) } }
            .launchIn(viewModelScope)
        uploads.uploaded
            .onEach { attachment ->
                val markdown = attachment.markdown ?: return@onEach
                bodyState.editFromViewModel { appendBlock(markdown) }
            }.launchIn(viewModelScope)
        snapshotFlow { bodyState.text.toString() }
            .onEach { body -> if (body != _uiState.value.body) mutate { it.copy(body = body) } }
            .launchIn(viewModelScope)
    }

    /**
     * Opens the sheet, restoring whatever was left behind.
     *
     * A [quote] passed in wins over the stored one — tapping 回复 on a different floor is a new
     * intent, and silently answering the previous floor would be worse than losing the old context.
     */
    fun open(quote: ReplyQuote? = null) {
        viewModelScope.launch {
            val stored = repository.draft(postId).first()
            _uiState.update { current ->
                val restored = if (current.body.isBlank() && stored != null) {
                    bodyState.editFromViewModel {
                        replace(0, length, stored.body)
                        placeCursorAtEnd()
                    }
                    current.copy(
                        body = stored.body,
                        savedAtMillis = stored.savedAtMillis.takeIf { it > 0 },
                        quote = stored.toQuote(),
                    )
                } else {
                    current
                }
                restored.copy(visible = true, previewing = false, quote = quote ?: restored.quote)
            }
        }
    }

    fun close() {
        _uiState.update { it.copy(visible = false, previewing = false) }
    }

    fun clearQuote() = mutate { it.copy(quote = null) }

    fun setPreviewing(previewing: Boolean) {
        _uiState.update { it.copy(previewing = previewing) }
    }

    fun addImages(images: List<PickedImage>) = uploads.enqueue(images)

    fun retryUpload(attachment: ImageAttachment) = uploads.retry(attachment.id)

    fun retryFailedUploads() = uploads.retryFailed()

    fun removeAttachment(attachment: ImageAttachment) {
        val removed = uploads.remove(attachment.id) ?: return
        val markdown = removed.markdown ?: return
        bodyState.editFromViewModel { removeBlock(markdown) }
    }

    fun publish(onPublished: (Int?) -> Unit) {
        val state = _uiState.value
        if (!state.canPublish || publishJob?.isActive == true) return
        publishJob = viewModelScope.launch {
            _uiState.update { it.copy(isPublishing = true, publishError = null, publishErrorDetail = null) }
            runCatchingExceptCancellation {
                repository.publish(
                    CommentSubmission(
                        postId = postId,
                        body = state.submissionBody,
                        quotedFloor = state.quote?.floor,
                    ),
                )
            }.onSuccess { floor ->
                saveJob?.cancel()
                repository.deleteDraft(postId)
                _uiState.value = ReplyComposerUiState(postId = postId)
                uploads.clear()
                onPublished(floor)
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

    private fun mutate(transform: (ReplyComposerUiState) -> ReplyComposerUiState) {
        _uiState.update(transform)
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MILLIS)
            val state = _uiState.value
            if (state.body.isBlank()) {
                repository.deleteDraft(postId)
                _uiState.update { it.copy(savedAtMillis = null) }
                return@launch
            }
            repository.saveDraft(postId, state.toDraft())
            _uiState.update { it.copy(savedAtMillis = clock.nowMillis()) }
        }
    }

    /** Writes the strip through on every edit in the wrench panel; see `ToolbarCustomizeSheet`. */
    fun setToolbar(actions: List<EditorAction>) {
        val repository = settings ?: return
        viewModelScope.launch {
            repository.setComposerToolbar(ComposerSurface.REPLY, actions.map(EditorAction::name))
        }
    }

    /** Clears the stored arrangement, which is what makes the defaults come back. */
    fun resetToolbar() {
        val repository = settings ?: return
        viewModelScope.launch { repository.setComposerToolbar(ComposerSurface.REPLY, emptyList()) }
    }

    companion object {
        private const val AUTOSAVE_DELAY_MILLIS = 750L

        fun factory(container: AppContainer, postId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ReplyComposerViewModel(
                    postId = postId,
                    repository = container.commentComposerRepository,
                    clock = container.clock,
                    uploader = container.imageUploader,
                    settings = container.settingsRepository,
                )
            }
        }
    }
}

data class ReplyComposerUiState(
    /** Fixed for the lifetime of the editor; [submissionBody] needs it to link the quoted floor. */
    val postId: Long = 0L,
    val visible: Boolean = false,
    val previewing: Boolean = false,
    val body: String = "",
    val quote: ReplyQuote? = null,
    val savedAtMillis: Long? = null,
    val isPublishing: Boolean = false,
    val publishError: NodeSeekError? = null,
    val publishErrorDetail: String? = null,
    val attachments: List<ImageAttachment> = emptyList(),
    /** The formatting strip's keys and the wrench panel's pool. Defaults until settings arrive. */
    val toolbar: ToolbarLayout = toolbarLayout(emptyList(), EditorActions.Reply),
) {
    val failedUploadCount: Int get() = attachments.count { it.status == UploadStatus.FAILED }

    /** The first failure's reason, for the error strip; retrying clears it along with the status. */
    val uploadFailure: UploadFailure?
        get() = attachments.firstOrNull { it.status == UploadStatus.FAILED }?.failure

    val uploadErrorDetail: String?
        get() = attachments.firstOrNull { it.status == UploadStatus.FAILED }?.errorDetail

    val canPublish: Boolean
        get() = body.isNotBlank() &&
            !isPublishing &&
            attachments.none { it.status == UploadStatus.UPLOADING || it.status == UploadStatus.WAITING }

    /**
     * What actually gets sent: the quote is a chip in the editor but a Markdown blockquote on the
     * wire, because that is how the site's own quote button leaves it in the comment source.
     *
     * The shape is copied from a captured quote rather than invented (sandbox thread, 2026-07-28):
     * a header line naming the author and linking the floor, the quoted text as further blockquote
     * lines, a blank line, then the reply. Getting the order wrong is not cosmetic — the site renders
     * `@name` at the *end* of a blockquote as part of the quotation, so the previous shape read as
     * though the quoted author had signed the reply.
     */
    val submissionBody: String
        get() = quote?.let { quote ->
            buildString {
                append("> @").append(quote.author)
                append(" [#").append(quote.floor).append(']')
                append('(').append(NodeSeekSite.BASE_URL)
                append(NodeSeekSite.postPath(postId)).append('#').append(quote.floor).append(')')
                quote.postedAt?.takeIf(String::isNotBlank)?.let { append(" 发布于").append(it) }
                append('\n')
                append("> ").append(quote.excerpt.replace("\n", "\n> "))
                append("\n\n")
            }
        }.orEmpty() + body
}

private fun ReplyComposerUiState.toDraft() = CommentDraft(
    body = body,
    quotedFloor = quote?.floor,
    quotedAuthor = quote?.author,
    quotedText = quote?.excerpt,
    savedAtMillis = savedAtMillis ?: 0L,
)

private fun CommentDraft.toQuote(): ReplyQuote? {
    val floor = quotedFloor ?: return null
    return ReplyQuote(floor = floor, author = quotedAuthor.orEmpty(), excerpt = quotedText.orEmpty())
}
