package io.github.nodyssey.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import io.github.nodyssey.data.local.CacheSessionEntity
import io.github.nodyssey.data.local.CommentEntity
import io.github.nodyssey.data.local.FeedPostRow
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.local.ReadHistoryRow
import io.github.nodyssey.data.local.ReadMarkEntity
import io.github.nodyssey.data.local.ReadingPositionEntity
import io.github.nodyssey.data.local.toSnapshot
import io.github.nodyssey.data.local.toSummary
import io.github.nodyssey.data.local.writeTransaction
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.PostDetail
import io.github.nodyssey.model.PostListPage
import io.github.nodyssey.model.PostSummary
import io.github.nodyssey.model.ReactionAction
import io.github.nodyssey.model.ThreadSnapshot
import io.github.plaza.core.AppClock
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * One row as the list renders it: the post, plus what the user has already seen of it.
 *
 * Read state is joined in at the data layer rather than looked up per row by the UI. A composable
 * that queries while it draws is how a scroll turns janky.
 */
data class FeedPost(
    val summary: PostSummary,
    val isRead: Boolean,
    /** Replies added since the user last opened the thread. Zero when unread or unchanged. */
    val newCommentCount: Int,
    /**
     * Which of the site's pages the row arrived on, or null when it did not come out of a feed —
     * local search matches the whole post cache, where the question has no answer.
     */
    val page: Int? = null,
)

/**
 * One line of the browsing history.
 *
 * Everything but [postId] and [lastReadAtMillis] can be null, and none of it comes from the `posts`
 * table: a thread opened from a notification or an external link has never been in a feed, so there
 * is no row there to join against. What is here was snapshotted when the thread was read.
 */
data class ReadHistoryEntry(
    val postId: Long,
    val title: String?,
    val authorName: String?,
    val authorUid: Long?,
    val categoryTitle: String?,
    val commentCount: Int?,
    val lastReadAtMillis: Long,
    /**
     * Where this thread was left off, if it has a bookmark at all.
     *
     * Nothing on the history screen draws it. It is here because removing a row deletes the bookmark
     * with it, and 撤销 has only this entry to put everything back from.
     */
    val readingPosition: ReadingPosition? = null,
)

/**
 * No results, and finished looking.
 *
 * A bare `PagingData.empty()` carries no load states, so every consumer sits in `Loading` forever —
 * a spinner for a search nobody has run. Spelling the terminal states out is what lets the screen
 * draw its empty state instead, and what stops `asSnapshot` in tests waiting for a page that is
 * never coming.
 */
fun emptyLoadedPagingData(): PagingData<FeedPost> =
    PagingData.empty(
        LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = true),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        ),
    )

/**
 * The offline-first post repository.
 *
 * Reads come from Room and only from Room; the network's only role is to write into it. Callers get a
 * [Flow] and never a one-shot getter, which is what stops a second copy of the data appearing in a
 * ViewModel field — the mistake this project already made once with the board list.
 */
interface PostRepository {
    /**
     * A pager backed by the database, refilled from the network by [FeedRemoteMediator].
     *
     * @param startPage where the window begins. Callers that simply want the feed leave it alone; it
     * is 首页翻页栏 that asks for anything else, and asking for a different one is what replaces the
     * stored window rather than appending to it.
     */
    fun feed(
        categorySlug: String?,
        sort: FeedSort,
        startPage: Int = FeedRemoteMediator.FIRST_PAGE,
    ): Flow<PagingData<FeedPost>>

    /**
     * How many pages the site last said this feed has; 1 until a page has been stored.
     *
     * Separate from [feed] because it is metadata about the list rather than a row in it: 首页翻页栏
     * has to draw "第 3 / 217 页" before the reader has scrolled anywhere, and a `PagingData` carries
     * no place to put a number that belongs to the whole feed.
     */
    fun feedTotalPages(
        categorySlug: String?,
        sort: FeedSort,
    ): Flow<Int>

    /**
     * Where the site's page [page] begins in the feed's own row order, or null when no row of it is
     * stored.
     *
     * 首页翻页栏 asks this to tell travelling from fetching apart. It cannot ask the list: the pager
     * runs with placeholders on and Room re-windows it on every write, so the rows of the page one
     * step away are almost always placeholders even though the reader scrolled through them a moment
     * ago. The database still has them, and this is the index they are at.
     */
    suspend fun feedRowIndexOfPage(
        categorySlug: String?,
        sort: FeedSort,
        page: Int,
    ): Int?

