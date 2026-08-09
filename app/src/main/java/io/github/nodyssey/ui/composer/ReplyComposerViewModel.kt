package io.github.nodyssey.ui.composer

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.StardustReceiveMarkup
import io.github.nodyssey.core.VoteMarkup
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.VoteRepository
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

/**
 * A floor being answered, as either of the site's two actions takes it.
 *
 * The two are not two flavours of the same thing, and the editor treats them differently on purpose:
 *
 * - 回复 names **one** floor. It becomes [ReplyComposerUiState.replyTo], shown as a chip, and on the
 *   wire it opens the comment with `@name` and a link to that floor. Answering a second floor
 *   replaces the first, because a comment can only address one.
 * - 引用 collects **as many floors as you like**. Each tap drops another blockquote into the body
 *   text, exactly where the site's own 引用 button would put it, and from then on it is ordinary
 *   editable text — reorder it, type between the blocks, delete one.
 */
data class FloorReference(
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
    /**
     * Null wherever the APP menu is not offered — tests, and any build that has not wired it.
     *
     * The reply sheet gained the menu at the same time as the post editor, because the site's own
     * reply box has always had it: 投票 and 收款码 belong to a floor as much as to an opening post.
     */
    private val voteRepository: VoteRepository? = null,
    private val profileRepository: ProfileRepository? = null,
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
     * A [replyTo] passed in wins over the stored one — tapping 回复 on a different floor is a new
     * intent, and silently answering the previous floor would be worse than losing the old context.
     */
    fun open(replyTo: FloorReference? = null) {
        viewModelScope.launch {
            restoreDraft()
            _uiState.update { it.copy(visible = true, previewing = false, replyTo = replyTo ?: it.replyTo) }
        }
    }

    /**
     * Drops another floor into the body as a blockquote, opening the sheet if it was closed.
     *
     * Deliberately text and not state: 引用 is cumulative on this site — a reader quotes one floor,
     * scrolls on, quotes three more — and once the block is in the body it is ordinary Markdown that
     * can be typed between, reordered or deleted like anything else the editor holds. A list of
     * quotes kept beside the text could do none of that, and would have to invent an order for them.
     */
    fun quote(floor: FloorReference) {
        viewModelScope.launch {
            restoreDraft()
            bodyState.editFromViewModel {
                appendBlock(floor.toQuoteBlock(postId))
                placeCursorAtEnd()
            }
            _uiState.update { it.copy(visible = true, previewing = false) }
        }
    }

    /**
     * Puts a stored draft back, once, before the sheet becomes visible.
     *
     * Skipped when something has already been typed: a draft saved earlier must not overwrite the
     * reply in progress, which is also why 引用 can be tapped repeatedly without re-restoring.
     */
    private suspend fun restoreDraft() {
        if (_uiState.value.body.isNotBlank()) return
        val stored = repository.draft(postId).first() ?: return
        bodyState.editFromViewModel {
            replace(0, length, stored.body)
            placeCursorAtEnd()
        }
        _uiState.update {
            it.copy(
                body = stored.body,
                savedAtMillis = stored.savedAtMillis.takeIf { millis -> millis > 0 },
                replyTo = stored.toReplyTarget(),
            )
        }
    }

    /**
     * Creates a vote and splices its marker in at the caret.
     *
     * The post editor's `createVote` with the sheet's own state; see it for why the two steps must
     * not come apart — the vote exists server-side before the body mentions it, so only a success
     * touches [bodyState] and only then does [onInserted] close the dialog.
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
                    bodyState.editFromViewModel { insertText(VoteMarkup.marker(voteId)) }
                    _uiState.update { it.copy(voteCreation = VoteCreationState.Idle) }
                    onInserted()
                }.onFailure { throwable ->
                    _uiState.update {
                        it.copy(voteCreation = VoteCreationState.Failed((throwable as? SiteException)?.detail))
                    }
                }
        }
    }

    /** Puts the creation state back to idle — the dialog closing without having created anything. */
    fun dismissVoteCreation() {
        _uiState.update { it.copy(voteCreation = VoteCreationState.Idle) }
    }

    /** The uid a 收款码 would collect for, or null when the app does not know it yet. */
    fun receiveCodePayeeUid(): Long? = profileRepository?.selfUid

    /** Splices a 收款码 in at the caret. No request; see the post editor's `insertReceiveCode`. */
    fun insertReceiveCode(
        amount: Int,
        refId: Long,
        description: String,
        onetime: Boolean,
    ) {
        val payee = profileRepository?.selfUid ?: return
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

    fun close() {
        _uiState.update { it.copy(visible = false, previewing = false) }
    }

    fun clearReplyTo() = mutate { it.copy(replyTo = null) }

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
                        quotedFloor = state.replyTo?.floor,
                    ),
                )
            }.onSuccess { floor ->
                saveJob?.cancel()
                repository.deleteDraft(postId)
                // The field is not part of the state object, so resetting the state alone would leave
                // the just-published text sitting in the editor for the next floor to inherit.
                bodyState.editFromViewModel { replace(0, length, "") }
                _uiState.value = ReplyComposerUiState(postId = postId)
                uploads.clear()
                onPublished(floor)
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
                    voteRepository = container.voteRepository,
                    profileRepository = container.profileRepository,
                )
            }
        }
    }
}

