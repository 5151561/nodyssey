package io.github.nodyssey.ui.tools

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
import io.github.nodyssey.data.AwardRepository
import io.github.nodyssey.data.PostRepository
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.model.PostListPage
import io.github.nodyssey.model.PostSummary
import kotlinx.coroutines.CancellationException
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

/** One thread of 推荐阅读, with the site page it came from — what the page rail reads its number off. */
data class AwardRow(
    val summary: PostSummary,
    val page: Int,
)

data class AwardUiState(
    /** The page the list was last sent to: page 1, or wherever the last jump landed. */
    val startPage: Int = 1,
    val totalPages: Int = 1,
    /** Threads per site page, once a page that is not the last has said so. The jump sheet's footnote. */
    val pageSize: Int? = null,
    /** Threads this device has opened, so their titles dim the way 首页's do. */
    val readPostIds: Set<Long> = emptySet(),
)

/**
 * 推荐阅读 — the curated threads, read the way 首页 is read.
 *
 * One scroll that keeps going, with the page rail for travelling: the next page joins the foot as the
 * reader nears it, and a jump starts the list afresh at the page asked for. Every row knows which
 * site page it came from, which is what the rail shows, and the list also grows *upwards* from a
 * jump: scrolling back from page 12 reads page 11 rather than stopping at a wall.
 *
 * Paging's own source over the site's page numbers, with a new [Pager] per jump. It is the model the
 * site hands over — pages by number, no cursor — and a jump is exactly a new initial key.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AwardViewModel(
    private val repository: AwardRepository,
    postRepository: PostRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AwardUiState())
    val uiState: StateFlow<AwardUiState> = _uiState.asStateFlow()

    val posts: Flow<PagingData<AwardRow>> =
        _uiState
            .map { it.startPage }
            .distinctUntilChanged()
            .flatMapLatest { start ->
                Pager(
                    // The site decides how many threads a page holds; the size here only paces the
                    // prefetch, and one page ahead is what that should be.
                    config = PagingConfig(pageSize = PAGE_SIZE_HINT, enablePlaceholders = false),
                    initialKey = start,
                ) { AwardPagingSource(repository::page, ::onPageLoaded) }.flow
            }.cachedIn(viewModelScope)

    init {
        // Read marks are the same rows 首页 dims its titles from; this list just asks for them by id.
        postRepository
            .readHistory()
            .map { history -> history.mapTo(HashSet()) { it.postId } }
            .distinctUntilChanged()
            .onEach { ids -> _uiState.update { it.copy(readPostIds = ids) } }
            .launchIn(viewModelScope)
    }

    /**
     * Starts the list afresh at [page].
     *
     * The screen calls this only for a page it holds no rows of and cannot reach by reading on —
     * anything else is a scroll, and going through here would throw the loaded pages away for it.
     */
    fun goToPage(page: Int) {
        val target = page.coerceIn(1, _uiState.value.totalPages.coerceAtLeast(1))
        _uiState.update { it.copy(startPage = target) }
    }

    private fun onPageLoaded(result: PostListPage) {
        _uiState.update {
            it.copy(
                totalPages = result.totalPages,
                // Only a page with one after it is full; the last page holds whatever is left over.
                pageSize = if (result.hasNextPage) result.posts.size else it.pageSize,
            )
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { AwardViewModel(container.awardRepository, container.postRepository) }
            }
    }
}

/**
 * The site's award pages by number, both ways from wherever the list was started.
 *
 * [onPageLoaded] hands each page's header — the page count, and how many threads a full page holds —
 * to the screen, which draws the rail from them. The rows carry their own page, which is what the
 * refresh key is read off: a retry after a failure comes back to the page the reader was on.
 */
internal class AwardPagingSource(
    private val fetch: suspend (Int) -> PostListPage,
    private val onPageLoaded: (PostListPage) -> Unit = {},
) : PagingSource<Int, AwardRow>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AwardRow> =
        try {
            val requested = params.key ?: 1
            val result = fetch(requested)
            onPageLoaded(result)
            LoadResult.Page(
                data = result.posts.map { AwardRow(it, result.page) },
                prevKey = (result.page - 1).takeIf { it >= 1 },
                nextKey = (result.page + 1).takeIf { result.hasNextPage },
            )
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            LoadResult.Error(throwable)
        }

    override fun getRefreshKey(state: PagingState<Int, AwardRow>): Int? =
        state.anchorPosition?.let { position -> state.closestItemToPosition(position)?.page }
}

/** Roughly one site page, so the next is asked for about a page before the reader reaches it. */
private const val PAGE_SIZE_HINT = 20
