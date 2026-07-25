package io.github.nsreader.ui.postlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.PagingData
import androidx.paging.cachedIn
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.data.Board
import io.github.nsreader.data.CategoryRepository
import io.github.nsreader.data.FeedPost
import io.github.nsreader.data.PostRepository
import io.github.nsreader.data.session.SessionRepository
import io.github.nsreader.data.session.SessionState
import io.github.nsreader.di.AppContainer
import io.github.nsreader.model.FeedSort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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
class PostListViewModel(
    private val repository: PostRepository,
    private val categoryRepository: CategoryRepository,
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
    private val feedGeneration = MutableStateFlow(0)

    /**
     * `cachedIn` keeps the loaded pages alive across configuration changes and across navigating into
     * a post and back. Without it, returning to the list restarts paging at page one and the scroll
     * position goes with it — the exact regression phase two exists to fix.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val feed: Flow<PagingData<FeedPost>> =
        combine(
            uiState.map { it.categorySlug to it.sort },
            feedGeneration,
        ) { (slug, sort), generation -> FeedKey(slug, sort, generation) }
            .distinctUntilChanged()
            .flatMapLatest { key -> repository.feed(key.categorySlug, key.sort) }
            .cachedIn(viewModelScope)

    init {
        // Boards are owned by the repository; the ViewModel mirrors them into UiState but never
        // becomes their source of truth.
        categoryRepository.boards
            .onEach { boards -> _uiState.update { it.copy(boards = boards) } }
            .launchIn(viewModelScope)

        // Signing in, or clearing a challenge, changes what the site will hand us. `drop(1)` skips the
        // cookies we started the process with — a cold start is not a session change.
        session
            .map { it.generation }
            .distinctUntilChanged()
            .drop(1)
            .onEach {
                repository.invalidateCaches()
                feedGeneration.update { generation -> generation + 1 }
            }.launchIn(viewModelScope)

        viewModelScope.launch { categoryRepository.refreshIfNeeded() }
    }

    fun selectCategory(slug: String?) {
        // The guard stays: re-emitting the same slug rebuilds the pager and drops the position.
        if (_uiState.value.categorySlug == slug) return
        _uiState.update { it.copy(categorySlug = slug) }
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
        _uiState.update { it.copy(sort = sort) }
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
    val categorySlug: String? = null,
    val sort: FeedSort = FeedSort.LAST_REPLY,
) {
    val selectedBoardIndex: Int
        get() = boards.indexOfFirst { it.slug == categorySlug }.coerceAtLeast(0)

    /**
     * The selected board's name, or null on the mixed front page.
     *
     * Null rather than "综合" on purpose: it feeds the "needs login" screen, and "「综合」需要登录"
     * would be a sentence about a board that does not exist.
     */
    val selectedBoardTitle: String?
        get() = categorySlug?.let { slug -> boards.firstOrNull { it.slug == slug }?.title }
}

internal fun Throwable.toNodeSeekError(): NodeSeekError = (this as? NodeSeekException)?.error ?: NodeSeekError.Unknown
