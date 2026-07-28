package io.github.nodyssey.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.core.AppClock
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.runCatchingExceptCancellation
import io.github.nodyssey.data.ForumNotification
import io.github.nodyssey.data.MessageConversation
import io.github.nodyssey.data.MessageRepository
import io.github.nodyssey.data.NotificationCategory
import io.github.nodyssey.data.NotificationCounts
import io.github.nodyssey.data.NotificationRepository
import io.github.nodyssey.data.SearchRepository
import io.github.nodyssey.data.UserSearchResult
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.postlist.toNodeSeekError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotificationsViewModel(
    private val repository: NotificationRepository,
    private val messages: MessageRepository,
    private val search: SearchRepository,
    private val session: SessionRepository,
    private val clock: AppClock,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(NotificationsUiState(isSignedIn = session.state.value.isSignedIn))
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var pickerJob: Job? = null

    init {
        session.state
            .distinctUntilChangedBy { it.generation }
            .onEach { value ->
                _uiState.update {
                    if (value.isSignedIn) it.copy(isSignedIn = true) else NotificationsUiState()
                }
                if (value.isSignedIn) refresh()
            }.launchIn(viewModelScope)
    }

    fun selectCategory(category: NotificationCategory) {
        if (_uiState.value.selectedCategory == category) return
        _uiState.update {
            it.copy(selectedCategory = category, items = emptyList(), conversations = emptyList())
        }
        refresh()
    }

    /**
     * Loads the counts plus whichever list the selected group shows.
     *
     * 私信 is not a list of notifications — it is the conversation list of board 7e — so the group
     * decides which repository answers, and the counts call is shared because the chips show all
     * three badges whatever is selected.
     */
    fun refresh() {
        if (!session.state.value.isSignedIn) return
        val category = _uiState.value.selectedCategory
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                runCatchingExceptCancellation {
                    val counts = repository.unreadCounts()
                    if (category == NotificationCategory.MESSAGES) {
                        Loaded(counts, conversations = messages.conversations())
                    } else {
                        Loaded(counts, items = repository.notifications(category))
                    }
                }.onSuccess { loaded ->
                    _uiState.update {
                        it.copy(
                            counts = loaded.counts,
                            items = loaded.items,
                            conversations = loaded.conversations,
                            isLoading = false,
                            error = null,
                            nowMillis = clock.nowMillis(),
                        )
                    }
                }.onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false, error = throwable.toNodeSeekError()) }
                }
            }
    }

    /**
     * Clears the group, optimistically and then for real.
     *
     * The local update lands first because the button should not feel like a network round trip; the
     * request that follows is what makes it survive a refresh. A failure re-reads the server rather
     * than restoring the badges by hand — whatever it says is the truth either way.
     */
    fun markAllRead() {
        val category = _uiState.value.selectedCategory
        _uiState.update { state ->
            state.copy(
                items = state.items.map { it.copy(isUnread = false) },
                conversations = state.conversations.map { it.copy(unreadCount = 0) },
                counts = NotificationCounts(),
            )
        }
        viewModelScope.launch {
            runCatchingExceptCancellation {
                if (category == NotificationCategory.MESSAGES) {
                    messages.markAllRead()
                } else {
                    repository.markAllRead(category)
                }
            }.onFailure { refresh() }
        }
    }

    fun markOpened(id: String) {
        _uiState.update { state ->
            state.copy(items = state.items.map { if (it.id == id) it.copy(isUnread = false) else it })
        }
    }

    fun markConversationOpened(uid: Long) {
        _uiState.update { state ->
            state.copy(
                conversations =
                state.conversations.map { if (it.uid == uid) it.copy(unreadCount = 0) else it },
            )
        }
    }

    fun showNewConversation() {
        _uiState.update { it.copy(newConversation = NewConversationState(isVisible = true)) }
    }

    fun dismissNewConversation() {
        pickerJob?.cancel()
        _uiState.update { it.copy(newConversation = NewConversationState()) }
    }

    fun updateNewConversationQuery(value: String) {
        _uiState.update { it.copy(newConversation = it.newConversation.copy(query = value)) }
    }

    fun searchRecipients() {
        val query = _uiState.value.newConversation.query.trim()
        if (query.isEmpty()) return
        pickerJob?.cancel()
        pickerJob =
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        newConversation =
                        it.newConversation.copy(isSearching = true, error = null, results = emptyList()),
                    )
                }
                runCatchingExceptCancellation { search.searchUsers(query) }
                    .onSuccess { users ->
                        _uiState.update {
                            it.copy(
                                newConversation =
                                it.newConversation.copy(isSearching = false, results = users),
                            )
                        }
                    }.onFailure { throwable ->
                        _uiState.update {
                            it.copy(
                                newConversation =
                                it.newConversation.copy(
                                    isSearching = false,
                                    error = throwable.toNodeSeekError(),
                                ),
                            )
                        }
                    }
            }
    }

    private data class Loaded(
        val counts: NotificationCounts,
        val items: List<ForumNotification> = emptyList(),
        val conversations: List<MessageConversation> = emptyList(),
    )

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    NotificationsViewModel(
                        repository = container.notificationRepository,
                        messages = container.messageRepository,
                        search = container.searchRepository,
                        session = container.sessionRepository,
                        clock = container.clock,
                    )
                }
            }
    }
}

/**
 * The 新建私信 picker (board 7e's FAB).
 *
 * Marked "App 增强" on the board: the site has no way to start a conversation with someone you have
 * never talked to, so this reuses the member-search endpoint to pick a recipient.
 */
data class NewConversationState(
    val isVisible: Boolean = false,
    val query: String = "",
    val results: List<UserSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val error: NodeSeekError? = null,
)

data class NotificationsUiState(
    val isSignedIn: Boolean = false,
    val selectedCategory: NotificationCategory = NotificationCategory.MENTIONS,
    val counts: NotificationCounts = NotificationCounts(),
    val items: List<ForumNotification> = emptyList(),
    val conversations: List<MessageConversation> = emptyList(),
    val isLoading: Boolean = false,
    val error: NodeSeekError? = null,
    /** Stamped when the list loaded, so relative labels stay stable across recomposition. */
    val nowMillis: Long = 0L,
    val newConversation: NewConversationState = NewConversationState(),
) {
    val hasUnread: Boolean
        get() = items.any(ForumNotification::isUnread) || conversations.any { it.unreadCount > 0 }

    val isEmpty: Boolean
        get() =
            if (selectedCategory == NotificationCategory.MESSAGES) {
                conversations.isEmpty()
            } else {
                items.isEmpty()
            }
}
