package io.github.nodyssey.ui.postlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.PagingData
import androidx.paging.cachedIn
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.Board
import io.github.nodyssey.data.CategoryRepository
import io.github.nodyssey.data.FeedPost
import io.github.nodyssey.data.FeedRemoteMediator
import io.github.nodyssey.data.PostRepository
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.data.session.SessionState
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.settings.homeBoardArrangement
import io.github.nodyssey.data.settings.visibleHomeBoards
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.model.FeedSort
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the topic list.
 *
 * It holds no posts. Rows arrive from Room as a [PagingData] stream, and loading and error state come
 * from Paging's own `LoadState`, so there is exactly one copy of the list and it is the database's.
 *
 * That deletes a class of bug this ViewModel used to defend against by hand: there is no in-flight
 * page to double-start, and no late response from the previous board to leak into the new one,
 * because switching boards replaces the stream instead of mutating a list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PostListViewModel(
    private val repository: PostRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
    session: StateFlow<SessionState> = MutableStateFlow(SessionState()),
) : ViewModel() {
    private val _uiState = MutableStateFlow(PostListUiState())
    val uiState: StateFlow<PostListUiState> = _uiState.asStateFlow()

    /**
     * Bumped once the caches have been marked stale after a session change.
     *
     * It is a separate flow from [SessionRepository.state] precisely so the ordering is visible: the
     * pager must not be rebuilt until the invalidation has committed, or the mediator answers
     * `SKIP_INITIAL_REFRESH` and the user who just signed in keeps reading the signed-out list.
     */
    // Negative until the persisted cache provenance has been reconciled with the current cookie jar.
    // This prevents a signed-out cold start from briefly exposing rows fetched while signed in.
    private val feedGeneration = MutableStateFlow(-1)

    /**
     * `cachedIn` keeps the loaded pages alive across configuration changes and across navigating into
     * a post and back. Without it, returning to the list restarts paging at page one and the scroll
     * position goes with it — the exact regression phase two exists to fix.
     */
    val feed: Flow<PagingData<FeedPost>> =
        combine(
            uiState.map { FeedKey(it.categorySlug, it.sort, it.startPage, 0) },
            feedGeneration,
        ) { key, generation -> key.copy(sessionGeneration = generation) }
            .filter { it.sessionGeneration >= 0 }
            .distinctUntilChanged()
            .flatMapLatest { key -> repository.feed(key.categorySlug, key.sort, key.startPage) }
            .cachedIn(viewModelScope)

    init {
        /*
         * Boards are owned by the repository; the ViewModel mirrors them into UiState but never
         * becomes their source of truth. Two independent preferences then narrow that list, in this
         * order, because they mean different things:
         *
         *  1. The account's 首页版块 switches remove a board outright — it is not on the strip at all,
         *     in either half, and only 账号设置 can bring it back.
         *  2. The strip's own arrangement reorders what is left and parks some of it at the tail,
         *     where it stays visible and one tap from coming back.
         *
         * 综合 survives both: it is the front page rather than a board, so it is split off first and
         * prepended, always first and never parked.
         *
         * Losing the board the user is currently reading — to either mechanism — would leave a
         * selection with no pill, so it falls back to the front page.
         */
        combine(
            categoryRepository.boards,
            settingsRepository.settings
                .map { Triple(it.hiddenHomeBoards, it.homeBoardOrder, it.disabledHomeBoards) }
                .distinctUntilChanged(),
        ) { boards, (hiddenBoards, order, parkedSlugs) ->
            val (frontPage, real) = boards.partition { it.slug == null }
            homeBoardArrangement(visibleHomeBoards(real, hiddenBoards), order, parkedSlugs)
                .let { it.copy(enabled = frontPage + it.enabled) }
        }.onEach { arrangement ->
            _uiState.update { state ->
                val stillSelectable = arrangement.enabled.any { it.slug == state.categorySlug }
                state.copy(
                    boards = arrangement.enabled,
                    parkedBoards = arrangement.parked,
                    categorySlug = if (stillSelectable) state.categorySlug else null,
                )
            }
        }.launchIn(viewModelScope)

        settingsRepository.settings
            .map { it.homePageBar }
            .distinctUntilChanged()
            .onEach { enabled ->
                // Turning the bar off also gives up the page it had travelled to: with no control on
                // screen there is no way back from page 40, and a feed silently stuck there is worse
                // than the one the switch was turned off to get.
                _uiState.update { state ->
                    state.copy(
                        pageBarEnabled = enabled,
                        startPage = if (enabled) state.startPage else FeedRemoteMediator.FIRST_PAGE,
                    )
                }
            }.launchIn(viewModelScope)

        uiState
            .map { it.categorySlug to it.sort }
            .distinctUntilChanged()
            .flatMapLatest { (slug, sort) -> repository.feedTotalPages(slug, sort) }
            .onEach { total -> _uiState.update { it.copy(totalPages = total) } }
            .launchIn(viewModelScope)

        // Reconcile the first cookie snapshot before opening a Pager. Later generations either
        // invalidate authenticated data or clear it when the user becomes signed out.
        session
            .distinctUntilChangedBy { it.generation }
            .onEach { sessionState ->
                val isInitial = feedGeneration.value < 0
                val cleared =
                    repository.reconcileSession(
                        isSignedIn = sessionState.isSignedIn,
                        fingerprint = sessionState.fingerprint,
                    )
                if (!isInitial && sessionState.isSignedIn && !cleared) {
                    repository.invalidateCaches()
                }
                feedGeneration.update { generation ->
                    when {
                        generation < 0 -> 0
                        cleared || !isInitial -> generation + 1
                        else -> generation
                    }
                }
            }.launchIn(viewModelScope)

        viewModelScope.launch { categoryRepository.refreshIfNeeded() }
    }

    fun selectCategory(slug: String?) {
        // The guard stays: re-emitting the same slug rebuilds the pager and drops the position.
        if (_uiState.value.categorySlug == slug) return
        _uiState.update { it.copy(categorySlug = slug, startPage = FeedRemoteMediator.FIRST_PAGE) }
    }

    /**
     * Sort order is session state rather than a stored setting.
     *
     * Switching it is a "look at this differently for a minute" action, not a preference — and each
     * order is a separate feed in the database, so persisting it would also mean deciding which of
     * the two cached lists a cold start should paint.
     */
    fun selectSort(sort: FeedSort) {
        if (_uiState.value.sort == sort) return
        _uiState.update { it.copy(sort = sort, startPage = FeedRemoteMediator.FIRST_PAGE) }
    }

    /**
     * Sends 首页 to a page it is not currently holding.
     *
     * The screen calls this only when the target is genuinely absent — a page already in the loaded
     * window is a scroll, not a fetch, and going through here would throw away everything after it
     * for no reason. What lands is a new window starting at [page]: the site pages forwards only, so
     * arriving at page 40 means holding page 40 onwards and nothing before it.
     */
    fun goToPage(page: Int) {
        val target = page.coerceIn(FeedRemoteMediator.FIRST_PAGE, _uiState.value.totalPages.coerceAtLeast(1))
        if (_uiState.value.startPage == target) return
        _uiState.update { it.copy(startPage = target) }
    }

    /**
     * Where [page] starts among the rows 首页 is already holding, or null when it holds none of it.
     *
     * This is the question [goToPage] is the answer to only half the time. The reader who has scrolled
     * through five pages has all five in the database, and stepping back to the fourth should be a
     * scroll — but the pager keeps one window of rows in memory and re-makes it on every write, so
     * asking the list would say "not loaded" about a page the reader was looking at a moment ago and
     * fetch it again.
     */
    suspend fun rowIndexOfPage(page: Int): Int? =
        repository.feedRowIndexOfPage(_uiState.value.categorySlug, _uiState.value.sort, page)

    /**
     * Commits an edit made on the strip itself: the new left-to-right order, and which boards are
     * parked at the tail.
     *
     * The strip sends whole lists rather than "moved X to 3" or "parked Y" because a drag is already
     * a whole-list operation by the time the finger lifts, and a parked board is also a move. One
     * write per gesture, and the flow above turns it back into two halves.
     *
     * 综合 is filtered out on the way in. It is not a board, so it has no slug to rank, and the strip
     * pins it in front regardless.
     */
    fun saveBoardArrangement(
        order: List<String>,
        parked: Set<String>,
    ) {
        viewModelScope.launch { settingsRepository.setHomeBoardArrangement(order, parked) }
    }

    /** The URL a WebView should open to clear the current error. */
    fun challengeUrl(): String =
        NodeSeekSite.BASE_URL +
            NodeSeekSite.listPath(_uiState.value.categorySlug, 1, _uiState.value.sort)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    PostListViewModel(
                        container.postRepository,
                        container.categoryRepository,
                        container.settingsRepository,
                        container.sessionRepository.state,
                    )
                }
            }
    }
}

