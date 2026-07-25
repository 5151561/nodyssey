package io.github.nsreader.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.runCatchingExceptCancellation
import io.github.nsreader.data.ForumNotification
import io.github.nsreader.data.NotificationCategory
import io.github.nsreader.data.NotificationCounts
import io.github.nsreader.data.NotificationRepository
import io.github.nsreader.data.session.SessionRepository
import io.github.nsreader.di.AppContainer
import io.github.nsreader.ui.postlist.toNodeSeekError
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
    private val session: SessionRepository,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(NotificationsUiState(isSignedIn = session.state.value.isSignedIn))
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

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
        _uiState.update { it.copy(selectedCategory = category, items = emptyList()) }
        refresh()
    }

    fun refresh() {
        if (!session.state.value.isSignedIn) return
        val category = _uiState.value.selectedCategory
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                runCatchingExceptCancellation {
                    val counts = repository.unreadCounts()
                    val items = repository.notifications(category)
                    counts to items
                }.onSuccess { (counts, items) ->
                    _uiState.update {
                        it.copy(counts = counts, items = items, isLoading = false, error = null)
                    }
                }.onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false, error = throwable.toNodeSeekError()) }
                }
            }
    }

    /**
     * The observed endpoint marks notifications as they are opened; this action clears the current
     * presentation immediately and the next refresh reconciles with the server's authoritative count.
     */
    fun markAllRead() {
        _uiState.update { state ->
            state.copy(
                items = state.items.map { it.copy(isUnread = false) },
                counts = NotificationCounts(),
            )
        }
    }

    fun markOpened(id: String) {
        _uiState.update { state ->
            state.copy(items = state.items.map { if (it.id == id) it.copy(isUnread = false) else it })
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    NotificationsViewModel(
                        repository = container.notificationRepository,
                        session = container.sessionRepository,
                    )
                }
            }
    }
}

data class NotificationsUiState(
    val isSignedIn: Boolean = false,
    val selectedCategory: NotificationCategory = NotificationCategory.REPLIES,
    val counts: NotificationCounts = NotificationCounts(),
    val items: List<ForumNotification> = emptyList(),
    val isLoading: Boolean = false,
    val error: NodeSeekError? = null,
)