    /**
     * The site's own `/search?q=…`, read exactly like a board feed.
     *
     * Deliberately the same return type, the same pager and the same mediator as [feed]: search is
     * a list of NodeSeek posts served from a different route, and the moment it had a pipeline of
     * its own it grew a second, worse one — several requests per load, nothing cached, and a pager
     * that never admitted the end. Rows arrive with read state and reply badges for free, because
     * they come out of the same table.
     *
     * @param categorySlug one board or none; the site accepts exactly one and filters server-side.
     */
    fun searchFeed(
        query: String,
        categorySlug: String?,
        sort: FeedSort,
    ): Flow<PagingData<FeedPost>>

    /** Searches the local feed cache by title or author. */
    fun search(query: String): Flow<List<FeedPost>>

    /**
     * Marks every cached feed and thread stale.
     *
     * Called when the session changes. NodeSeek serves different content to a signed-in reader — whole
     * boards appear — so what is stored answered a different question and its freshness window is a
     * lie. The content stays readable until the refresh lands; only the timestamp is withdrawn.
     */
    suspend fun invalidateCaches()

    /**
     * Removes every post-derived value that may have been fetched with an authenticated session.
     *
     * The database cannot currently prove which rows came from a public page and which came from a
     * signed-in-only board, so logout must fail closed and clear the whole post cache. Boards and
     * settings are not session-scoped and are deliberately retained.
     */
    suspend fun clearSessionData()

    /** Clears downloaded post content while preserving the current session provenance marker. */
    suspend fun clearCache(
        isSignedIn: Boolean,
        fingerprint: Int,
    )

    /**
     * Reconciles persisted cache provenance with the cookie jar before cached rows are exposed.
     *
     * @return true when authenticated rows were cleared because the current process is signed out.
     */
    suspend fun reconcileSession(
        isSignedIn: Boolean,
        fingerprint: Int,
    ): Boolean

    /** The cached thread, emitting null until page 1 has ever been fetched. */
    fun thread(postId: Long): Flow<ThreadSnapshot?>

    /**
     * Fetches [page] and makes it the whole of the cached thread. Throws on failure.
     *
     * This is what a first read, a retry and a jump all are: one page, read fresh, with no claim that
     * anything either side of it is still accurate. Any other cached page is dropped — a comment
     * deleted since they were stored has shifted every floor after it.
     */
    suspend fun refreshThread(
        postId: Long,
        page: Int,
    )

    /**
     * Fetches [page] and adds it to the cached slice, keeping what is already there. Throws on failure.
     *
     * For the pages a reader reaches by continuing — the next one as they scroll off the end, the
     * previous one as they page back. A [page] that does not adjoin the cached slice cannot be
     * appended without leaving a hole in the scroll, so it is stored as a [refreshThread] instead.
     */
    suspend fun extendThread(
        postId: Long,
        page: Int,
    )

    /** True when the cached thread is recent enough that opening it need not hit the network. */
    suspend fun isThreadFresh(postId: Long): Boolean

    /**
     * True when the list is showing more replies than the last read of this thread accounted for —
     * the state the feed draws as "3 条新回复".
     *
     * Freshness cannot answer this on its own. A thread refreshed a minute ago is inside the cache
     * window, and a reply that arrived since is exactly what the badge on the row the reader just
     * tapped was pointing at; opening it on the cached copy showed the content they had already
     * read and left a manual refresh as the only way through.
     *
     * False for a thread no feed carries — a notification or an external link — where there is no
     * count to compare against and the freshness window is the whole of the answer.
     */
    suspend fun hasUnreadReplies(postId: Long): Boolean

    /** The comment pages the cache currently holds, or null when the thread was never fetched. */
    suspend fun cachedPages(postId: Long): IntRange?

    /**
     * Records that the user has opened this thread.
     *
     * The baseline for "new since" is taken from the stored post rather than passed in, so the badge
     * later compares like with like.
     */
    suspend fun markThreadRead(postId: Long)

