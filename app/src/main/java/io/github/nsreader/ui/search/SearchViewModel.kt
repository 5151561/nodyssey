package io.github.nsreader.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.data.Board
import io.github.nsreader.data.CategoryRepository
import io.github.nsreader.data.FeedPost
import io.github.nsreader.data.SearchRepository
import io.github.nsreader.data.UserSearchResult
import io.github.nsreader.data.settings.SettingsRepository
import io.github.nsreader.di.AppContainer
import io.github.nsreader.model.SearchHistoryEntry
import io.github.nsreader.model.SearchSort
import io.github.nsreader.model.SearchTarget
import io.github.nsreader.ui.postlist.toNodeSeekError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val categoryRepository: CategoryRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // One job per target: paging posts must not cancel a user lookup, and vice versa.
    private var postJob: Job? = null
    private var userJob: Job? = null

    init {
        categoryRepository.boards
            .onEach { boards -> _uiState.update { it.copy(boards = boards.filter { board -> board.slug != null }) } }
            .launchIn(viewModelScope)
        settings.settings
            .onEach { userSettings ->
                _uiState.update {
                    it.copy(
                        searchHistory = userSettings.searchHistory,
                        recentBoards = userSettings.recentBoards,
                    )
                }
            }.launchIn(viewModelScope)
        viewModelScope.launch { categoryRepository.refreshIfNeeded() }
    }

    fun updateQuery(value: String) {
        val invalidatesSubmittedSearch = value != _uiState.value.submittedQuery
        _uiState.update {
            if (invalidatesSubmittedSearch) {
                it.copy(
                    query = value,
                    submittedQuery = null,
                    postLoadState = SearchLoadState.Idle,
                    userLoadState = SearchLoadState.Idle,
                )
            } else {
                it.copy(query = value)
            }
        }
        if (invalidatesSubmittedSearch) {
            postJob?.cancel()
            userJob?.cancel()
        }
    }

    fun selectTarget(target: SearchTarget) {
        _uiState.update { it.copy(target = target) }
        // The other tab's search runs lazily, on first visit: a posts search must not spend a user
        // API call (and the reverse) for a tab the user may never open.
        val state = _uiState.value
        val submitted = state.submittedQuery ?: return
        val loadState = if (target == SearchTarget.POSTS) state.postLoadState else state.userLoadState
        if (loadState == SearchLoadState.Idle) startSearch(target, submitted)
    }

    fun submitSearch() {
        val state = _uiState.value
        executeSearch(
            query = state.query,
            historyEntry =
            SearchHistoryEntry(
                query = state.query,
                target = state.target,
                categorySlugs = if (state.target == SearchTarget.POSTS) state.selectedBoards else emptySet(),
                sort = state.sort,
            ),
        )
    }

    fun selectHistory(entry: SearchHistoryEntry) {
        _uiState.update {
            it.copy(
                query = entry.query,
                target = entry.target,
                selectedBoards = entry.categorySlugs,
                sort = entry.sort,
            )
        }
        executeSearch(entry.query, entry)
    }

    fun removeHistory(entry: SearchHistoryEntry) {
        viewModelScope.launch { settings.removeSearchHistory(entry) }
    }

    fun clearHistory() {
        val target = _uiState.value.target
        viewModelScope.launch { settings.clearSearchHistory(target) }
    }

    fun setBoards(boards: Set<String>) {
        _uiState.update { it.copy(selectedBoards = boards) }
        viewModelScope.launch { settings.recordRecentBoards(boards) }
        rerunSubmittedSearch()
    }

    fun selectSort(sort: SearchSort) {
        if (_uiState.value.sort == sort) return
        _uiState.update { it.copy(sort = sort) }
        rerunSubmittedSearch()
    }

    fun retry() {
        val state = _uiState.value
        executeSearch(
            query = state.submittedQuery ?: state.query,
            historyEntry = null,
        )
    }

    fun challengeUrl(): String =
        NodeSeekSite.BASE_URL +
            NodeSeekSite.postSearchPath(
                query = _uiState.value.submittedQuery ?: _uiState.value.query,
                categorySlug = _uiState.value.selectedBoards.singleOrNull(),
                sort = if (_uiState.value.sort == SearchSort.TIME) io.github.nsreader.model.FeedSort.POST_TIME else io.github.nsreader.model.FeedSort.LAST_REPLY,
            )

    private fun rerunSubmittedSearch() {
        val state = _uiState.value
        if (state.submittedQuery == null) return
        executeSearch(
            query = state.submittedQuery,
            historyEntry =
            SearchHistoryEntry(
                query = state.submittedQuery,
                target = SearchTarget.POSTS,
                categorySlugs = state.selectedBoards,
                sort = state.sort,
            ),
        )
    }

    /** Appends the next server page of post results; a no-op while one is already on its way. */
    fun loadMorePosts() {
        val state = _uiState.value
        val submitted = state.submittedQuery ?: return
        if (!state.postHasNext || state.isAppendingPosts) return
        if (state.postLoadState != SearchLoadState.Success) return
        _uiState.update { it.copy(isAppendingPosts = true) }
        postJob =
            viewModelScope.launch {
                try {
                    val next =
                        searchRepository.searchPosts(
                            query = submitted,
                            page = state.postPage + 1,
                            categorySlugs = state.selectedBoards,
                            sort = state.sort,
                        )
                    _uiState.update { current ->
                        val merged =
                            (current.postResults.map(FeedPost::summary) + next.posts)
                                .distinctBy { it.postId }
                        current.copy(
                            postResults = merged.map { FeedPost(it, isRead = false, newCommentCount = 0) },
                            postPage = next.page,
                            postTotalPages = next.totalPages,
                            postHasNext = next.hasNextPage,
                            isAppendingPosts = false,
                        )
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (throwable: Throwable) {
                    // The loaded prefix stays useful; the next scroll to the end simply tries again.
                    _uiState.update { it.copy(isAppendingPosts = false) }
                }
            }
    }

    private fun executeSearch(
        query: String,
        historyEntry: SearchHistoryEntry?,
    ) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return
        postJob?.cancel()
        userJob?.cancel()
        _uiState.update {
            it.copy(
                query = normalized,
                submittedQuery = normalized,
                postResults = emptyList(),
                userResults = emptyList(),
                postPage = 1,
                postTotalPages = 1,
                postHasNext = false,
                isAppendingPosts = false,
                postLoadState = SearchLoadState.Idle,
                userLoadState = SearchLoadState.Idle,
            )
        }
        historyEntry?.copy(query = normalized)?.let { entry ->
            viewModelScope.launch { settings.addSearchHistory(entry) }
        }
        startSearch(_uiState.value.target, normalized)
    }

    private fun startSearch(target: SearchTarget, query: String) {
        when (target) {
            SearchTarget.POSTS -> {
                val selectedBoards = _uiState.value.selectedBoards
                val sort = _uiState.value.sort
                _uiState.update { it.copy(postLoadState = SearchLoadState.Loading) }
                postJob =
                    viewModelScope.launch {
                        try {
                            val results =
                                searchRepository.searchPosts(
                                    query = query,
                                    page = 1,
                                    categorySlugs = selectedBoards,
                                    sort = sort,
                                )
                            _uiState.update {
                                it.copy(
                                    postResults =
                                    results.posts.map { summary ->
                                        FeedPost(summary, isRead = false, newCommentCount = 0)
                                    },
                                    postPage = results.page,
                                    postTotalPages = results.totalPages,
                                    postHasNext = results.hasNextPage,
                                    postLoadState = SearchLoadState.Success,
                                )
                            }
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (throwable: Throwable) {
                            _uiState.update { it.copy(postLoadState = SearchLoadState.Error(throwable.toNodeSeekError())) }
                        }
                    }
            }

            SearchTarget.USERS -> {
                _uiState.update { it.copy(userLoadState = SearchLoadState.Loading) }
                userJob =
                    viewModelScope.launch {
                        try {
                            val users = searchRepository.searchUsers(query)
                            _uiState.update {
                                it.copy(
                                    userResults = users,
                                    userLoadState = SearchLoadState.Success,
                                )
                            }
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (throwable: Throwable) {
                            _uiState.update { it.copy(userLoadState = SearchLoadState.Error(throwable.toNodeSeekError())) }
                        }
                    }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    SearchViewModel(
                        searchRepository = container.searchRepository,
                        categoryRepository = container.categoryRepository,
                        settings = container.settingsRepository,
                    )
                }
            }
    }
}

sealed interface SearchLoadState {
    data object Idle : SearchLoadState

    data object Loading : SearchLoadState

    data object Success : SearchLoadState

    data class Error(val error: NodeSeekError) : SearchLoadState
}

data class SearchUiState(
    val query: String = "",
    val submittedQuery: String? = null,
    val target: SearchTarget = SearchTarget.POSTS,
    val searchHistory: List<SearchHistoryEntry> = emptyList(),
    val recentBoards: List<String> = emptyList(),
    val boards: List<Board> = emptyList(),
    val selectedBoards: Set<String> = emptySet(),
    val sort: SearchSort = SearchSort.RELEVANCE,
    val postResults: List<FeedPost> = emptyList(),
    val userResults: List<UserSearchResult> = emptyList(),
    val postPage: Int = 1,
    val postTotalPages: Int = 1,
    val postHasNext: Boolean = false,
    val isAppendingPosts: Boolean = false,
    val postLoadState: SearchLoadState = SearchLoadState.Idle,
    val userLoadState: SearchLoadState = SearchLoadState.Idle,
) {
    val currentLoadState: SearchLoadState
        get() = if (target == SearchTarget.POSTS) postLoadState else userLoadState
}
