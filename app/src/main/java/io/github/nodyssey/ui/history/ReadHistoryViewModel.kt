package io.github.nodyssey.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.PostRepository
import io.github.nodyssey.data.ReadHistoryEntry
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReadHistoryUiState(
    val entries: List<ReadHistoryEntry> = emptyList(),
    /** True until the first database emission; an empty list before that is not yet an empty history. */
    val isLoading: Boolean = true,
    /** 保留条数, as the picker shows it. [SettingsRepository.READ_HISTORY_UNLIMITED] means 无上限. */
    val limit: Int = SettingsRepository.DEFAULT_READ_HISTORY_LIMIT,
)

/**
 * 浏览历史 — every thread this device has opened, newest first.
 *
 * Purely local. NodeSeek keeps no such list, so there is nothing to fetch, nothing to refresh and no
 * failure state: the database either has rows or it does not.
 *
 * The rows are the same ones that make "4 条新回复" work on the feed. That sharing is why clearing
 * the history is a confirmed action rather than a plain button, why a single removal offers 撤销, and
 * why the 保留条数 picker says out loud what a smaller number costs — see the screen.
 */
class ReadHistoryViewModel(
    private val repository: PostRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReadHistoryUiState())
    val uiState: StateFlow<ReadHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.readHistory().collect { entries ->
                _uiState.update { it.copy(entries = entries, isLoading = false) }
            }
        }
        viewModelScope.launch {
            settings.settings
                .map { it.readHistoryLimit }
                .distinctUntilChanged()
                .collect { limit -> _uiState.update { it.copy(limit = limit) } }
        }
    }

    fun remove(entry: ReadHistoryEntry) {
        viewModelScope.launch { repository.removeFromHistory(entry.postId) }
    }

    /** Undo for [remove]. See [PostRepository.restoreToHistory] for what comes back and what does not. */
    fun restore(entry: ReadHistoryEntry) {
        viewModelScope.launch { repository.restoreToHistory(entry) }
    }

    fun clear() {
        viewModelScope.launch { repository.clearReadHistory() }
    }

    /**
     * Stores the new 保留条数 and applies it at once.
     *
     * The trim is not decoration: rows past the limit would otherwise keep greying out their feed
     * rows until the next thread is opened, so the setting would look like it had not taken.
     */
    fun setLimit(limit: Int) {
        viewModelScope.launch {
            settings.setReadHistoryLimit(limit)
            repository.trimReadHistory()
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ReadHistoryViewModel(container.postRepository, container.settingsRepository) }
            }
    }
}
