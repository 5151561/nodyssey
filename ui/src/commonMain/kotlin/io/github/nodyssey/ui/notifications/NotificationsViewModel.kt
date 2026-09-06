package io.github.nodyssey.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.ForumNotification
import io.github.nodyssey.data.MessageConversation
import io.github.nodyssey.data.MessageRepository
import io.github.nodyssey.data.NotificationCategory
import io.github.nodyssey.data.NotificationCounts
import io.github.nodyssey.data.NotificationRepository
import io.github.nodyssey.data.NotificationTab
import io.github.nodyssey.data.SearchRepository
import io.github.nodyssey.data.UserSearchResult
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.postlist.toSiteError
import io.github.plaza.core.AppClock
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.runCatchingExceptCancellation
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
                // Signing out has to empty the badge at the source too, or the counts the previous
                // account was carrying stay in the repository waiting to be re-emitted.
                if (value.isSignedIn) refresh() else repository.clearCounts()
            }.launchIn(viewModelScope)
        // The badges are the repository's, not this screen's: a conversation read on board 7f has to
        // move them too, and that happens while this view model is doing nothing at all.
        repository.counts
            .onEach { counts -> _uiState.update { it.copy(counts = counts) } }
            .launchIn(viewModelScope)
    }

    fun selectTab(tab: NotificationTab) {
        if (_uiState.value.selectedTab == tab) return
        _uiState.update {
            it.copy(selectedTab = tab, items = emptyList(), conversations = emptyList())
        }
        refresh()
    }

    /**
     * Loads the counts plus whichever list the selected tab shows.
     *
     * 私信 is not a list of notifications — it is the conversation list of board 7e — so the tab
     * decides which repository answers, and the counts call is shared because the chips show both
     * badges whatever is selected.
     */
    fun refresh() {
        if (!session.state.value.isSignedIn) return
        val tab = _uiState.value.selectedTab
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                runCatchingExceptCancellation {
                    // The counts land on screen through the repository's own flow, which is also
                    // what the merged load corrects a moment later — copying the returned value into
                    // the state here would race that correction and sometimes win.
                    repository.refreshCounts()
                    if (tab == NotificationTab.MESSAGES) {
                        Loaded(conversations = messages.conversations())
                    } else {
                        Loaded(items = repository.interactions())
                    }
                }.onSuccess { loaded ->
                    _uiState.update {
                        it.copy(
                            items = loaded.items,
                            conversations = loaded.conversations,
                            isLoading = false,
                            error = null,
                            nowMillis = clock.nowMillis(),
                        )
                    }
                }.onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false, error = throwable.toSiteError()) }
                }
            }
    }

    /**
     * [refresh], unless what is on screen is recent enough to stand.
     *
     * Called every time the screen comes back into view — the tab bar, Back from a thread, the app
     * returning to the foreground. This view model lives at the navigation root and survives all of
     * those, so nothing recreates it and init's one refresh was the only load a session ever ran.
     * The throttle is what keeps tab-hopping from turning into a request per tap; only a successful
     * load stamps [NotificationsUiState.nowMillis], so a failed one retries on the next return
     * instead of waiting out the interval.
     */
    fun refreshIfStale() {
        if (loadJob?.isActive == true) return
        if (clock.nowMillis() - _uiState.value.nowMillis < REFRESH_THROTTLE_MILLIS) return
        refresh()
    }

    /**
     * Clears the group, optimistically and then for real.
     *
     * The local update lands first because the button should not feel like a network round trip; the
     * request that follows is what makes it survive a refresh. A failure re-reads the server rather
     * than restoring the badges by hand — whatever it says is the truth either way.
     */
    fun markAllRead() {
        val tab = _uiState.value.selectedTab
        val state = _uiState.value
        // Only the groups the button belongs to. Zeroing the whole `NotificationCounts` used to wipe
        // every badge, so 全部已读 on 通知 also claimed the unread 私信 had been read.
        tab.categories.forEach { category ->
            repository.noteRead(category = category, count = state.counts.forCategory(category))
        }
        _uiState.update {
            it.copy(
                items = it.items.map(ForumNotification::read),
                conversations = it.conversations.map { row -> row.copy(unreadCount = 0) },
            )
        }
        viewModelScope.launch {
            runCatchingExceptCancellation {
                if (tab == NotificationTab.MESSAGES) {
                    messages.markAllRead()
                    repository.refreshCounts()
                } else {
                    // Both endpoints: the site has no "all groups" call, and the list on screen is
                    // the two of them.
                    tab.categories.forEach { repository.markAllRead(it) }
                }
            }.onFailure { refresh() }
        }
    }

    /**
     * Opening a row is a read, on the server as well as on screen.
     *
     * Only greying the row out locally was the whole of bug 1: the badge is the count endpoint's
     * answer, the count endpoint had never been told, and so the number that sent the user here
     * survived being acted on — through a refresh, and through a restart.
     */
    fun markOpened(id: String) {
        val item = _uiState.value.items.firstOrNull { it.id == id } ?: return
        _uiState.update { state ->
            state.copy(items = state.items.map { if (it.id == id) it.read() else it })
        }
        if (!item.isUnread) return
        viewModelScope.launch {
            // Every group the row arrived in, not just the one whose sentence it is showing: a
            // folded row is two unread rows on the server.
            runCatchingExceptCancellation { repository.markViewed(item.sources) }
        }
    }

    /**
     * The badge half of opening a conversation; board 7f posts the message ids once it has them.
     *
     * The list row knows how many are unread but not which, because `message/list` carries one row
     * per conversation — so this moves the badge now and the thread settles it against the server a
     * moment later.
     */
    fun markConversationOpened(uid: Long) {
        val conversation = _uiState.value.conversations.firstOrNull { it.uid == uid } ?: return
        repository.noteRead(NotificationCategory.MESSAGES, conversation.unreadCount)
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
                                    error = throwable.toSiteError(),
                                ),
                            )
                        }
                    }
            }
    }

    private data class Loaded(
        val items: List<ForumNotification> = emptyList(),
        val conversations: List<MessageConversation> = emptyList(),
    )

    companion object {
        /** How recent "recent enough" is; see [refreshIfStale]. */
        private const val REFRESH_THROTTLE_MILLIS = 30_000L

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
    val error: SiteError? = null,
)

data class NotificationsUiState(
    val isSignedIn: Boolean = false,
    val selectedTab: NotificationTab = NotificationTab.INTERACTIONS,
    val counts: NotificationCounts = NotificationCounts(),
    val items: List<ForumNotification> = emptyList(),
    val conversations: List<MessageConversation> = emptyList(),
    val isLoading: Boolean = false,
    val error: SiteError? = null,
    /** Stamped when the list loaded, so relative labels stay stable across recomposition. */
    val nowMillis: Long = 0L,
    val newConversation: NewConversationState = NewConversationState(),
) {
    val hasUnread: Boolean
        get() = items.any(ForumNotification::isUnread) || conversations.any { it.unreadCount > 0 }

    val isEmpty: Boolean
        get() =
            if (selectedTab == NotificationTab.MESSAGES) {
                conversations.isEmpty()
            } else {
                items.isEmpty()
            }
}