data class ReplyComposerUiState(
    /** Fixed for the lifetime of the editor; [submissionBody] needs it to link the answered floor. */
    val postId: Long = 0L,
    val visible: Boolean = false,
    val previewing: Boolean = false,
    val body: String = "",
    /** The single floor 回复 addresses. 引用 is not here — it lives in [body] as Markdown. */
    val replyTo: FloorReference? = null,
    val savedAtMillis: Long? = null,
    val isPublishing: Boolean = false,
    val publishError: SiteError? = null,
    val publishErrorDetail: String? = null,
    val attachments: List<ImageAttachment> = emptyList(),
    /** The formatting strip's keys and the wrench panel's pool. Defaults until settings arrive. */
    val toolbar: ToolbarLayout = toolbarLayout(emptyList(), EditorActions.Reply),
    val voteCreation: VoteCreationState = VoteCreationState.Idle,
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
     * What actually gets sent: the body as typed, with the 回复 reference in front of it.
     *
     * Any 引用 blocks are already *in* [body] — [ReplyComposerViewModel.quote] put them there — so
     * this only has the one thing left to add. The shape is the site's, read off a real comment
     * rather than invented (fixture `post-703863-1.html` floor #8):
     * `@ipv4 [#7](/post-703863-1#7) 想要十几刀年付的中盘鸡` — mention, floor link and answer on one
     * line. The mention is the entire point of 回复: it is what puts the answer in the other reader's
     * 回复我的, and what the site folds back into its "@ipv4 #7" reference chip.
     *
     * The one case that cannot share the line is a body that opens with a quote, because a `>` has to
     * start a line to be a blockquote at all. There the reference becomes its own paragraph above it.
     *
     * The floor link stays site-relative for the same reason the rest of the shape does: that is what
     * the site's own editor writes, so a reply from here is indistinguishable from one written there.
     */
    val submissionBody: String
        get() = replyTo?.let { target ->
            val reference = floorReference(postId, target.floor, target.author)
            if (body.trimStart().startsWith(">")) "$reference\n\n" else "$reference "
        }.orEmpty() + body
}

/** `@name [#7](/post-703863-1#7)`, the reference both actions are built out of. */
private fun floorReference(postId: Long, floor: Int, author: String): String = buildString {
    append('@').append(author)
    append(" [#").append(floor).append(']')
    append('(').append(NodeSeekSite.postPath(postId)).append('#').append(floor).append(')')
}

/**
 * One 引用, as the site's own quote button leaves it in the comment source: a header line naming the
 * author, linking the floor and repeating its timestamp, then the quoted text as blockquote lines.
 *
 * Order is not cosmetic — the site renders `@name` at the *end* of a blockquote as part of the
 * quotation, which reads as though the quoted author signed the reply. The trailing blank line is
 * what stops the next thing typed from being swallowed into the quotation as a lazy continuation,
 * and it is also what lets the next 引用 land as its own block rather than extending this one.
 */
internal fun FloorReference.toQuoteBlock(postId: Long): String = buildString {
    append("> ").append(floorReference(postId, floor, author))
    postedAt?.takeIf(String::isNotBlank)?.let { append(" 发布于").append(it) }
    append('\n')
    append("> ").append(excerpt.replace("\n", "\n> "))
    append("\n\n")
}

private fun ReplyComposerUiState.toDraft() = CommentDraft(
    body = body,
    replyToFloor = replyTo?.floor,
    replyToAuthor = replyTo?.author,
    replyToText = replyTo?.excerpt,
    savedAtMillis = savedAtMillis ?: 0L,
)

private fun CommentDraft.toReplyTarget(): FloorReference? {
    val floor = replyToFloor ?: return null
    return FloorReference(floor = floor, author = replyToAuthor.orEmpty(), excerpt = replyToText.orEmpty())
}