    /**
     * Advances the unread baseline for one reply this account has just published.
     *
     * A normal read takes its baseline from the last feed refresh. That count necessarily predates
     * a reply sent from the detail screen, so leaving it unchanged makes the user's own floor appear
     * as "1 条新回复" when the feed next refreshes.
     */
    suspend fun noteOwnReplyPublished(postId: Long)

    /**
     * Spends one mark on a floor and writes the site's new tally through Room.
     *
     * Throws on refusal, carrying the site's own sentence. Nothing is applied optimistically: these
     * cost the reader real currency and cannot be taken back, so the count moves only once the site
     * has said it moved. A dropped connection therefore leaves the floor exactly as it was.
     */
    suspend fun react(
        postId: Long,
        commentId: Long,
        action: ReactionAction,
    )

    /** Today's remaining free 加鸡腿, or null when the site would not say. */
    suspend fun freeChickenLegs(): FreeChickenLegs?

    /**
     * Adds the thread to this account's collection, or takes it out. Throws on refusal.
     *
     * Whole-thread, not per floor: that is all the site offers.
     */
    suspend fun setCollected(
        postId: Long,
        collected: Boolean,
    )

    /** Threads this device has opened, most recent first. Local only — the site keeps no such list. */
    fun readHistory(): Flow<List<ReadHistoryEntry>>

    /** Forgets one thread: its history row, its unread baseline, and where it was left off. */
    suspend fun removeFromHistory(postId: Long)

    /**
     * Puts a just-removed row back, for 撤销 — including its bookmark, which the entry carries.
     *
     * The unread baseline comes back at the snapshot's reply count rather than the exact
     * `lastSeenCommentCount` that was deleted with it — the history entry does not carry that field.
     * The two are written from the same read and differ only for a thread whose replies were deleted
     * between reads, so an undo can at worst re-announce a reply the reader had already seen.
     */
    suspend fun restoreToHistory(entry: ReadHistoryEntry)

    /**
     * Drops everything past the current 保留条数.
     *
     * Called after the limit is lowered. Every other caller gets this for free when a thread is read;
     * this exists because a setting change has to take effect on rows nobody is about to re-read —
     * otherwise a thread the user just told the app to forget would keep greying out its feed row.
     *
     * Bounds the reading places by the same number, which is the whole reason they are trimmed here
     * rather than where they are written: 保留条数 is one answer to "how many threads does this app
     * remember", and the write path runs while somebody is scrolling.
     */
    suspend fun trimReadHistory()

    /**
     * Forgets every thread this device has read.
     *
     * Also clears the unread baselines, because they are the same rows: read marks do both jobs, and
     * the bookmarks, which are the same claim about the same threads. Callers must say so before
     * asking — every already-read thread goes back to looking unread and opening at its top.
     */
    suspend fun clearReadHistory()
}

