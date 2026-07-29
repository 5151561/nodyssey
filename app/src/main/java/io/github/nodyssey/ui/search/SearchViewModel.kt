package io.github.nodyssey.ui.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.data.Board
import io.github.nodyssey.data.CategoryRepository
import io.github.nodyssey.data.FeedPost
import io.github.nodyssey.data.SearchRepository
import io.github.nodyssey.data.UserSearchResult
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.model.SearchHistoryEntry
import io.github.nodyssey.model.SearchSort
import io.github.nodyssey.model.SearchTarget
import io.github.nodyssey.ui.postlist.toNodeSeekError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val categoryRepository: CategoryRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    /**
     * The search box itself.
     *
     * Held here rather than mirrored through the UiState so the screen and the results can never
     * disagree about what is being searched for, and so the caret survives a history pick.
     */
    val query = TextFieldState()

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val postRequest = MutableStateFlow<PostSearchRequest?>(null)
    val postResults: kotlinx.coroutines.flow.Flow<PagingData<FeedPost>> =
        postRequest
            .flatMapLatest { request ->
                if (request == null) {
                    flowOf(PagingData.empty())
                } else {
                    Pager(PagingConfig(pageSize = POST_PAGE_SIZE, initialLoadSize = POST_PAGE_SIZE)) {
                        SearchPostsPagingSource(searchRepository, request)
                    }.flow
                }
            }.cachedIn(viewModelScope)

    private var userJob: Job? = null
    private var postRequestGeneration = 0

    init {
        observeQuery()
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

    /**
     * Editing the box invalidates the results it no longer describes.
     *
     * Typing back to exactly the submitted text is not an edit as far as the results go — the list on
     * screen still answers the question in the box — so that case keeps them.
     */
    private fun observeQuery() {
        viewModelScope.launch {
            snapshotFlow { query.text.toString() }.collect { value ->
                if (value == _uiState.value.submittedQuery) return@collect
                _uiState.update { it.copy(submittedQuery = null, userLoadState = SearchLoadState.Idle) }
                postRequest.value = null
                userJob?.cancel()
            }
        }
    }

    fun selectTarget(target: SearchTarget) {
        _uiState.update { it.copy(target = target) }
        // The other tab's search runs lazily, on first visit: a posts search must not spend a user
        // API call (and the reverse) for a tab the user may never open.
        val state = _uiState.value
        val submitted = state.submittedQuery ?: return
        if (target == SearchTarget.POSTS) {
            if (postRequest.value?.query != submitted) startPostSearch(submitted)
        } else if (state.userLoadState == SearchLoadState.Idle) {
            startUserSearch(submitted)
        }
    }

    fun submitSearch() {
        val state = _uiState.value
        // Straight off the field, not off a mirror: what gets searched has to be what is in the box
        // at the moment the key was pressed.
        val typed = query.text.toString()
        executeSearch(
            query = typed,
            historyEntry =
            SearchHistoryEntry(
                query = typed,
                target = state.target,
                categorySlugs = if (state.target == SearchTarget.POSTS) state.selectedBoards else emptySet(),
                sort = state.sort,
            ),
        )
    }

    fun selectHistory(entry: SearchHistoryEntry) {
        query.setTextAndPlaceCursorAtEnd(entry.query)
        _uiState.update {
            it.copy(
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

    fun retryUsers() {
        val state = _uiState.value
        startUserSearch(state.submittedQuery ?: query.text.toString())
    }

    fun challengeUrl(): String =
        NodeSeekSite.BASE_URL +
            NodeSeekSite.postSearchPath(
                query = _uiState.value.submittedQuery ?: query.text.toString(),
                categorySlug = _uiState.value.selectedBoards.singleOrNull(),
                sort = if (_uiState.value.sort == SearchSort.TIME) io.github.nodyssey.model.FeedSort.POST_TIME else io.github.nodyssey.model.FeedSort.LAST_REPLY,
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

    private fun executeSearch(
        query: String,
        historyEntry: SearchHistoryEntry?,
    ) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return
        // The box is trimmed to what was actually searched, the way it used to be when the UiState
        // held the text — otherwise a stray space stays visible next to results that ignored it.
        if (this.query.text.toString() != normalized) this.query.setTextAndPlaceCursorAtEnd(normalized)
        postRequest.value = null
        userJob?.cancel()
        _uiState.update {
            it.copy(
                submittedQuery = normalized,
                userResults = emptyList(),
                userLoadState = SearchLoadState.Idle,
            )
        }
        historyEntry?.copy(query = normalized)?.let { entry ->
            viewModelScope.launch { settings.addSearchHistory(entry) }
        }
        if (_uiState.value.target == SearchTarget.POSTS) {
            startPostSearch(normalized)
        } else {
            startUserSearch(normalized)
        }
    }

    private fun startPostSearch(query: String) {
        val state = _uiState.value
        postRequest.value =
            PostSearchRequest(
                query = query,
                boards = state.selectedBoards,
                sort = state.sort,
                generation = ++postRequestGeneration,
            )
    }

    private fun startUserSearch(query: String) {
        userJob?.cancel()
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

    companion object {
        internal const val MAX_PAGES_PER_LOAD = 3
        private const val POST_PAGE_SIZE = 20

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
    /** What was actually searched for. Null while the box holds text nobody has submitted yet. */
    val submittedQuery: String? = null,
    val target: SearchTarget = SearchTarget.POSTS,
    val searchHistory: List<SearchHistoryEntry> = emptyList(),
    val recentBoards: List<String> = emptyList(),
    val boards: List<Board> = emptyList(),
    val selectedBoards: Set<String> = emptySet(),
    val sort: SearchSort = SearchSort.RELEVANCE,
    val userResults: List<UserSearchResult> = emptyList(),
    val userLoadState: SearchLoadState = SearchLoadState.Idle,
)

internal data class PostSearchRequest(
    val query: String,
    val boards: Set<String>,
    val sort: SearchSort,
    val generation: Int,
)

internal class SearchPostsPagingSource(
    private val repository: SearchRepository,
    private val request: PostSearchRequest,
) : PagingSource<Int, FeedPost>() {
    private val seenPostIds = mutableSetOf<Long>()

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FeedPost> =
        try {
            val startPage = params.key ?: 1
            val knownIds = seenPostIds.toSet()
            val summaries = mutableListOf<io.github.nodyssey.model.PostSummary>()
            var currentPage = startPage
            var lastLoadedPage = startPage
            var hasNext = true
            var fetchedPages = 0
            do {
                val result =
                    repository.searchPosts(
                        query = request.query,
                        page = currentPage,
                        categorySlugs = request.boards,
                        sort = request.sort,
                    )
                summaries += result.posts.filter { it.postId !in knownIds }
                lastLoadedPage = result.page
                hasNext = result.hasNextPage
                fetchedPages++
                if (summaries.isEmpty() && hasNext) currentPage = result.page + 1
            } while (
                summaries.isEmpty() &&
                hasNext &&
                fetchedPages < SearchViewModel.MAX_PAGES_PER_LOAD
            )

            val unique = summaries.distinctBy { it.postId }
            seenPostIds += unique.map { it.postId }
            LoadResult.Page(
                data = unique.map { FeedPost(it, isRead = false, newCommentCount = 0) },
                prevKey = null,
                nextKey = if (hasNext) lastLoadedPage + 1 else null,
            )
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            LoadResult.Error(throwable)
        }

    override fun getRefreshKey(state: PagingState<Int, FeedPost>): Int? = null
}
