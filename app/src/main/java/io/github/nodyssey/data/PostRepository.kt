package io.github.nodyssey.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import io.github.nodyssey.core.AppClock
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException
import io.github.nodyssey.data.local.CacheSessionEntity
import io.github.nodyssey.data.local.CommentEntity
import io.github.nodyssey.data.local.FeedPostRow
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.local.toSnapshot
import io.github.nodyssey.data.local.toSummary
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.PostListPage
import io.github.nodyssey.model.PostSummary
import io.github.nodyssey.model.ReactionAction
import io.github.nodyssey.model.ThreadSnapshot
import kotlinx.coroutines.flow.Flow
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
)

/**
 * No results, and finished looking.
 *
 * A bare `PagingData.empty()` carries no load states, so every consumer sits in `Loading` forever —
 * a spinner for a search nobody has run. Spelling the terminal states out is what lets the screen
 * draw its empty state instead, and what stops `asSnapshot` in tests waiting for a page that is
 * never coming.
 */
internal fun emptyLoadedPagingData(): PagingData<FeedPost> =
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
    /** A pager backed by the database, refilled from the network by [FeedRemoteMediator]. */
    fun feed(
        categorySlug: String?,
        sort: FeedSort,
    ): Flow<PagingData<FeedPost>>

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
}

@OptIn(ExperimentalPagingApi::class)
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
) : PostRepository {
    override fun feed(
        categorySlug: String?,
        sort: FeedSort,
    ): Flow<PagingData<FeedPost>> {
        val feedKey = feedKeyFor(categorySlug, sort)
        return pagedFeed(feedKey) { page -> remote.loadList(categorySlug, page, sort) }
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
                database.withTransaction {
                    database.feedDao().clearOtherSearchFeeds(SEARCH_FEED_KEY_PREFIX, feedKey)
                    database.feedDao().clearOtherSearchRemoteKeys(SEARCH_FEED_KEY_PREFIX, feedKey)
                }
            }
            remote.loadSearch(normalized, page, categorySlug, sort)
        }
    }

    /** One pager shape for every list of posts, so a feed and a search can never drift apart. */
    private fun pagedFeed(
        feedKey: String,
        loadPage: suspend (page: Int) -> PostListPage,
    ): Flow<PagingData<FeedPost>> =
        Pager(
            config =
            PagingConfig(
                pageSize = NETWORK_PAGE_SIZE,
                // Matches the old hand-rolled "load when eight rows from the end" heuristic.
                prefetchDistance = 8,
                initialLoadSize = NETWORK_PAGE_SIZE,
                enablePlaceholders = false,
            ),
            remoteMediator =
            FeedRemoteMediator(
                feedKey = feedKey,
                database = database,
                clock = clock,
                loadPage = loadPage,
            ),
            pagingSourceFactory = { database.feedDao().pagingSource(feedKey) },
        ).flow.map { pagingData -> pagingData.map(FeedPostRow::toFeedPost) }

    override fun search(query: String): Flow<List<FeedPost>> {
        val escaped = query.trim().escapeLikePattern()
        if (escaped.isEmpty()) return flowOf(emptyList())
        return database.feedDao().search(escaped).map { rows -> rows.map(FeedPostRow::toFeedPost) }
    }

    override suspend fun invalidateCaches() {
        database.feedDao().expireAllFeeds()
        database.postDetailDao().expireAllThreads()
    }

    override suspend fun clearSessionData() {
        database.withTransaction {
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
        database.withTransaction {
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
        database.withTransaction {
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
        val detail = remote.loadDetail(postId, page)
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
            nowMillis = clock.nowMillis(),
            replacesWindow = replacesWindow,
        )
        database.postDetailDao().trimTo(MAX_CACHED_THREADS)
    }

    override suspend fun isThreadFresh(postId: Long): Boolean {
        val detail = database.postDetailDao().findDetail(postId) ?: return false
        return clock.nowMillis() - detail.cachedAtMillis < THREAD_CACHE_TTL_MILLIS
    }

    override suspend fun cachedPages(postId: Long): IntRange? =
        database.postDetailDao().findDetail(postId)?.let { it.firstLoadedPage..it.lastLoadedPage }

    override suspend fun markThreadRead(postId: Long) {
        // Zero when the post was never in a cached list (a deep link), which is the honest baseline:
        // every reply is then genuinely new to this user.
        val commentCount = database.feedDao().commentCount(postId) ?: 0
        database.readMarkDao().markRead(postId, commentCount, clock.nowMillis())
    }

    override suspend fun react(
        postId: Long,
        commentId: Long,
        action: ReactionAction,
    ) {
        val writer = reactions ?: throw NodeSeekException(NodeSeekError.NotWired)
        val outcome = writer.react(postId = postId, commentId = commentId, action = action)
        database.postDetailDao().updateReactions(postId, commentId) { previous ->
            previous.applying(action, outcome)
        }
    }

    override suspend fun freeChickenLegs(): FreeChickenLegs? = reactions?.freeChickenLegs()

    companion object {
        /**
         * What NodeSeek actually renders per list page, measured at 49 on the front page and rounded
         * up. Paging's window and the network page want to be the same size; when the window is
         * smaller, Paging asks the mediator to append while a page it already fetched is still unread.
         */
        const val NETWORK_PAGE_SIZE = 50

        /**
         * Threads go stale faster than they are re-read, so this is short. It only decides whether
         * *opening* a cached thread also refreshes it — the cached copy shows either way.
         */
        const val THREAD_CACHE_TTL_MILLIS = 2 * 60 * 1000L

        /** Enough to cover a browsing session without letting the cache grow forever. */
        const val MAX_CACHED_THREADS = 60
    }
}

private fun FeedPostRow.toFeedPost(): FeedPost {
    val seen = lastSeenCommentCount
    return FeedPost(
        summary = post.toSummary(),
        isRead = lastReadAtMillis != null,
        // coerceAtLeast because a deleted comment can push the current count below the seen one.
        newCommentCount = if (seen == null) 0 else ((post.commentCount ?: 0) - seen).coerceAtLeast(0),
    )
}

private fun String.escapeLikePattern(): String =
    replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
