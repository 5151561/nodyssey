package io.github.nodyssey.ui.bookmarks

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.CollectedPostMeta
import io.github.nodyssey.data.CollectedPostMetaStore
import io.github.nodyssey.data.OfflineLibrary
import io.github.nodyssey.data.OfflineSettings
import io.github.nodyssey.data.OfflineState
import io.github.nodyssey.data.OfflineUsage
import io.github.nodyssey.data.PostRepository
import io.github.nodyssey.data.SpacePost
import io.github.nodyssey.data.UserSpaceRepository
import io.github.nodyssey.data.isEmpty
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.postlist.toSiteError
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One row of 收藏: the thread as the site lists it, plus what this device has of it. */
@Immutable
data class BookmarkEntry(
    val postId: Long,
    val title: String,
    val categoryTitle: String?,
    val categorySlug: String?,
    val authorName: String?,
    val commentCount: Int?,
    val createdAtText: String?,
    val offline: OfflineState = OfflineState.NotDownloaded,
)

/** The three filter chips. Counts come from the whole list, which is why the list is loaded whole. */
enum class BookmarkFilter {
    ALL,
    DOWNLOADED,
    NEW_REPLIES,
}

/**
 * What ⇅ offers.
 *
 * All three are local orderings of an already-loaded list — the collection endpoint takes no sort
 * parameter, so anything else here would be a control that reorders the visible page and silently
 * lies about the rest.
 */
enum class BookmarkSort {
    /** The order the site returned, which is collection order. */
    SITE,
    REPLIES,

    /** Everything still to download first — the order you want when you are about to leave Wi-Fi. */
    PENDING_FIRST,
}

@Immutable
data class BookmarksUiState(
    val entries: List<BookmarkEntry> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: SiteError? = null,
    /** True when the collection ran past [BookmarksViewModel.MAX_PAGES] and the counts are a floor. */
    val truncated: Boolean = false,
    val filter: BookmarkFilter = BookmarkFilter.ALL,
    val sort: BookmarkSort = BookmarkSort.SITE,
    /**
     * 搜索 over the loaded list. Null is "not searching", which is a different screen from an empty
     * query — the latter has the field open and shows everything.
     */
    val query: String? = null,
    /** Non-null only in multi-select; empty-but-non-null is multi-select with nothing ticked. */
    val selection: Set<Long>? = null,
    val offlineAvailable: Boolean = false,
    /** What the selection would cost to download, when [OfflineLibrary.estimateBytes] can say. */
    val selectionEstimateBytes: Long? = null,
    val usage: OfflineUsage = OfflineUsage(),
    val offlineSettings: OfflineSettings = OfflineSettings(),
) {
    val inSelection: Boolean get() = selection != null

    val downloadedCount: Int get() = entries.count { it.offline is OfflineState.Downloaded }

    val newReplyCount: Int get() = entries.count { it.offline is OfflineState.Stale }

    /** What 「全部下载 · N 篇」 counts: everything not already stored, failures included. */
    val pendingDownloadCount: Int
        get() =
            entries.count {
                it.offline is OfflineState.NotDownloaded || it.offline is OfflineState.Failed
            }

    val isSearching: Boolean get() = query != null

    val visible: List<BookmarkEntry>
        get() {
            val matched =
                query?.trim()?.takeIf { it.isNotEmpty() }?.let { needle ->
                    entries.filter {
                        it.title.contains(needle, ignoreCase = true) ||
                            it.authorName?.contains(needle, ignoreCase = true) == true
                    }
                } ?: entries
            val filtered =
                when (filter) {
                    BookmarkFilter.ALL -> matched

                    BookmarkFilter.DOWNLOADED ->
                        matched.filter {
                            it.offline is OfflineState.Downloaded || it.offline is OfflineState.Stale
                        }

                    BookmarkFilter.NEW_REPLIES -> matched.filter { it.offline is OfflineState.Stale }
                }
            return when (sort) {
                BookmarkSort.SITE -> filtered
                BookmarkSort.REPLIES -> filtered.sortedByDescending { it.commentCount ?: -1 }
                BookmarkSort.PENDING_FIRST -> filtered.sortedBy { it.offline.downloadRank }
            }
        }

    /** Whether 全选 has nothing left to add — which is when it becomes 取消全选. */
    val allVisibleSelected: Boolean
        get() = visible.isNotEmpty() && selection?.containsAll(visible.map { it.postId }) == true

    val selected: List<BookmarkEntry>
        get() = selection?.let { ids -> entries.filter { it.postId in ids } }.orEmpty()
}