/**
 * Everything that decides *which* pager the screen is showing.
 *
 * A data class rather than a `Triple` so `distinctUntilChanged` compares fields that have names, and
 * so adding a fourth reason to rebuild cannot silently reorder the destructuring.
 */
private data class FeedKey(
    val categorySlug: String?,
    val sort: FeedSort,
    /** A jump is a different pager, for the same reason a different board is: the rows are replaced. */
    val startPage: Int,
    val sessionGeneration: Int,
)

/**
 * Everything about the list that is *not* the list.
 *
 * Row data, loading and error state deliberately do not appear here — they belong to the paging
 * stream. Mirroring them in would recreate the second copy phase two removed.
 */
data class PostListUiState(
    val boards: List<Board> = listOf(CategoryRepository.FRONT_PAGE),
    /**
     * Boards the user parked at the tail of the strip.
     *
     * Separate from [boards] rather than a flag on them: nothing outside the strip may treat these as
     * selectable, and a list the feed can page through is exactly what [boards] is.
     */
    val parkedBoards: List<Board> = emptyList(),
    val categorySlug: String? = null,
    val sort: FeedSort = FeedSort.LAST_REPLY,
    /**
     * 设置 › 首页翻页栏, mirroring [io.github.nodyssey.data.settings.UserSettings.homePageBar]'s own
     * default so the bar does not blink into place once DataStore answers.
     */
    val pageBarEnabled: Boolean = true,
    /**
     * Where the loaded window starts — 1 unless 翻页栏 has sent the feed somewhere.
     *
     * Part of the state rather than a field because it selects the pager: changing it is what makes
     * the mediator replace the stored rows instead of appending to them.
     */
    val startPage: Int = FeedRemoteMediator.FIRST_PAGE,
    /** How many pages the site says this feed has, as of the last page stored. */
    val totalPages: Int = 1,
) {
    /**
     * The selected board's name, or null on the mixed front page.
     *
     * Null rather than "综合" on purpose: it feeds the "needs login" screen, and "「综合」需要登录"
     * would be a sentence about a board that does not exist.
     */
    val selectedBoardTitle: String?
        get() = categorySlug?.let { slug -> boards.firstOrNull { it.slug == slug }?.title }
}

internal fun Throwable.toSiteError(): SiteError = (this as? SiteException)?.error ?: SiteError.Unknown
