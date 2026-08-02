package io.github.nodyssey.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.nodyssey.core.runCatchingExceptCancellation
import io.github.nodyssey.data.Board
import io.github.nodyssey.data.CategoryRepository
import io.github.nodyssey.data.RulingPage
import io.github.nodyssey.data.RulingRecord
import io.github.nodyssey.data.RulingRepository
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.postlist.toNodeSeekError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RulingUiState(
    /** A load that replaces what is on screen: the first one, a retry, or a jump off the loaded slice. */
    val isLoading: Boolean = true,
    /** The next page joining the tail. Kept apart from [isLoading] so appending never blanks the list. */
    val isAppending: Boolean = false,
    val error: NodeSeekError? = null,
    val records: List<RulingRecord> = emptyList(),
    /** The site page each record came from, index-aligned with [records]. */
    val recordPages: List<Int> = emptyList(),
    /** The first site page held in [records] — not always 1, since a jump loads its page alone. */
    val firstLoadedPage: Int = 1,
    /** The last site page held in [records]. */
    val lastLoadedPage: Int = 1,
    val totalPages: Int = 1,
    val hasNextPage: Boolean = false,
    /** The page the screen still owes the reader a scroll to, once its records are in [records]. */
    val pendingScroll: Int? = null,
    /**
     * Board slug → title, for the 移动版块 verb.
     *
     * Carried on the state rather than looked up in the row because the boards are
     * [CategoryRepository]'s to own; this screen only observes them.
     */
    val boardTitles: Map<String, String> = emptyMap(),
) {
    /** The first row of [page], or null when that page is not loaded. */
    fun firstIndexOfPage(page: Int): Int? = recordPages.indexOf(page).takeIf { it >= 0 }

    fun isPageLoaded(page: Int): Boolean = page in firstLoadedPage..lastLoadedPage
}

/**
 * 管理记录 — the public log of penalties and rewards.
 *
 * The site reads it as a numbered table; the app reads it as one scroll that keeps going, with the
 * same page control the comment thread uses for the same problem. The two halves are deliberate:
 * **appending** is how you keep reading, and it only ever extends the slice by one adjoining page;
 * **jumping** is how you travel, and a jump anywhere else replaces the slice rather than fetching
 * every page in between. Page 60 is one request, not fifty-nine.
 */
class RulingViewModel(
    private val repository: RulingRepository,
    /**
     * The board list, observed rather than owned — it belongs to [CategoryRepository].
     *
     * Taken as the flow itself and not as the repository: this screen wants one field off one
     * emission, and depending on the whole repository would drag its database in behind it.
     */
    boards: Flow<List<Board>>,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RulingUiState(boardTitles = STATIC_BOARD_TITLES))
    val uiState: StateFlow<RulingUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /** The page the user last asked for — the loaded window only moves on success. */
    private var requestedPage = 1

    init {
        viewModelScope.launch {
            // Seeded with the static table and then overwritten, so a cold start with an empty board
            // cache still reads "移动版块至 日常" instead of "移动版块至 daily".
            boards.collect { list ->
                val titles = STATIC_BOARD_TITLES + list.mapNotNull { board -> board.titleBySlug() }
                _uiState.update { it.copy(boardTitles = titles) }
            }
        }
        load(page = 1, replacesWindow = true)
    }

    /**
     * The next page, joined onto the tail.
     *
     * Guarded rather than debounced at the call site: the screen asks on every scroll frame that
     * comes near the foot of the list, and the guard is what turns that into one request per page.
     */
    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoading || state.isAppending || !state.hasNextPage) return
        load(page = state.lastLoadedPage + 1, replacesWindow = false)
    }

    /** Brings [page] onto the screen, fetching it when it is not one of the loaded ones. */
    fun loadPage(page: Int) {
        val state = _uiState.value
        val target = page.coerceIn(1, state.totalPages.coerceAtLeast(1))
        if (state.isPageLoaded(target)) {
            _uiState.update { it.copy(pendingScroll = target) }
            return
        }
        if (state.isLoading || state.isAppending) return
        // Only the page directly after the slice extends it. The one before would have to be
        // prepended, and a list that grows upward moves everything the reader is looking at.
        load(page = target, replacesWindow = target != state.lastLoadedPage + 1, scrollTo = target)
    }

    /** The screen has scrolled to [RulingUiState.pendingScroll]; stop asking it to. */
    fun onScrollHandled() {
        _uiState.update { it.copy(pendingScroll = null) }
    }

    // Retries the page that just failed, not the one still on screen from the last success.
    fun retry() = load(page = requestedPage, replacesWindow = true)

    private fun load(
        page: Int,
        replacesWindow: Boolean,
        scrollTo: Int? = null,
    ) {
        requestedPage = page
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _uiState.update {
                    it.copy(isLoading = replacesWindow, isAppending = !replacesWindow, error = null)
                }
                runCatchingExceptCancellation { repository.records(page) }
                    .onSuccess { result ->
                        _uiState.update { it.merge(result, replacesWindow, scrollTo) }
                    }.onFailure { throwable ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isAppending = false,
                                error = throwable.toNodeSeekError(),
                            )
                        }
                    }
            }
    }

    /**
     * Folds a fetched page into the window.
     *
     * The rows arrive newest-first and the pages are ordered the same way, so page *n+1* belongs at
     * the tail — no sorting, and no merge key. An append that arrives twice would duplicate rows, so
     * a page already inside the window replaces the whole thing rather than joining it.
     */
    private fun RulingUiState.merge(
        result: RulingPage,
        replacesWindow: Boolean,
        scrollTo: Int?,
    ): RulingUiState {
        val appends = !replacesWindow && result.page == lastLoadedPage + 1 && records.isNotEmpty()
        val pageColumn = List(result.records.size) { result.page }
        return copy(
            isLoading = false,
            isAppending = false,
            error = null,
            records = if (appends) records + result.records else result.records,
            recordPages = if (appends) recordPages + pageColumn else pageColumn,
            firstLoadedPage = if (appends) firstLoadedPage else result.page,
            lastLoadedPage = result.page,
            totalPages = result.totalPages,
            hasNextPage = result.page < result.totalPages,
            pendingScroll = scrollTo ?: pendingScroll,
        )
    }

    companion object {
        /** The site serves the first 100 pages of the log and refuses the rest. */
        const val MAX_PAGES = NodeSeekJsonClient.RULING_MAX_PAGES

        private val STATIC_BOARD_TITLES: Map<String, String> =
            NodeSeekSite.categories.mapNotNull { category ->
                category.slug?.let { it to category.title }
            }.toMap()

        private fun Board.titleBySlug(): Pair<String, String>? = slug?.let { it to title }

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { RulingViewModel(container.rulingRepository, container.categoryRepository.boards) }
            }
    }
}