@OptIn(ExperimentalPagingApi::class, ExperimentalCoroutinesApi::class)
class OfflineFirstPostRepository(
    private val database: NodeSeekDatabase,
    private val remote: PostRemoteDataSource,
    private val clock: AppClock,
    /*
     * Defaulted to absent so the many read-only tests that build this repository stay as short as
     * they are. Production always passes one; a build that did not would refuse the write outright
     * rather than pretend it landed.
     */
    private val reactions: PostReactionWriter? = null,
    /**
     * 临时显示被屏蔽内容, as a flow because it can change while a list is on screen.
     *
     * Every list of posts reads it here rather than each screen remembering to: blocking is account
     * state that the site applies to every route, so one feed honouring it and another not would be
     * the app disagreeing with itself. Default false — a caller that never wires it up hides blocked
     * rows, which is the site's own default.
     */
    private val showBlockedContent: Flow<Boolean> = flowOf(false),
    /** Absent for the same reason [reactions] is; see its note. */
    private val collections: PostCollectionWriter? = null,
    /**
     * 浏览历史保留条数, as a flow for the same reason [showBlockedContent] is one: it can change while
     * the history is on screen, and the list has to re-length itself when it does.
     */
    private val readHistoryLimit: Flow<Int> = flowOf(SettingsRepository.DEFAULT_READ_HISTORY_LIMIT),
    /**
     * 离线阅读's store, consulted only when the site cannot be reached.
     *
     * Absent by default for the same reason the writers above are: most of this class's tests never
     * go near it, and a build that did not wire it up simply has no fallback rather than a fake one.
     * Nothing here writes into it — downloads are the offline library's own business — and the copy
     * it hands back is stored into the ordinary post cache, so the detail screen needs to know
     * nothing about any of this.
     */
    private val offlineThreads: OfflineThreadReader? = null,
    /**
     * Where what this device knows about a collected thread is written down — see
     * [rememberCollectedThread]. Absent by default like the writers above, and a build without one
     * simply remembers nothing rather than remembering something invented.
     */
    private val collectedMeta: CollectedPostMetaStore? = null,
) : PostRepository {
    override fun feed(
        categorySlug: String?,
        sort: FeedSort,
        startPage: Int,
    ): Flow<PagingData<FeedPost>> {
        val feedKey = feedKeyFor(categorySlug, sort)
        return pagedFeed(feedKey, startPage) { page -> remote.loadList(categorySlug, page, sort) }
    }

    override fun feedTotalPages(
        categorySlug: String?,
        sort: FeedSort,
    ): Flow<Int> =
        database
            .feedDao()
            .remoteKeyStream(feedKeyFor(categorySlug, sort))
            .map { key -> key?.totalPages?.coerceAtLeast(1) ?: 1 }
            .distinctUntilChanged()

    override suspend fun feedRowIndexOfPage(
        categorySlug: String?,
        sort: FeedSort,
        page: Int,
    ): Int? {
        val feedKey = feedKeyFor(categorySlug, sort)
        // The same reveal the pager was built under; counting under the other one is off by however
        // many blocked posts sit above the page.
        val includeBlocked = showBlockedContent.first()
        val dao = database.feedDao()
        val sortIndex = dao.firstSortIndexOnPage(feedKey, page, includeBlocked) ?: return null
        return dao.countRowsBefore(feedKey, sortIndex, includeBlocked)
    }

    override fun searchFeed(
        query: String,
        categorySlug: String?,
        sort: FeedSort,
    ): Flow<PagingData<FeedPost>> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return flowOf(emptyLoadedPagingData())
        val feedKey = searchFeedKeyFor(normalized, categorySlug, sort)
        return pagedFeed(feedKey) { page ->
            // Swept on the first page only, which is where a refresh always starts: appends have
            // nothing to sweep, and doing it here rather than at submit time keeps it on the
            // mediator's dispatcher, inside the load that is about to write the replacement.
            if (page == FeedRemoteMediator.FIRST_PAGE) {
                database.writeTransaction {
                    database.feedDao().clearOtherSearchFeeds(SEARCH_FEED_KEY_PREFIX, feedKey)
                    database.feedDao().clearOtherSearchRemoteKeys(SEARCH_FEED_KEY_PREFIX, feedKey)
                }
            }
            remote.loadSearch(normalized, page, categorySlug, sort)
        }
    }

    /**
     * One pager shape for every list of posts, so a feed and a search can never drift apart.
     *
     * Flipping 临时显示被屏蔽内容 builds a new pager rather than filtering an existing one, because the
     * paging window is what the query decides: reveal has to change which rows exist, not which of
     * the loaded ones are drawn. The rows themselves are already in Room either way, so the reveal
     * costs a re-query and not a request.
     */
    private fun pagedFeed(
        feedKey: String,
        startPage: Int = FeedRemoteMediator.FIRST_PAGE,
        loadPage: suspend (page: Int) -> PostListPage,
    ): Flow<PagingData<FeedPost>> =
        showBlockedContent.distinctUntilChanged().flatMapLatest { includeBlocked ->
            Pager(
                config = FEED_PAGING_CONFIG,
                remoteMediator =
                FeedRemoteMediator(
                    feedKey = feedKey,
                    database = database,
                    clock = clock,
                    startPage = startPage,
                    loadPage = loadPage,
                ),
                pagingSourceFactory = { database.feedDao().pagingSource(feedKey, includeBlocked) },
            ).flow
        }.map { pagingData -> pagingData.map(FeedPostRow::toFeedPost) }

    override fun search(query: String): Flow<List<FeedPost>> {
        val escaped = query.trim().escapeLikePattern()
        if (escaped.isEmpty()) return flowOf(emptyList())
        return showBlockedContent.distinctUntilChanged().flatMapLatest { includeBlocked ->
            database.feedDao().search(escaped, includeBlocked)
        }.map { rows -> rows.map(FeedPostRow::toFeedPost) }
    }

    override suspend fun invalidateCaches() {
        database.feedDao().expireAllFeeds()
        database.postDetailDao().expireAllThreads()
    }

    override suspend fun clearSessionData() {
        database.writeTransaction {
            clearPostData()
            database.cacheSessionDao().upsert(
                CacheSessionEntity(authenticated = false, fingerprint = 0),
            )
        }
    }

    override suspend fun clearCache(
        isSignedIn: Boolean,
        fingerprint: Int,
    ) {
        database.writeTransaction {
            clearPostData()
            database.cacheSessionDao().upsert(
                CacheSessionEntity(
                    authenticated = isSignedIn,
                    fingerprint = if (isSignedIn) fingerprint else 0,
                ),
            )
        }
    }

    override suspend fun reconcileSession(
        isSignedIn: Boolean,
        fingerprint: Int,
    ): Boolean =
        database.writeTransaction {
            val previous = database.cacheSessionDao().find()
            val mustClear =
                previous?.authenticated == true &&
                    (!isSignedIn || previous.fingerprint != fingerprint)
            if (mustClear) clearPostData()
            database.cacheSessionDao().upsert(
                CacheSessionEntity(
                    authenticated = isSignedIn,
                    fingerprint = if (isSignedIn) fingerprint else 0,
                ),
            )
            mustClear
        }

    private suspend fun clearPostData() {
        // Positions reference posts, and comments reference details. Clear owners only after their
        // membership rows so foreign-key enforcement never observes an invalid graph.
        database.feedDao().clearAllFeedPositions()
        database.feedDao().clearAllRemoteKeys()
        database.feedDao().clearAllPosts()
        database.postDetailDao().clearAllThreads()
        database.readMarkDao().clearAll()
        database.readingPositionDao().clearAll()
        // The collection is one account's, and this runs when the account changes or goes away. It
        // was harmless to keep while 收藏 asked the site for its list every time; now that the list
        // is read off disk, keeping it would show the previous account's collection to whoever signs
        // in next.
        database.collectedPostMetaDao().clearAll()
    }

    override fun thread(postId: Long): Flow<ThreadSnapshot?> = database.postDetailDao().observeThread(postId).map { it?.toSnapshot() }

    override suspend fun refreshThread(
        postId: Long,
        page: Int,
    ) = loadThreadPage(postId, page, replacesWindow = true)

    override suspend fun extendThread(
        postId: Long,
        page: Int,
    ) = loadThreadPage(postId, page, replacesWindow = false)

    private suspend fun loadThreadPage(
        postId: Long,
        page: Int,
        replacesWindow: Boolean,
    ) {
        val fetched =
            runCatchingExceptCancellation { remote.loadDetail(postId, page) to clock.nowMillis() }
                .recoverCatching { thrown ->
                    val stored = storedPageFor(postId, page, thrown) ?: throw thrown
                    stored.detail to stored.downloadedAtMillis
                }.getOrThrow()
        val (detail, cachedAtMillis) = fetched
        database.postDetailDao().saveThreadPage(
            postId = postId,
            title = detail.title,
            body = detail.body,
            totalPages = detail.totalPages,
            page = page,
            comments =
            detail.comments.mapIndexed { position, content ->
                CommentEntity(
                    postId = postId,
                    page = page,
                    position = position,
                    content = content,
                )
            },
            nowMillis = cachedAtMillis,
            replacesWindow = replacesWindow,
            collected = detail.collected,
            collectionCount = detail.collectionCount,
            isAwarded = detail.isAwarded,
        )
        database.postDetailDao().trimTo(MAX_CACHED_THREADS)
        rememberFromPage(detail)
    }

    /**
     * Writes down what a page just said about a thread this account has collected.
     *
     * The most travelled of the three routes into `collected_post_meta`, and the one that makes the
     * 收藏 list fill itself in as it is used: tapping a bare row opens the very page that names its
     * board, its author, its avatar and when it was posted, so by the time the reader comes back the
     * row they tapped is complete.
     *
     * Gated on the page's own `collected`, not on the request: this runs for every thread anyone
     * opens, and the table is about the ones in this account's collection. Null — a page carrying no
     * `__config__`, which is a signed-out read — is not treated as a yes, and a signed-out reader has
     * no collection for it to be about.
     */
    private suspend fun rememberFromPage(detail: PostDetail) {
        val store = collectedMeta ?: return
        if (detail.collected != true) return
        val body = detail.body
        val meta =
            CollectedPostMeta(
                postId = detail.postId,
                title = detail.title.takeIf { it.isNotBlank() },
                categoryTitle = body?.categoryTitle,
                authorName = body?.authorName,
                avatarUrl = body?.avatarUrl,
                authorUid = body?.authorUid,
                createdAtText = body?.createdAtText,
            )
        if (!meta.isEmpty) store.remember(meta)
    }

    /**
     * The downloaded copy of this page, when the site could not be reached and one exists.
     *
     * Only for failures that are about *reaching* the site — no connection, or the site answering
     * with its own breakage. A refusal is an answer: signed out, level-gated or deleted must reach
     * the reader rather than being papered over with a copy from last week. A Cloudflare challenge
     * is deliberately on that side of the line too, even though it is not about this thread: the
     * screen's recovery for it is to open a WebView and clear it, and a reader served a stored copy
     * instead would never find out their session needs attention.
     *
     * The copy is stored under its own download timestamp rather than under now, so the cache does
     * not call a week-old page fresh and skip the refresh that would replace it.
     */
    private suspend fun storedPageFor(
        postId: Long,
        page: Int,
        thrown: Throwable,
    ): StoredThreadPage? {
        val reader = offlineThreads ?: return null
        val error = (thrown as? SiteException)?.error ?: return null
        val unreachable = error is SiteError.Network || (error is SiteError.Http && error.statusCode >= 500)
        return if (unreachable) reader.storedPage(postId, page) else null
    }

    override suspend fun isThreadFresh(postId: Long): Boolean {
        val detail = database.postDetailDao().findDetail(postId) ?: return false
        return clock.nowMillis() - detail.cachedAtMillis < THREAD_CACHE_TTL_MILLIS
    }

    override suspend fun hasUnreadReplies(postId: Long): Boolean {
        val listed = database.feedDao().commentCount(postId) ?: return false
        val seen = database.readMarkDao().find(postId)?.lastSeenCommentCount ?: return false
        return listed > seen
    }

    override suspend fun cachedPages(postId: Long): IntRange? =
        database.postDetailDao().findDetail(postId)?.let { it.firstLoadedPage..it.lastLoadedPage }

    override suspend fun markThreadRead(postId: Long) {
        val listRow = database.feedDao().findPost(postId)
        // The cached thread is the better source for a deep link: it is what was just rendered,
        // whereas `posts` holds nothing at all for a thread no feed ever carried.
        val cached = database.postDetailDao().findDetail(postId)
        database.readMarkDao().markRead(
            postId = postId,
            // Zero when the post was never in a cached list (a deep link), which is the honest
            // baseline: every reply is then genuinely new to this user.
            commentCount = listRow?.commentCount ?: 0,
            nowMillis = clock.nowMillis(),
            title = cached?.title ?: listRow?.title,
            authorName = cached?.body?.authorName ?: listRow?.authorName,
            authorUid = cached?.body?.authorUid ?: listRow?.authorUid,
            categoryTitle = cached?.body?.categoryTitle ?: listRow?.categoryTitle,
            totalComments = listRow?.commentCount,
        )
        trimReadHistory()
    }

    override suspend fun noteOwnReplyPublished(postId: Long) {
        val previous = database.readMarkDao().find(postId)
        val listRow = database.feedDao().findPost(postId)
        val cached = database.postDetailDao().findDetail(postId)
        database.readMarkDao().markRead(
            postId = postId,
            // Advance what was already seen by exactly our one accepted reply. Taking the latest
            // feed count here could also consume somebody else's reply that arrived concurrently.
            commentCount = (previous?.lastSeenCommentCount ?: listRow?.commentCount ?: 0) + 1,
            nowMillis = clock.nowMillis(),
            title = cached?.title ?: listRow?.title,
            authorName = cached?.body?.authorName ?: listRow?.authorName,
            authorUid = cached?.body?.authorUid ?: listRow?.authorUid,
            categoryTitle = cached?.body?.categoryTitle ?: listRow?.categoryTitle,
            totalComments = (previous?.commentCount ?: listRow?.commentCount)?.plus(1),
        )
        trimReadHistory()
    }

    override fun readHistory(): Flow<List<ReadHistoryEntry>> =
        readHistoryLimit
            .distinctUntilChanged()
            .flatMapLatest { limit -> database.readMarkDao().observeHistory(limit) }
            .map { rows -> rows.map(ReadHistoryRow::toHistoryEntry) }

    override suspend fun removeFromHistory(postId: Long) {
        database.readMarkDao().delete(postId)
        database.readingPositionDao().delete(postId)
    }

    override suspend fun restoreToHistory(entry: ReadHistoryEntry) {
        database.readMarkDao().upsert(
            ReadMarkEntity(
                postId = entry.postId,
                lastReadAtMillis = entry.lastReadAtMillis,
                lastSeenCommentCount = entry.commentCount ?: 0,
                title = entry.title,
                authorName = entry.authorName,
                authorUid = entry.authorUid,
                categoryTitle = entry.categoryTitle,
                commentCount = entry.commentCount,
            ),
        )
        // Restored as freshly written rather than at the moment it was earned, because that moment
        // went with the deleted row. The only thing this timestamp decides is the trim order, so at
        // worst an undone deletion keeps a bookmark a little longer than it would have.
        entry.readingPosition?.let { position ->
            database.readingPositionDao().upsert(
                ReadingPositionEntity(
                    postId = entry.postId,
                    page = position.page,
                    floor = position.floor,
                    updatedAtMillis = clock.nowMillis(),
                ),
            )
        }
    }

    override suspend fun trimReadHistory() {
        val limit = readHistoryLimit.first()
        // Skipped rather than run with Int.MAX_VALUE: 无上限 should not cost a full-table DELETE scan
        // on every thread the user opens.
        if (limit == SettingsRepository.READ_HISTORY_UNLIMITED) return
        database.readMarkDao().trimTo(limit)
        // The same number bounds the bookmarks, and this is the only place that has to know it: a
        // place can only exist for a thread that was opened, and opening one is what runs this.
        database.readingPositionDao().trimTo(limit)
    }

    override suspend fun clearReadHistory() {
        database.readMarkDao().clearAll()
        database.readingPositionDao().clearAll()
    }

    override suspend fun react(
        postId: Long,
        commentId: Long,
        action: ReactionAction,
    ) {
        val writer = reactions ?: throw SiteException(SiteError.NotWired)
        val outcome = writer.react(postId = postId, commentId = commentId, action = action)
        database.postDetailDao().updateReactions(postId, commentId) { previous ->
            previous.applying(action, outcome)
        }
    }

    override suspend fun freeChickenLegs(): FreeChickenLegs? = reactions?.freeChickenLegs()

    override suspend fun setCollected(
        postId: Long,
        collected: Boolean,
    ) {
        val writer = collections ?: throw SiteException(SiteError.NotWired)
        val outcome = writer.setCollected(postId = postId, collected = collected)
        // The site's echo, not the request: see [CollectionOutcome.collected].
        database.postDetailDao().updateCollection(postId, outcome.collected, outcome.postCollectionCount)
        if (outcome.collected) {
            rememberCollectedThread(postId)
        } else {
            // The list 收藏 draws lives on this device now, so a star pressed off here has to reach
            // it — otherwise the thread is gone from the site's collection and still on the screen
            // until the next successful walk, which on a train is never.
            collectedMeta?.forget(listOf(postId))
        }
    }

    /**
     * Writes down what this device already knows about a thread the moment it is collected.
     *
     * The star is pressed on a page that has just been read, so this is the one moment when the app
     * is holding the board, the author, the reply count and the posting time — and it is also the
     * last moment, because `list-collection` will not repeat any of them and the read mark that
     * carries some of them is trimmed by 浏览历史保留条数.
     *
     * Three sources, coalesced rather than ranked, because they know different things: the feed row
     * has the board and the site's reply count, the thread's opening post has the author and when it
     * was posted, and the read mark is what is left of both after the thread leaves the feed cache.
     * Nothing here is derived — every value was said by the site at some point.
     */
    private suspend fun rememberCollectedThread(postId: Long) {
        val store = collectedMeta ?: return
        val listed = database.feedDao().findPost(postId)
        val cached = database.postDetailDao().findDetail(postId)
        val mark = database.readMarkDao().find(postId)
        val body = cached?.body
        val meta =
            CollectedPostMeta(
                postId = postId,
                title = cached?.title ?: listed?.title ?: mark?.title,
                categoryTitle = listed?.categoryTitle ?: body?.categoryTitle ?: mark?.categoryTitle,
                categorySlug = listed?.categorySlug,
                authorName = body?.authorName ?: listed?.authorName ?: mark?.authorName,
                avatarUrl = body?.avatarUrl ?: listed?.avatarUrl,
                authorUid = body?.authorUid ?: listed?.authorUid ?: mark?.authorUid,
                commentCount = listed?.commentCount ?: mark?.commentCount,
                createdAtText = body?.createdAtText,
            )
        if (!meta.isEmpty) store.remember(meta)
    }

    companion object {
        /**
         * What NodeSeek actually renders per list page, measured at 49 on the front page and rounded
         * up. Paging's window and the network page want to be the same size; when the window is
         * smaller, Paging asks the mediator to append while a page it already fetched is still unread.
         */
        const val NETWORK_PAGE_SIZE = 50

        /**
         * The window every list of posts is read through — the feed and search alike, so the two can
         * never drift apart.
         *
         * [PagingConfig.enablePlaceholders] is on, and is not negotiable for a Room-backed pager.
         * Room invalidates this `PagingSource` on every write to `posts`, `feed_positions` or
         * `post_read_marks` — so on every page the mediator appends, and on every thread the reader
         * opens, because opening one writes its read mark. Paging answers an invalidation by loading
         * one [NETWORK_PAGE_SIZE] window around the anchor and handing the UI a new list.
         *
         * With placeholders off, that new list is *re-based*: the row being read stops being item 217
         * and becomes item 25 of a fifty-item list. `LazyColumn` follows an item by key across a
         * window of a few dozen positions, so a shift that large is not a move it can follow — it
         * keeps the old index, finds it out of range, and clamps to the end of what it now has. The
         * reader lands near the top of the feed having touched nothing. That is the "opening a post
         * throws me back to the top" bug, and it was never in the navigation layer at all.
         *
         * With placeholders on, Room reports the full row count and indices are absolute: item 217 is
         * item 217 before and after the reload, so there is nothing for the list to recover from. The
         * price is a placeholder row for anything outside the window, which at this prefetch distance
         * is rarely on screen.
         */
        internal val FEED_PAGING_CONFIG =
            PagingConfig(
                pageSize = NETWORK_PAGE_SIZE,
                // Matches the old hand-rolled "load when eight rows from the end" heuristic.
                prefetchDistance = 8,
                initialLoadSize = NETWORK_PAGE_SIZE,
                enablePlaceholders = true,
            )

        /**
         * Threads go stale faster than they are re-read, so this is short. It only decides whether
         * *opening* a cached thread also refreshes it — the cached copy shows either way.
         */
        const val THREAD_CACHE_TTL_MILLIS = 2 * 60 * 1000L

        /** Enough to cover a browsing session without letting the cache grow forever. */
        const val MAX_CACHED_THREADS = 60
    }
}

private fun ReadHistoryRow.toHistoryEntry(): ReadHistoryEntry =
    ReadHistoryEntry(
        postId = mark.postId,
        title = mark.title,
        authorName = mark.authorName,
        authorUid = mark.authorUid,
        categoryTitle = mark.categoryTitle,
        commentCount = mark.commentCount,
        lastReadAtMillis = mark.lastReadAtMillis,
        readingPosition = readingPage?.let { ReadingPosition(page = it, floor = readingFloor) },
    )

private fun FeedPostRow.toFeedPost(): FeedPost {
    val seen = lastSeenCommentCount
    return FeedPost(
        summary = post.toSummary(),
        isRead = lastReadAtMillis != null,
        // coerceAtLeast because a deleted comment can push the current count below the seen one.
        newCommentCount = if (seen == null) 0 else ((post.commentCount ?: 0) - seen).coerceAtLeast(0),
        page = feedPage,
    )
}

private fun String.escapeLikePattern(): String =
    replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
