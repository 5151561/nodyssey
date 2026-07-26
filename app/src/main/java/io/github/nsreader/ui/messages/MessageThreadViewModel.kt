package io.github.nsreader.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.core.AppClock
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.runCatchingExceptCancellation
import io.github.nsreader.data.DirectMessage
import io.github.nsreader.data.MessageRepository
import io.github.nsreader.data.session.SessionRepository
import io.github.nsreader.di.AppContainer
import io.github.nsreader.ui.postlist.toNodeSeekError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val session: SessionRepository,
    private val clock: AppClock,
    uid: Long,
    userName: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MessageThreadUiState(uid = uid, userName = userName))
    val uiState: StateFlow<MessageThreadUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var pendingSeed = 0L

    init {
        refresh()
    }

    fun refresh() {
        if (!session.state.value.isSignedIn) {
            _uiState.update { it.copy(error = NodeSeekError.LoginRequired) }
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
                    }.onFailure { throwable ->
                        _uiState.update {
                            it.copy(isLoading = false, error = throwable.toNodeSeekError())
                        }
                    }
            }
    }

    fun updateDraft(value: String) {
        _uiState.update { it.copy(draft = value) }
    }

    fun toggleMarkdown() {
        _uiState.update { it.copy(isMarkdown = !it.isMarkdown) }
    }

    fun send() {
        val content = _uiState.value.draft.trim()
        if (content.isEmpty()) return
        val bubble =
            MessageBubble(
                id = "pending-${pendingSeed++}",
                isMine = true,
                content = content,
                sentAtMillis = clock.nowMillis(),
                sentAtText = null,
                isEdited = false,
                status = SendStatus.SENDING,
            )
        _uiState.update {
            it.copy(draft = "", messages = it.messages + bubble, nowMillis = clock.nowMillis())
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
                    accepted?.let(::delivered)?.copy(id = bubble.id)
                        ?: it.copy(status = SendStatus.SENT)
                }
            }.onFailure { throwable ->
                replace(bubble.id) {
                    it.copy(status = SendStatus.FAILED, failure = throwable.toNodeSeekError())
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
            sentAtMillis = message.sentAtMillis,
            sentAtText = message.sentAtText,
            isEdited = message.isEdited,
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
                        session = container.sessionRepository,
                        clock = container.clock,
                        uid = uid,
                        userName = userName,
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
    val sentAtMillis: Long?,
    val sentAtText: String?,
    val isEdited: Boolean,
    val status: SendStatus,
    val failure: NodeSeekError? = null,
)

data class MessageThreadUiState(
    val uid: Long,
    val userName: String,
    val avatarUrl: String? = null,
    val level: Int? = null,
    val messages: List<MessageBubble> = emptyList(),
    val isLoading: Boolean = false,
    val error: NodeSeekError? = null,
    val draft: String = "",
    /** The site's own MD On/Off switch; off sends the text verbatim. */
    val isMarkdown: Boolean = true,
    val nowMillis: Long = 0L,
) {
    val canSend: Boolean get() = draft.isNotBlank()
}
