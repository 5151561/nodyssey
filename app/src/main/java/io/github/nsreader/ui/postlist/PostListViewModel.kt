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
import io.github.nsreader.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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
) : ViewModel() {
    private val _uiState = MutableStateFlow(PostListUiState())
    val uiState: StateFlow<PostListUiState> = _uiState.asStateFlow()

    /**
     * `cachedIn` keeps the loaded pages alive across configuration changes and across navigating into
     * a post and back. Without it, returning to the list restarts paging at page one and the scroll
     * position goes with it — the exact regression phase two exists to fix.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val feed: Flow<PagingData<FeedPost>> =
        uiState
            .map { it.categorySlug }
            .distinctUntilChanged()
            .flatMapLatest { slug -> repository.feed(slug) }
            .cachedIn(viewModelScope)

    init {
        // Boards are owned by the repository; the ViewModel mirrors them into UiState but never
        // becomes their source of truth.
        categoryRepository.boards
            .onEach { boards -> _uiState.update { it.copy(boards = boards) } }
            .launchIn(viewModelScope)

        viewModelScope.launch { categoryRepository.refreshIfNeeded() }
    }

    fun selectCategory(slug: String?) {
        // The guard stays: re-emitting the same slug rebuilds the pager and drops the position.
        if (_uiState.value.categorySlug == slug) return
        _uiState.update { it.copy(categorySlug = slug) }
    }

    /** The URL a WebView should open to clear the current error. */
    fun challengeUrl(): String = NodeSeekSite.BASE_URL + NodeSeekSite.listPath(_uiState.value.categorySlug, 1)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    PostListViewModel(container.postRepository, container.categoryRepository)
                }
            }
    }
}

/**
 * Everything about the list that is *not* the list.
 *
 * Row data, loading and error state deliberately do not appear here — they belong to the paging
 * stream. Mirroring them in would recreate the second copy phase two removed.
 */
data class PostListUiState(
    val boards: List<Board> = listOf(CategoryRepository.FRONT_PAGE),
    val categorySlug: String? = null,
) {
    val selectedBoardIndex: Int
        get() = boards.indexOfFirst { it.slug == categorySlug }.coerceAtLeast(0)
}

internal fun Throwable.toNodeSeekError(): NodeSeekError = (this as? NodeSeekException)?.error ?: NodeSeekError.Unknown
