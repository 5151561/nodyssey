package io.github.nodyssey.ui.messages

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.DirectMessage
import io.github.nodyssey.data.MessageRepository
import io.github.nodyssey.data.NotificationCategory
import io.github.nodyssey.data.NotificationRepository
import io.github.nodyssey.data.composer.ImageAttachment
import io.github.nodyssey.data.composer.ImageUploadQueue
import io.github.nodyssey.data.composer.ImageUploader
import io.github.nodyssey.data.composer.PickedImage
import io.github.nodyssey.data.composer.UploadStatus
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.data.settings.ComposerSurface
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.composer.EditorActions
import io.github.nodyssey.ui.postlist.toSiteError
import io.github.plaza.core.AppClock
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.runCatchingExceptCancellation
import io.github.plaza.designsys.editor.EditorAction
import io.github.plaza.designsys.editor.ToolbarCustomizeSheet
import io.github.plaza.designsys.editor.ToolbarLayout
import io.github.plaza.designsys.editor.appendBlock
import io.github.plaza.designsys.editor.editFromViewModel
import io.github.plaza.designsys.editor.removeBlock
import io.github.plaza.designsys.editor.toolbarLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Board 7f.
 *
 * The delivery states are the app's, not the site's: NodeSeek posts a message and re-renders the
 * page, so there is nothing to observe between "typed" and "there". Keeping the optimistic bubble in
 * the list — rather than clearing the field and waiting — is what makes a failed send recoverable
 * without the user having retyped anything.
 */
class MessageThreadViewModel(
    private val repository: MessageRepository,
    private val notifications: NotificationRepository,
    private val session: SessionRepository,
    private val clock: AppClock,
    uploader: ImageUploader,
    uid: Long,
    userName: String,
    /** Null in tests; the bar then shows [EditorActions.Message] and offers no wrench. */
    private val settings: SettingsRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MessageThreadUiState(uid = uid, userName = userName))
    val uiState: StateFlow<MessageThreadUiState> = _uiState.asStateFlow()

    /** The composer's text and selection, held here for the same reason the post editor's is. */
    val draftState = TextFieldState()

    /**
     * The same queue the two composers use, and the same NodeImage host behind it.
     *
     * There is nothing message-shaped about an image here: `message/send` carries one `content`
     * string, so an image in a private message is `![](…)` spliced into that string exactly the way
     * it is spliced into a topic's body.
     */
    private val uploads = ImageUploadQueue(viewModelScope, uploader)
    private var loadJob: Job? = null
    private var pendingSeed = 0L

    init {
        refresh()
        // The text lives in a state object the ViewModel cannot make a StateFlow out of, so the one
        // derived bit it needs is mirrored across. Whether that is *enough* to send is the UiState's
        // question, because an upload still in flight also has a say.
        snapshotFlow { draftState.text.isNotBlank() }
            .onEach { hasText -> _uiState.update { it.copy(hasDraftText = hasText) } }
            .launchIn(viewModelScope)
        uploads.attachments
            .onEach { attachments -> _uiState.update { it.copy(attachments = attachments) } }
            .launchIn(viewModelScope)
        // Appended rather than spliced at the caret: the upload lands while typing continues, and
        // dropping `![](…)` mid-sentence is both surprising and hard to undo.
        uploads.uploaded
            .onEach { attachment ->
                val markdown = attachment.markdown ?: return@onEach
                draftState.editFromViewModel { appendBlock(markdown) }
            }.launchIn(viewModelScope)
        settings?.settings
            ?.map { it.messageToolbarActions }
            ?.distinctUntilChanged()
            ?.onEach { stored ->
                _uiState.update { it.copy(toolbar = toolbarLayout(stored, EditorActions.Message)) }
            }?.launchIn(viewModelScope)
    }

    fun addImages(images: List<PickedImage>) = uploads.enqueue(images)

    fun retryUpload(attachment: ImageAttachment) = uploads.retry(attachment.id)

    fun retryFailedUploads() = uploads.retryFailed()

    /** Dismissing a finished upload also takes its Markdown out of the draft it was written into. */
    fun removeAttachment(attachment: ImageAttachment) {
        val removed = uploads.remove(attachment.id) ?: return
        val markdown = removed.markdown ?: return
        draftState.editFromViewModel { removeBlock(markdown) }
    }

    /** Writes the strip through on every edit in the wrench panel; see `ToolbarCustomizeSheet`. */
    fun setToolbar(actions: List<EditorAction>) {
        val repository = settings ?: return
        viewModelScope.launch {
            repository.setComposerToolbar(ComposerSurface.MESSAGE, actions.map(EditorAction::name))
        }
    }

    /** Clears the stored arrangement, which is what makes the defaults come back. */
    fun resetToolbar() {
        val repository = settings ?: return
        viewModelScope.launch { repository.setComposerToolbar(ComposerSurface.MESSAGE, emptyList()) }
    }

    fun refresh() {
        if (!session.state.value.isSignedIn) {
            _uiState.update { it.copy(error = SiteError.LoginRequired) }
            return
        }
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                runCatchingExceptCancellation { repository.thread(_uiState.value.uid) }
                    .onSuccess { thread ->
                        _uiState.update { state ->
                            state.copy(
                                // The server list replaces the delivered history but must not drop a
                                // bubble that is still in flight or waiting to be retried.
                                messages =
                                thread.messages.map(::delivered) +
                                    state.messages.filter { it.status != SendStatus.SENT },
                                userName = thread.userName.ifBlank { state.userName },
                                avatarUrl = thread.avatarUrl,
                                level = thread.level,
                                isLoading = false,
                                error = null,
                                nowMillis = clock.nowMillis(),
                            )
                        }
                        markRead(thread.unreadIds)
                    }.onFailure { throwable ->
                        _uiState.update {
                            it.copy(isLoading = false, error = throwable.toSiteError())
                        }
                    }
            }
    }

    /**
     * Tells the server the conversation has been read, then re-reads the badge.
     *
     * Fetching the history is not what clears it — the site posts the ids back and only then does
     * `unread-count` drop, which is why the 私信 badge used to outlive reading the message.
     */
    private fun markRead(ids: List<Long>) {
        if (ids.isEmpty()) return
        notifications.noteRead(NotificationCategory.MESSAGES, ids.size)
        viewModelScope.launch {
            runCatchingExceptCancellation {
                repository.markRead(ids)
                notifications.refreshCounts()
            }
        }
    }

    fun toggleMarkdown() {
        _uiState.update { it.copy(isMarkdown = !it.isMarkdown) }
    }

    fun send() {
        val content = draftState.text.toString().trim()
        if (content.isEmpty()) return
        val bubble =
            MessageBubble(
                id = "pending-${pendingSeed++}",
                isMine = true,
                content = content,
                isMarkdown = _uiState.value.isMarkdown,
                sentAtMillis = clock.nowMillis(),
                sentAtText = null,
                status = SendStatus.SENDING,
            )
        draftState.clearText()
        // The Markdown went out inside `content`; the queue's cells were only ever the progress
        // report for getting it there, and leaving them up would attach them to the next message.
        uploads.clear()
        _uiState.update {
            it.copy(messages = it.messages + bubble, nowMillis = clock.nowMillis())
        }
        deliver(bubble)
    }

    fun retry(id: String) {
        val bubble = _uiState.value.messages.firstOrNull { it.id == id } ?: return
        if (bubble.status != SendStatus.FAILED) return
        replace(id) { it.copy(status = SendStatus.SENDING) }
        deliver(bubble)
    }

    private fun deliver(bubble: MessageBubble) {
        viewModelScope.launch {
            runCatchingExceptCancellation {
                repository.send(
                    uid = _uiState.value.uid,
                    content = bubble.content,
                    markdown = _uiState.value.isMarkdown,
                )
            }.onSuccess { accepted ->
                replace(bubble.id) {
                    // Whatever the echo says, this bubble is ours: an ack without a sender must not
                    // send the message we just wrote over to the other side of the thread.
                    accepted?.let(::delivered)?.copy(id = bubble.id, isMine = true)
                        ?: it.copy(status = SendStatus.SENT)
                }
            }.onFailure { throwable ->
                replace(bubble.id) {
                    it.copy(
                        status = SendStatus.FAILED,
                        // "对方已屏蔽你" is not something retrying will fix, and it is the only way
                        // the user finds that out — a bare 发送失败 invites tapping 重试 forever.
                        failureReason = (throwable as? SiteException)?.detail,
                    )
                }
            }
        }
    }

    private fun replace(
        id: String,
        transform: (MessageBubble) -> MessageBubble,
    ) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { if (it.id == id) transform(it) else it },
                nowMillis = clock.nowMillis(),
            )
        }
    }

    private fun delivered(message: DirectMessage) =
        MessageBubble(
            id = message.id,
            isMine = message.isMine,
            content = message.content,
            isMarkdown = message.isMarkdown,
            sentAtMillis = message.sentAtMillis,
            sentAtText = message.sentAtText,
            status = SendStatus.SENT,
        )

    companion object {
        fun factory(
            container: AppContainer,
            uid: Long,
            userName: String,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    MessageThreadViewModel(
                        repository = container.messageRepository,
                        notifications = container.notificationRepository,
                        session = container.sessionRepository,
                        clock = container.clock,
                        uploader = container.imageUploader,
                        uid = uid,
                        userName = userName,
                        settings = container.settingsRepository,
                    )
                }
            }
    }
}