/** Undownloaded first, then failures, then in flight, then what is already here. */
private val OfflineState.downloadRank: Int
    get() =
        when (this) {
            is OfflineState.NotDownloaded -> 0
            is OfflineState.Failed -> 1
            is OfflineState.Stale -> 2
            is OfflineState.Downloading -> 3
            is OfflineState.Downloaded -> 4
        }

/**
 * 收藏 — the signed-in user's, as its own screen (board i1).
 *
 * The whole list in one shot rather than through Paging, which is what the design asks for without
 * saying so: 「全部 12 / 已下载 5 / 有新回复 3」, 全选, and 「全部下载 · 7 篇」 are all statements about the
 * *whole* collection, and a paged list can only ever count the pages it happens to have. Bounded by
 * [MAX_PAGES] so a pathological account cannot turn the screen into an unbounded fetch; when that
 * bound bites, [BookmarksUiState.truncated] says so instead of the counts quietly being wrong.
 *
 * The site publishes nobody else's collections, so there is no uid here — this screen is only ever
 * about the signed-in account.
 */
class BookmarksViewModel(
    private val spaceRepository: UserSpaceRepository,
    private val postRepository: PostRepository,
    private val offline: OfflineLibrary,
    private val collectedMeta: CollectedPostMetaStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BookmarksUiState(offlineAvailable = offline.isAvailable))
    val uiState: StateFlow<BookmarksUiState> = _uiState.asStateFlow()

    /**
     * The list exactly as the site returned it, before anything local is laid over it.
     *
     * Kept apart from [BookmarksUiState.entries] because a row is built from three sources that
     * change independently — the site's list, what this device knows about those threads, and what
     * it has downloaded of them — and folding each new arrival into the previous row would make the
     * result depend on the order they happened to land in. Rebuilding from the three is the only
     * version where a later, better answer can replace an earlier, thinner one.
     */
    private val siteRows = MutableStateFlow<List<SpacePost>>(emptyList())

    private var loadJob: Job? = null
    private var estimateJob: Job? = null

    init {
        load(refresh = false)
        viewModelScope.launch {
            combine(siteRows, offline.states, collectedMeta.observe()) { rows, states, meta ->
                rows.map { post ->
                    post.toEntry(
                        offline = states[post.postId] ?: OfflineState.NotDownloaded,
                        known = meta[post.postId],
                    )
                }
            }.collect { entries -> _uiState.update { it.copy(entries = entries) } }
        }
        viewModelScope.launch {
            combine(offline.usage, offline.settings, ::Pair).collect { (usage, settings) ->
                _uiState.update { it.copy(usage = usage, offlineSettings = settings) }
            }
        }
    }

    fun refresh() = load(refresh = true)

    fun retry() = load(refresh = false)

    fun setFilter(filter: BookmarkFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun setSort(sort: BookmarkSort) {
        _uiState.update { it.copy(sort = sort) }
    }

    /**
     * Opens or closes 搜索.
     *
     * Local, over the list already in memory — which is only honest because the list *is* all of it.
     * The site has no endpoint that searches a collection, and a search box that quietly only looked
     * at the loaded page would be worse than none.
     */
    fun setSearching(searching: Boolean) {
        _uiState.update { it.copy(query = if (searching) it.query.orEmpty() else null) }
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    // --- multi-select -------------------------------------------------------------------------

    fun startSelection(postId: Long) {
        _uiState.update { it.copy(selection = setOf(postId)) }
        estimateSelection()
    }

    fun toggleSelection(postId: Long) {
        _uiState.update { state ->
            val current = state.selection ?: return@update state
            state.copy(selection = if (postId in current) current - postId else current + postId)
        }
        estimateSelection()
    }

    /** 全选 toggles: a tap when everything visible is already ticked clears it instead. */
    fun toggleSelectAll() {
        _uiState.update { state ->
            if (state.selection == null) return@update state
            val visible = state.visible.map { it.postId }.toSet()
            state.copy(selection = if (state.selection.containsAll(visible)) emptySet() else visible)
        }
        estimateSelection()
    }

    fun clearSelection() {
        _uiState.update { it.copy(selection = null, selectionEstimateBytes = null) }
    }

    /**
     * Re-asks what the current selection would weigh.
     *
     * Cancelling the previous ask is the point: ticking six rows in a row otherwise leaves six
     * answers racing, and the one that lands last is not necessarily the one for the last selection.
     */
    private fun estimateSelection() {
        estimateJob?.cancel()
        val ids = _uiState.value.selection
        if (ids.isNullOrEmpty()) {
            _uiState.update { it.copy(selectionEstimateBytes = null) }
            return
        }
        estimateJob =
            viewModelScope.launch {
                val bytes = runCatchingExceptCancellation { offline.estimateBytes(ids) }.getOrNull()
                _uiState.update { if (it.selection == ids) it.copy(selectionEstimateBytes = bytes) else it }
            }
    }

    // --- writes -------------------------------------------------------------------------------

    /**
     * Takes the selected threads out of the collection, optimistically.
     *
     * Optimistic because the alternative is a list that sits still for a round trip after an action
     * the design gives no confirmation to — and [restore] puts back exactly what failed, so a refusal
     * costs a flicker rather than a wrong list.
     */
    fun removeSelected(onDone: (removed: List<BookmarkEntry>, failed: SiteError?) -> Unit) {
        val target = _uiState.value.selected
        if (target.isEmpty()) return
        viewModelScope.launch {
            val ids = target.map { it.postId }.toSet()
            siteRows.update { rows -> rows.filterNot { row -> row.postId in ids } }
            _uiState.update { it.copy(selection = null) }
            val failure =
                target.firstNotNullOfOrNull { entry ->
                    runCatchingExceptCancellation { postRepository.setCollected(entry.postId, collected = false) }
                        .exceptionOrNull()
                        ?.toSiteError()
                }
            if (failure != null) load(refresh = true)
            onDone(target, failure)
        }
    }

    /** 撤销 for [removeSelected]. */
    fun restore(entries: List<BookmarkEntry>) {
        viewModelScope.launch {
            entries.forEach { entry ->
                runCatchingExceptCancellation { postRepository.setCollected(entry.postId, collected = true) }
            }
            load(refresh = true)
        }
    }

    fun downloadSelected() {
        val ids = _uiState.value.selection.orEmpty()
        if (ids.isEmpty()) return
        _uiState.update { it.copy(selection = null) }
        viewModelScope.launch { offline.download(ids) }
    }

    /** 全部下载: everything not already stored. Threads mid-download are left alone. */
    fun downloadPending() {
        val ids =
            _uiState.value.entries
                .filter { it.offline is OfflineState.NotDownloaded || it.offline is OfflineState.Failed }
                .map { it.postId }
        if (ids.isEmpty()) return
        viewModelScope.launch { offline.download(ids) }
    }

    /** The per-row download column: download, retry, catch up on replies — all the same request. */
    fun download(postId: Long) {
        viewModelScope.launch { offline.download(listOf(postId)) }
    }

    fun cancelDownload(postId: Long) {
        viewModelScope.launch { offline.cancel(postId) }
    }

    fun updateOfflineSettings(settings: OfflineSettings) {
        viewModelScope.launch { offline.updateSettings(settings) }
    }

    fun clearOffline() {
        viewModelScope.launch { offline.clearAll() }
    }

    // --- loading ------------------------------------------------------------------------------

    private fun load(refresh: Boolean) {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _uiState.update {
                    it.copy(isLoading = !refresh && it.entries.isEmpty(), isRefreshing = refresh, error = null)
                }
                runCatchingExceptCancellation { loadAllPages() }
                    .onSuccess { (posts, truncated) ->
                        // Whatever this load *did* carry is written down, because the endpoint is not
                        // reliably going to say it again and something on this device may want it
                        // when it does not — see [CollectedPostMetaStore].
                        collectedMeta.remember(posts.map(SpacePost::toKnownMeta).filterNot { it.isEmpty })
                        // What the site says each thread's reply count is *now* — the only half of
                        // 「离线版落后 N 条回复」 a stored copy cannot work out for itself. This screen
                        // is holding both numbers already, so it hands them over rather than leaving
                        // the library to go and ask for what the app was just given.
                        offline.noteReplyCounts(posts.mapNotNull { post -> post.commentCount?.let { post.postId to it } }.toMap())
                        siteRows.value = posts
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                isRefreshing = false,
                                error = null,
                                truncated = truncated,
                                // A thread removed on another device must not stay ticked.
                                selection =
                                state.selection?.intersect(posts.map { it.postId }.toSet()),
                            )
                        }
                    }.onFailure { throwable ->
                        _uiState.update {
                            it.copy(isLoading = false, isRefreshing = false, error = throwable.toSiteError())
                        }
                    }
            }
    }

    /**
     * Walks the collection endpoint to its end, or to [MAX_PAGES].
     *
     * A page that comes back empty ends the walk even when the payload claims another one: the
     * endpoint's `hasNext` is a guess when the response carries no paging metadata (see
     * `UserSpaceRepository`), and trusting it alone is what turns a missing field into 20 requests.
     */
    private suspend fun loadAllPages(): Pair<List<SpacePost>, Boolean> {
        val all = mutableListOf<SpacePost>()
        var page = 1
        while (page <= MAX_PAGES) {
            val result = spaceRepository.collections(page)
            all += result.items
            if (!result.hasNextPage || result.items.isEmpty()) return all to false
            page++
        }
        return all to true
    }

    companion object {
        /**
         * The walk's bound. At the endpoint's own page size this is several thousand collected
         * threads — far past any real account, and the point is only that the number exists.
         */
        const val MAX_PAGES = 20

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    BookmarksViewModel(
                        spaceRepository = container.userSpaceRepository,
                        postRepository = container.postRepository,
                        offline = container.offlineLibrary,
                        collectedMeta = container.collectedPostMetaStore,
                    )
                }
            }
    }
}

/**
 * One row: the site's answer, with what this device already knew filling the gaps it leaves.
 *
 * `list-collection` returns a title and, in practice, nothing else — no board, no author, no reply
 * count — which is why the same `ThreadRow` that carries four pieces of information on the front
 * page carries one here. [known] is what the site itself said about the thread somewhere else, so
 * the fallback is a remembered answer rather than an invented one, and the site's own value always
 * wins where it has one.
 */
private fun SpacePost.toEntry(
    offline: OfflineState,
    known: CollectedPostMeta?,
) = BookmarkEntry(
    postId = postId,
    title = title,
    categoryTitle = categoryTitle ?: known?.categoryTitle,
    categorySlug = categorySlug ?: known?.categorySlug,
    authorName = authorName ?: known?.authorName,
    commentCount = commentCount ?: known?.commentCount,
    createdAtText = createdAtText ?: known?.createdAtText,
    offline = offline,
)

/** What one row of the site's answer is worth writing down. */
private fun SpacePost.toKnownMeta() =
    CollectedPostMeta(
        postId = postId,
        title = title,
        categoryTitle = categoryTitle,
        categorySlug = categorySlug,
        authorName = authorName,
        commentCount = commentCount,
        createdAtText = createdAtText,
    )
