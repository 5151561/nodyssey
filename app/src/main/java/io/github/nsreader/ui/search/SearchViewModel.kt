package io.github.nsreader.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.data.FeedPost
import io.github.nsreader.data.PostRepository
import io.github.nsreader.data.settings.SettingsRepository
import io.github.nsreader.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val posts: PostRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")

    private val results =
        query
            .debounce(180)
            .distinctUntilChanged()
            .flatMapLatest { value ->
                if (value.isBlank()) flowOf(emptyList()) else posts.search(value)
            }

    val uiState: StateFlow<SearchUiState> =
        combine(query, results, settings.settings) { currentQuery, currentResults, userSettings ->
            SearchUiState(
                query = currentQuery,
                recentSearches = userSettings.recentSearches,
                results = currentResults,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SearchUiState(),
        )

    fun updateQuery(value: String) {
        query.value = value
    }

    fun submitSearch() {
        val value = query.value.trim()
        if (value.isEmpty()) return
        query.value = value
        viewModelScope.launch { settings.addRecentSearch(value) }
    }

    fun selectRecentSearch(value: String) {
        query.value = value
        viewModelScope.launch { settings.addRecentSearch(value) }
    }

    fun removeRecentSearch(value: String) {
        viewModelScope.launch { settings.removeRecentSearch(value) }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { settings.clearRecentSearches() }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    SearchViewModel(
                        posts = container.postRepository,
                        settings = container.settingsRepository,
                    )
                }
            }
    }
}

data class SearchUiState(
    val query: String = "",
    val recentSearches: List<String> = emptyList(),
    val results: List<FeedPost> = emptyList(),
)