enum class SendStatus {
    SENT,
    SENDING,
    FAILED,
}

data class MessageBubble(
    val id: String,
    val isMine: Boolean,
    val content: String,
    /** The site records this per message, so an old plain message stays plain. */
    val isMarkdown: Boolean,
    val sentAtMillis: Long?,
    val sentAtText: String?,
    val status: SendStatus,
    /** The server's own words for a rejection, when it gave any. */
    val failureReason: String? = null,
)

data class MessageThreadUiState(
    val uid: Long,
    val userName: String,
    val avatarUrl: String? = null,
    val level: Int? = null,
    val messages: List<MessageBubble> = emptyList(),
    val isLoading: Boolean = false,
    val error: SiteError? = null,
    /** Mirrored out of [MessageThreadViewModel.draftState]; the text itself is not UiState. */
    val hasDraftText: Boolean = false,
    val attachments: List<ImageAttachment> = emptyList(),
    /** The site's own MD On/Off switch; off sends the text verbatim. */
    val isMarkdown: Boolean = true,
    /** The formatting strip's keys and the wrench panel's pool. Defaults until settings arrive. */
    val toolbar: ToolbarLayout = toolbarLayout(emptyList(), EditorActions.Message),
    val nowMillis: Long = 0L,
) {
    /**
     * Sending an image that has not finished uploading would send its placeholder.
     *
     * The same guard the two composers put on 发布, for the same reason: the Markdown carries a URL
     * the upload has not produced yet, and a message is gone the moment it is sent.
     */
    val canSend: Boolean
        get() = hasDraftText &&
            attachments.none { it.status == UploadStatus.UPLOADING || it.status == UploadStatus.WAITING }
}
