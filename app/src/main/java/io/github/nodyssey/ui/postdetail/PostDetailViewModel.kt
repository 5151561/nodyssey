package io.github.nodyssey.ui.postdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException
import io.github.nodyssey.core.runCatchingExceptCancellation
import io.github.nodyssey.data.FreeChickenLegs
import io.github.nodyssey.data.NoReadingPositions
import io.github.nodyssey.data.PostRepository
import io.github.nodyssey.data.ReadingPosition
import io.github.nodyssey.data.ReadingPositionStore
import io.github.nodyssey.data.session.SessionState
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.model.PostContent
import io.github.nodyssey.model.ReactionAction
import io.github.nodyssey.ui.postlist.toNodeSeekError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for one thread, reading from the database and writing through it.
 *
 * The thread is *observed*, never fetched-and-held: [PostRepository.thread] emits whatever Room holds,
 * so a cached thread paints on the first frame and offline it paints at all. A refresh writes into
 * Room and the new content arrives through the same observation — there is no second path by which
 * content reaches the screen.
 *
 * A failed refresh therefore does not blank the screen. The error is reported, but cached comments
 * stay visible underneath it, which is the behaviour that makes a flaky connection tolerable.
 */
class PostDetailViewModel(
    private val postId: Long,
    private val repository: PostRepository,
    session: StateFlow<SessionState> = MutableStateFlow(SessionState()),
    /** The floor a notification or a quote came in on, as the site labels it (`"#127"`). */
    private val initialFloor: String? = null,
    /** The page a `/post-703863-4` link named, when the thread was opened from one. */
    private val initialPage: Int? = null,
    /**
     * 临时显示被屏蔽内容.
     *
     * A floor the site marked blocked is downloaded and cached like any other, so revealing is a
     * change of view and not of content — no re-fetch, and switching it back re-collapses what is
     * already on screen.
     */
    showBlockedContent: StateFlow<Boolean> = MutableStateFlow(false),
    /** Where this thread was left off last time, and where this read's own place is written. */
    private val readingPositions: ReadingPositionStore = NoReadingPositions,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PostDetailUiState())
    val uiState: StateFlow<PostDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /** Where the reader is *now*, reported by the screen as it scrolls. See [recordReadingPosition]. */
    private val readingPosition = MutableStateFlow<ReadingPosition?>(null)

    init {
        // Reconcile session provenance before collecting Room. A restored detail destination can be
        // the first entry composed after process death, so relying on the list ViewModel to do this
        // would allow an authenticated snapshot to flash on a signed-out cold start.
        viewModelScope.launch {
            repository.reconcileSession(
                isSignedIn = session.value.isSignedIn,
                fingerprint = session.value.fingerprint,
            )
            repository.thread(postId).collect { thread ->
                if (thread == null) {
                    // Logout deletes the Room row while this Navigation 3 entry can remain alive in a
                    // background tab. Clear the mirrored UI state as well or the composable would keep
                    // rendering the last authenticated snapshot after its owner was removed.
                    //
                    // Everything cleared here is *content*, and [pendingScroll] deliberately is not:
                    // null is also what a thread nobody has cached looks like, which is what every
                    // thread opened from a notification looks like for the length of its first fetch.
                    // Clearing the floor here threw away the one thing that read was opened for,
                    // before the floors it named could possibly have arrived.
                    _uiState.update {
                        it.copy(
                            title = "",
                            body = null,
                            comments = emptyList(),
                            commentPages = emptyList(),
                            firstLoadedPage = 1,
                            lastLoadedPage = 1,
                            totalPages = 1,
                            hasNextPage = false,
                            collected = null,
                            collectionCount = null,
                        )
                    }
                    return@collect
                }

                _uiState.update { state ->
                    state.copy(
                        title = thread.title,
                        body = thread.body,
                        comments = thread.comments,
                        commentPages = thread.commentPages,
                        firstLoadedPage = thread.firstLoadedPage,
                        lastLoadedPage = thread.lastLoadedPage,
                        totalPages = thread.totalPages,
                        hasNextPage = thread.hasNextPage,
                        collected = thread.collected,
                        collectionCount = thread.collectionCount,
                    )
                }

                // "Read" means content actually reached the screen — which is exactly this emission,
                // and the only signal that covers all the ways it can happen: a fresh fetch, a cache
                // hit that skipped the network, or a cached thread read in aeroplane mode.
                //
                // Marking read on *opening* instead looked equivalent and was not: opening an
                // uncached post offline shows nothing but an error, and the thread still ended up
                // dimmed in the list as though it had been read.
                repository.markThreadRead(postId)
            }
        }

        showBlockedContent
            .onEach { show -> _uiState.update { it.copy(showBlockedContent = show) } }
            .launchIn(viewModelScope)

        // The reply editor is the only thing here that needs an account, and it needs to know before
        // it opens rather than after a failed publish.
        session
            .onEach { sessionState -> _uiState.update { it.copy(isSignedIn = sessionState.isSignedIn) } }
            .launchIn(viewModelScope)

        // Coming back from the WebView signed in, or with a challenge cleared, is the one case where a
        // thread that was "fresh" a second ago is worth re-fetching: a locked thread has content now.
        // `drop(1)` skips the cookies we started with — a cold start is not a session change.
        session
            .distinctUntilChangedBy { it.generation }
            .drop(1)
            .onEach { sessionState ->
                val cleared =
                    repository.reconcileSession(
                        isSignedIn = sessionState.isSignedIn,
                        fingerprint = sessionState.fingerprint,
                    )
                if (sessionState.isSignedIn && !cleared) repository.invalidateCaches()
                refresh()
            }.launchIn(viewModelScope)

        // The stored place is read *before* this read starts writing over it, and then held for the
        // whole visit. That ordering is the feature: the thread opens at its top, so the first thing
        // this read would record is page 1, and a resume offer recomputed from the store would be
        // gone by the time the reader could reach for it.
        viewModelScope.launch {
            val resume = readingPositions.readingPosition(postId)
            _uiState.update { it.copy(resumePosition = resume) }
            readingPosition
                .filterNotNull()
                .distinctUntilChanged()
                // Scrolling past a floor moves the position; writing every one of them would put a
                // database write on each frame of a fling. The reader only has to stop somewhere.
                .debounce(POSITION_WRITE_DELAY_MILLIS)
                .collect { position -> readingPositions.setReadingPosition(postId, position) }
        }

        viewModelScope.launch {
            // Opening on a notification's floor, or on the page a `/post-703863-4` link named, starts
            // the thread there. Page 1 is only the default, not where every read begins.
            val start = startPage()
            if (initialFloor != null || start > 1) {
                _uiState.update { it.copy(pendingScroll = PendingScroll(start, initialFloor)) }
            }
            // A thread read moments ago needs no request — but only if the page being asked for is
            // one of the pages it cached; freshness says nothing about a page nobody has fetched.
            val cached = repository.cachedPages(postId)
            if (cached == null || start !in cached || !repository.isThreadFresh(postId)) {
                load(page = start, replacesWindow = true)
            }
        }
    }

    /** Where this thread opens: the floor's page, the link's page, or the top. */
    private fun startPage(): Int =
        NodeSeekSite
            .parseFloorNumber(initialFloor)
            ?.let(NodeSeekSite::pageOfFloor)
            ?: initialPage?.coerceAtLeast(1)
            ?: 1

    fun refresh() = load(page = _uiState.value.firstLoadedPage, replacesWindow = true)

    /**
     * Comments are paginated on the site but read as one thread on a phone, so the next page is
     * appended to the same list rather than replacing it. The append itself happens in the database.
     */
    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoading || state.isAppending || !state.hasNextPage) return
        load(page = state.lastLoadedPage + 1, replacesWindow = false)
    }

    /**
     * Brings [page] onto the screen, fetching it when it is not one of the loaded ones.
     *
     * The site serves any page directly, so a jump is one request: page 12 is fetched as page 12 and
     * becomes what the screen shows. It used to be reached by fetching pages 2 through 12 in turn, to
     * keep the loaded pages a prefix of the thread — which on a long thread meant dozens of requests
     * and, in practice, a control that never arrived anywhere it had not already been.
     *
     * Only the pages either side of the loaded slice extend it. Those are the ones a reader reaches
     * by continuing to read, and joining them on keeps the thread one uninterrupted scroll.
     */
    fun loadPage(page: Int) = bringIntoView(page, floor = null)

    /**
     * Scrolls to [floor], loading the page it lives on when it is not on screen.
     *
     * The site names a floor without saying where it is — a notification carries `floor_id`, a quote
     * reference an `#4` anchor — so the page is computed from the floor number.
     */
    fun jumpToFloor(floor: String) {
        val number = NodeSeekSite.parseFloorNumber(floor) ?: return
        bringIntoView(NodeSeekSite.pageOfFloor(number), floor = floor)
    }

    /**
     * Returns to where this thread was left off, exactly — the floor when one was recorded, the page
     * otherwise. A no-op when nothing was: the control that offers this hides itself in that case.
     */
    fun resumeReading() {
        val state = _uiState.value
        val resume = state.resumePosition ?: return
        val lastPage = state.totalPages.coerceAtLeast(1)
        // A thread can lose pages between visits — floors get deleted and everything after them moves
        // up. The last page is then the closest thing left to where the reader was, and the floor
        // they stopped on is certainly not on it.
        if (resume.page > lastPage) bringIntoView(lastPage, floor = null) else bringIntoView(resume.page, resume.floor)
    }

    /**
     * Reports where the reader is, so the next visit can offer to come back to it.
     *
     * Called freely as the list scrolls; the write behind it is debounced, and an unchanged position
     * costs nothing.
     */
    fun recordReadingPosition(
        page: Int,
        floor: String?,
    ) {
        readingPosition.value = ReadingPosition(page, floor)
    }

    private fun bringIntoView(
        page: Int,
        floor: String?,
    ) {
        val state = _uiState.value
        val target = page.coerceAtLeast(1)
        if (target > state.totalPages) return
        val scroll = PendingScroll(target, floor)
        if (target in state.firstLoadedPage..state.lastLoadedPage) {
            _uiState.update { it.copy(pendingScroll = scroll) }
            return
        }
        // Deliberately *not* guarded on [PostDetailUiState.isAppending]. The page control lives in the
        // bar at the foot of the list, which is exactly where auto-append is running, so refusing a
        // jump mid-append refused most of them — silently, since nothing on screen says why. A jump is
        // the reader overriding the append, and [load] cancels the request it overrides.
        val adjoins = target == state.firstLoadedPage - 1 || target == state.lastLoadedPage + 1
        load(page = target, replacesWindow = !adjoins, scrollTo = scroll)
    }

    /** The screen has scrolled to [PostDetailUiState.pendingScroll]; stop asking it to. */
    fun onScrollHandled() {
        _uiState.update { it.copy(pendingScroll = null) }
    }

    /**
     * Spends one mark on a floor.
     *
     * The new count is never written here: [PostRepository.react] puts it through Room and it arrives
     * by the same observation as every other change to the thread. All this holds is which floor is
     * mid-flight, so the row can show it and a second tap cannot double-spend.
     *
     * The guard against re-reacting is deliberately kept as well as the site's: theirs answers
     * "已经进行过加鸡腿操作" *after* a round trip, and a reader whose first tap was slow should not be
     * able to send a second in the meantime.
     */
    fun react(
        commentId: Long,
        action: ReactionAction,
    ) {
        if (_uiState.value.pendingReaction != null) return
        _uiState.update { it.copy(pendingReaction = PendingReaction(commentId, action), reactionFailure = null) }
        viewModelScope.launch {
            runCatchingExceptCancellation { repository.react(postId, commentId, action) }
                .onSuccess { _uiState.update { it.copy(pendingReaction = null) } }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            pendingReaction = null,
                            reactionFailure =
                            ReactionFailure(
                                error = throwable.toNodeSeekError(),
                                // The site's own sentence when it sent one — "鸡腿不足" says more
                                // than any wording of ours could from a status code.
                                detail = (throwable as? NodeSeekException)?.detail,
                            ),
                        )
                    }
                }
        }
    }

    /** The failure has been shown; stop holding it. */
    fun onReactionFailureShown() {
        _uiState.update { it.copy(reactionFailure = null) }
    }

    /**
     * Collects the thread, or takes it out of the collection.
     *
     * A no-op while [PostDetailUiState.collected] is null: no fetched page has said which way the
     * toggle points, and sending "add" on a guess would silently un-collect a thread the reader had
     * already saved.
     *
     * Deliberately not optimistic, unlike most toggles. The star's truth lives in Room and arrives by
     * the same observation as the rest of the thread; flipping it locally first means two writers for
     * one value, and the loser is whichever the reader was actually looking at. [collectPending]
     * greys the star for the round trip instead, which says the same thing without lying about it.
     */
    fun toggleCollect() {
        val state = _uiState.value
        val current = state.collected ?: return
        if (state.collectPending) return
        _uiState.update { it.copy(collectPending = true, collectFailure = null) }
        viewModelScope.launch {
            runCatchingExceptCancellation { repository.setCollected(postId, !current) }
                .onSuccess { _uiState.update { it.copy(collectPending = false) } }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            collectPending = false,
                            collectFailure =
                            ReactionFailure(
                                error = throwable.toNodeSeekError(),
                                detail = (throwable as? NodeSeekException)?.detail,
                            ),
                        )
                    }
                }
        }
    }

    /** The collection failure has been shown; stop holding it. */
    fun onCollectFailureShown() {
        _uiState.update { it.copy(collectFailure = null) }
    }

    /**
     * Loads today's free 加鸡腿 allowance so the confirmation can say whether this one is free.
     *
     * Called when the reader opens that confirmation, not on entering the thread: it is one request
     * per thread read that only ever changes a sentence, and most reads never open the dialog.
     */
    fun loadFreeChickenLegs() {
        if (_uiState.value.freeChickenLegs != null) return
        viewModelScope.launch {
            val quota = runCatchingExceptCancellation { repository.freeChickenLegs() }.getOrNull()
            _uiState.update { it.copy(freeChickenLegs = quota) }
        }
    }

    /**
     * @param replacesWindow true when [page] becomes the whole of the cached thread — a first read, a
     *   retry, or a jump — and false when it joins the pages already loaded.
     */
    private fun load(
        page: Int,
        replacesWindow: Boolean,
        scrollTo: PendingScroll? = null,
    ) {
        loadJob?.cancel()
        _uiState.update {
            it.copy(isLoading = replacesWindow, isAppending = !replacesWindow, error = null)
        }
        loadJob =
            viewModelScope.launch {
                runCatchingExceptCancellation {
                    if (replacesWindow) {
                        repository.refreshThread(postId, page)
                    } else {
                        repository.extendThread(postId, page)
                    }
                }.onSuccess {
                    // Neither content nor the read mark is applied here: both follow from the Room
                    // observation above, so there is exactly one path by which either can happen.
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isAppending = false,
                            pendingScroll = scrollTo ?: it.pendingScroll,
                        )
                    }
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

    fun postUrl(): String = NodeSeekSite.BASE_URL + NodeSeekSite.postPath(postId, _uiState.value.lastLoadedPage)

    companion object {
        fun factory(
            container: AppContainer,
            postId: Long,
            initialFloor: String? = null,
            initialPage: Int? = null,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    PostDetailViewModel(
                        postId,
                        container.postRepository,
                        container.sessionRepository.state,
                        initialFloor,
                        initialPage,
                        container.settingsRepository.showBlockedContent,
                        container.readingPositionStore,
                    )
                }
            }

        /**
         * How long the reader has to stop scrolling before their place is written.
         *
         * Long enough that a fling through twenty floors is one write rather than twenty, short
         * enough that leaving the thread the moment you stop still records where you stopped.
         */
        private const val POSITION_WRITE_DELAY_MILLIS = 500L
    }
}

