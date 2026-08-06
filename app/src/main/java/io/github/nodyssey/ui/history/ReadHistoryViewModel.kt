package io.github.nodyssey.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.PostRepository
import io.github.nodyssey.data.ReadHistoryEntry
import io.github.nodyssey.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReadHistoryUiState(
    val entries: List<ReadHistoryEntry> = emptyList(),
    /** True until the first database emission; an empty list before that is not yet an empty history. */
    val isLoading: Boolean = true,
)

/**
 * 浏览历史 — every thread this device has opened, newest first.
 *
 * Purely local. NodeSeek keeps no such list, so there is nothing to fetch, nothing to refresh and no
 * failure state: the database either has rows or it does not.
 *
 * The rows are the same ones that make "4 条新回复" work on the feed. That sharing is why clearing
 * the history is a confirmed action rather than a plain button — see the screen.
 */
class ReadHistoryViewModel(
    private val repository: PostRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReadHistoryUiState())
    val uiState: StateFlow<ReadHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.readHistory().collect { entries ->
                _uiState.update { it.copy(entries = entries, isLoading = false) }
            }
        }
    }

    fun remove(postId: Long) {
        viewModelScope.launch { repository.removeFromHistory(postId) }
    }

    fun clear() {
        viewModelScope.launch { repository.clearReadHistory() }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ReadHistoryViewModel(container.postRepository) }
            }
    }
}
