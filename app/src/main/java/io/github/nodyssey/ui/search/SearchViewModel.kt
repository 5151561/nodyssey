package io.github.nodyssey.ui.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.PagingData
import androidx.paging.cachedIn
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.data.Board
import io.github.nodyssey.data.CategoryRepository
import io.github.nodyssey.data.FeedPost
import io.github.nodyssey.data.PostRepository
import io.github.nodyssey.data.SearchRepository
import io.github.nodyssey.data.UserSearchResult
import io.github.nodyssey.data.emptyLoadedPagingData
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.SearchHistoryEntry
import io.github.nodyssey.model.SearchTarget
import io.github.nodyssey.ui.postlist.toNodeSeekError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
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
    private val postRepository: PostRepository,
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

    /**
     * Post results, straight off the forum's own pipeline.
     *
     * There is no search-specific pager, paging source or cache here any more. A request describes
     * a feed; [PostRepository.searchFeed] turns it into the same Room-backed, mediator-filled pager
     * a board gets, which is what makes one page cost one request, returning to the screen cost
     * none, and the results carry read state.
     *
     * A [MutableStateFlow] rather than a channel because it conflates: assigning a request equal to
     * the current one emits nothing, so re-submitting the same search keeps the pager it already has
     * instead of rebuilding it and spending a refresh proving the answer has not changed.
     */
    private val postRequest = MutableStateFlow<PostSearchRequest?>(null)
    val postResults: Flow<PagingData<FeedPost>> =
        postRequest
            .flatMapLatest { request ->
                if (request == null) {
                    flowOf(emptyLoadedPagingData())
                } else {
                    postRepository.searchFeed(
                        query = request.query,
                        categorySlug = request.board,
                        sort = request.sort,
                    )
                }
            }.cachedIn(viewModelScope)

    private var userJob: Job? = null

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
            // Unconditional now that the request conflates: asking for the feed already on screen
            // emits nothing, and the old "only if the query changed" guard missed a board or order
            // picked while the users tab was in front.
            startPostSearch(submitted)
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
                categorySlug = if (state.target == SearchTarget.POSTS) state.selectedBoard else null,
                sort = state.sort,
            ),
        )
    }

    fun selectHistory(entry: SearchHistoryEntry) {
        query.setTextAndPlaceCursorAtEnd(entry.query)
        _uiState.update {
            it.copy(
                target = entry.target,
                selectedBoard = entry.categorySlug,
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

    /** Null scopes the search to the whole site, which is what the site does without a `category`. */
    fun selectBoard(slug: String?) {
        if (_uiState.value.selectedBoard == slug) return
        _uiState.update { it.copy(selectedBoard = slug) }
        viewModelScope.launch { settings.recordRecentBoard(slug) }
        rerunSubmittedSearch()
    }

    fun selectSort(sort: FeedSort) {
        if (_uiState.value.sort == sort) return
        _uiState.update { it.copy(sort = sort) }
        rerunSubmittedSearch()
    }

    fun retryUsers() {
        val state = _uiState.value
        startUserSearch(state.submittedQuery ?: query.text.toString())
    }

    fun challengeUrl(): String {
        val state = _uiState.value
        return NodeSeekSite.BASE_URL +
            NodeSeekSite.postSearchPath(
                query = state.submittedQuery ?: query.text.toString(),
                categorySlug = state.selectedBoard,
                sort = state.sort,
            )
    }

    private fun rerunSubmittedSearch() {
        val state = _uiState.value
        if (state.submittedQuery == null) return
        executeSearch(
            query = state.submittedQuery,
            historyEntry =
            SearchHistoryEntry(
                query = state.submittedQuery,
                target = SearchTarget.POSTS,
                categorySlug = state.selectedBoard,
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
            postRequest.value = null
            startUserSearch(normalized)
        }
    }

    /**
     * Re-submitting the same search does *not* clear the request first.
     *
     * Dropping to null and back used to be how the results were reset, but it tears down the pager
     * and rebuilds it, and the rebuild re-requests page one. Emitting the request as-is lets
     * [distinctUntilChanged] recognise it, keep the pager, and serve the cached page.
     */
    private fun startPostSearch(query: String) {
        val state = _uiState.value
        postRequest.value = PostSearchRequest(query = query, board = state.selectedBoard, sort = state.sort)
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
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    SearchViewModel(
                        postRepository = container.postRepository,
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
    /** One board, or null for the whole site — the only two scopes `/search` can express. */
    val selectedBoard: String? = null,
    /** `/search` reads the boards' own `sortBy`, and reads it as 新帖子 when it is absent. */
    val sort: FeedSort = FeedSort.POST_TIME,
    val userResults: List<UserSearchResult> = emptyList(),
    val userLoadState: SearchLoadState = SearchLoadState.Idle,
)

/** Everything that names one search feed. Equality is what decides whether the pager is rebuilt. */
internal data class PostSearchRequest(
    val query: String,
    val board: String?,
    val sort: FeedSort,
)