data class PostDetailUiState(
    val title: String = "",
    val body: PostContent? = null,
    val comments: List<PostContent> = emptyList(),
    /** The site page each comment came from, index-aligned with [comments]. */
    val commentPages: List<Int> = emptyList(),
    /** The first site page held in [comments] — not always 1, since a jump loads its page alone. */
    val firstLoadedPage: Int = 1,
    /** The last site page held in [comments]. */
    val lastLoadedPage: Int = 1,
    val totalPages: Int = 1,
    val hasNextPage: Boolean = false,
    val isLoading: Boolean = false,
    val isAppending: Boolean = false,
    val isSignedIn: Boolean = false,
    /** 临时显示被屏蔽内容 — draws the blocked floors instead of collapsing them. */
    val showBlockedContent: Boolean = false,
    /** Where the screen should scroll once the content it names has arrived in [comments]. */
    val pendingScroll: PendingScroll? = null,
    /**
     * Where this thread was left off on a previous visit, or null when it was never read.
     *
     * Read once when the screen opens and then held for the visit, so it keeps meaning "last time"
     * rather than sliding along with the scroll it is offered against.
     */
    val resumePosition: ReadingPosition? = null,
    val error: NodeSeekError? = null,
    /** The floor whose reaction is in flight, so its row can show it and refuse a second tap. */
    val pendingReaction: PendingReaction? = null,
    /** A refused reaction, held until the screen has shown it once. */
    val reactionFailure: ReactionFailure? = null,
    /** Today's free 加鸡腿 allowance; null until [PostDetailViewModel.loadFreeChickenLegs] answers. */
    val freeChickenLegs: FreeChickenLegs? = null,
    /**
     * Whether this account collects the thread; null means no fetched page has said.
     *
     * Null is why the star can be absent rather than merely unlit — the site answers this question
     * only inside a post page, so before one arrives there is nothing honest to draw.
     */
    val collected: Boolean? = null,
    val collectionCount: Int? = null,
    /** A collection toggle is in flight, so the star refuses a second tap until it lands. */
    val collectPending: Boolean = false,
    /** A refused collection toggle, held until the screen has shown it once. */
    val collectFailure: ReactionFailure? = null,
)

data class PendingReaction(
    val commentId: Long,
    val action: ReactionAction,
)

/**
 * A scroll the screen still owes the reader.
 *
 * [page] is what decides whether the target has arrived — a floor that is not in the list yet is
 * indistinguishable from one that was deleted, but "is this page loaded" is answerable. [floor] is
 * the precise target when the site named one; without it the page's first floor will do.
 */
data class PendingScroll(
    val page: Int,
    val floor: String? = null,
)

/**
 * Why a reaction did not land.
 *
 * [detail] is the site's own sentence and wins when there is one; [error] is the fallback for the
 * failures that never reached the site at all.
 */
data class ReactionFailure(
    val error: NodeSeekError,
    val detail: String?,
)
