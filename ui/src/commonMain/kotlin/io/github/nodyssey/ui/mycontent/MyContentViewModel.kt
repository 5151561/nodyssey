package io.github.nodyssey.ui.mycontent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.SpaceComment
import io.github.nodyssey.data.SpacePage
import io.github.nodyssey.data.SpacePost
import io.github.nodyssey.data.UserProfile
import io.github.nodyssey.data.UserSpaceRepository
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.postlist.toSiteError
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 最新发布 / 最早发布 — the two orders the chip on boards n2 and n3 offers. */
enum class MyContentSort {
    NEWEST,
    OLDEST,
}

/**
 * One of the two 我的 lists, as the screen reads it.
 *
 * [items] is already filtered and ordered; [loadedCount] is the whole of what has arrived, which is
 * what the header counts and what the two chips act on. They are deliberately different numbers: a
 * board filter narrows the rows on screen without unloading anything.
 */
data class MyContentUiState<T>(
    val items: List<T> = emptyList(),
    val loadedCount: Int = 0,
    /** The site's own total for this list, off the profile payload. Null until it answers. */
    val totalCount: Int? = null,
    /** Board names seen so far, in the order they first appeared. Empty hides the board chip. */
    val boards: List<String> = emptyList(),
    val board: String? = null,
    val sort: MyContentSort = MyContentSort.NEWEST,
    val isLoading: Boolean = false,
    val isAppending: Boolean = false,
    val endReached: Boolean = false,
    val error: SiteError? = null,
) {
    val isEmpty: Boolean get() = loadedCount == 0 && !isLoading && error == null
}

/**
 * The list behind 我的主题帖 and 我的评论.
 *
 * Pages accumulate in memory rather than streaming through Paging 3, and that is what the two chips
 * cost. `/api/content/list-discussions` and its comment twin return newest-first with no sort or
 * board parameter of their own, so both chips can only ever act on what has been loaded — 最早发布 in
 * particular reverses the rows in hand, not the account's whole history. Paging 3 hands out a
 * `PagingData` that can be mapped and filtered but never reordered, so keeping the loaded rows here
 * is what lets the chip work at all.
 */
abstract class MyContentViewModel<T : Any>(
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MyContentUiState<T>(isLoading = true))
    val uiState: StateFlow<MyContentUiState<T>> = _uiState.asStateFlow()

    /** Every row that has arrived, newest first — the order the site returns them in. */
    private val loaded = mutableListOf<T>()
    private var nextPage = 1
    private var loadJob: Job? = null

    /** One page of the site's own list, for the uid the session belongs to. */
    protected abstract suspend fun page(uid: Long, page: Int): SpacePage<T>

    /** The row's board, when the payload carries one. Null rows never match a board filter. */
    protected abstract fun boardOf(item: T): String?

    /** This list's total as the profile reports it — the number the header counts, not what loaded. */
    protected abstract fun totalOf(profile: UserProfile): Int?

    init {
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loaded.clear()
        nextPage = 1
        _uiState.value = _uiState.value.copy(
            items = emptyList(),
            loadedCount = 0,
            totalCount = null,
            boards = emptyList(),
            board = null,
            isLoading = true,
            isAppending = false,
            endReached = false,
            error = null,
        )
        load()
    }

    /** Called when the list is scrolled to its end; a no-op unless there is another page to ask for. */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isAppending || state.endReached || state.error != null) return
        _uiState.value = state.copy(isAppending = true)
        load()
    }

    fun retry() {
        _uiState.value = _uiState.value.copy(error = null)
        if (loaded.isEmpty()) refresh() else loadMore()
    }

    fun selectBoard(board: String?) {
        _uiState.value = _uiState.value.copy(board = board).withRows()
    }

    fun selectSort(sort: MyContentSort) {
        _uiState.value = _uiState.value.copy(sort = sort).withRows()
    }

    private fun load() {
        loadJob =
            viewModelScope.launch {
                // The lists are the signed-in account's, and the endpoint behind them still wants
                // that account's uid in the path. `selfUid` is null until some screen has loaded a
                // profile this process, so the uid is asked for rather than waited on — collecting
                // the flow would hang forever on a cold start into this screen, and a refused
                // profile call has a site error worth showing.
                runCatchingExceptCancellation {
                    val profile = profileRepository.profile()
                    profile to page(profile.uid, nextPage)
                }
                    .onSuccess { (profile, result) ->
                        loaded += result.items
                        nextPage = result.page + 1
                        _uiState.value =
                            _uiState.value.copy(
                                loadedCount = loaded.size,
                                totalCount = totalOf(profile),
                                boards = loaded.mapNotNull(::boardOf).distinct(),
                                isLoading = false,
                                isAppending = false,
                                endReached = !result.hasNextPage,
                                error = null,
                            ).withRows()
                    }.onFailure { throwable ->
                        _uiState.value =
                            _uiState.value.copy(
                                isLoading = false,
                                isAppending = false,
                                error = throwable.toSiteError(),
                            )
                    }
            }
    }

    private fun MyContentUiState<T>.withRows(): MyContentUiState<T> {
        val filtered = board?.let { name -> loaded.filter { boardOf(it) == name } } ?: loaded.toList()
        return copy(items = if (sort == MyContentSort.OLDEST) filtered.asReversed() else filtered)
    }
}

/** Board n2. */
class MyTopicsViewModel(
    profileRepository: ProfileRepository,
    private val spaceRepository: UserSpaceRepository,
) : MyContentViewModel<SpacePost>(profileRepository) {
    override suspend fun page(uid: Long, page: Int): SpacePage<SpacePost> =
        spaceRepository.topics(uid, page)

    override fun boardOf(item: SpacePost): String? = item.categoryTitle

    override fun totalOf(profile: UserProfile): Int? = profile.topicCount

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    MyTopicsViewModel(container.profileRepository, container.userSpaceRepository)
                }
            }
    }
}

/** Board n3. */
class MyCommentsViewModel(
    profileRepository: ProfileRepository,
    private val spaceRepository: UserSpaceRepository,
) : MyContentViewModel<SpaceComment>(profileRepository) {
    override suspend fun page(uid: Long, page: Int): SpacePage<SpaceComment> =
        spaceRepository.comments(uid, page)

    // The comment payload carries the thread's title but never its board, so this list's board chip
    // stays hidden. Reading it off the title would be a guess dressed up as a filter.
    override fun boardOf(item: SpaceComment): String? = null

    override fun totalOf(profile: UserProfile): Int? = profile.commentCount

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    MyCommentsViewModel(container.profileRepository, container.userSpaceRepository)
                }
            }
    }
}
