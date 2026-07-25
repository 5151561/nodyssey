package io.github.nsreader.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import io.github.nsreader.core.AppClock
import io.github.nsreader.data.local.CommentEntity
import io.github.nsreader.data.local.FeedPostRow
import io.github.nsreader.data.local.NodeSeekDatabase
import io.github.nsreader.data.local.toSnapshot
import io.github.nsreader.data.local.toSummary
import io.github.nsreader.model.FeedSort
import io.github.nsreader.model.PostSummary
import io.github.nsreader.model.ThreadSnapshot
import kotlinx.coroutines.flow.Flow
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

    /** The cached thread, emitting null until page 1 has ever been fetched. */
    fun thread(postId: Long): Flow<ThreadSnapshot?>

    /** Fetches one comment page and writes it into the thread. Throws on failure. */
    suspend fun refreshThread(
        postId: Long,
        page: Int,
    )

    /** True when the cached thread is recent enough that opening it need not hit the network. */
    suspend fun isThreadFresh(postId: Long): Boolean

    /**
     * Records that the user has opened this thread.
     *
     * The baseline for "new since" is taken from the stored post rather than passed in, so the badge
     * later compares like with like.
     */
    suspend fun markThreadRead(postId: Long)
}

@OptIn(ExperimentalPagingApi::class)
class OfflineFirstPostRepository(
    private val database: NodeSeekDatabase,
    private val remote: PostRemoteDataSource,
    private val clock: AppClock,
) : PostRepository {
    override fun feed(
        categorySlug: String?,
        sort: FeedSort,
    ): Flow<PagingData<FeedPost>> {
        val feedKey = feedKeyFor(categorySlug, sort)
        return Pager(
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
                categorySlug = categorySlug,
                sort = sort,
                database = database,
                remote = remote,
                clock = clock,
            ),
            pagingSourceFactory = { database.feedDao().pagingSource(feedKey) },
        ).flow.map { pagingData -> pagingData.map(FeedPostRow::toFeedPost) }
    }

    override fun thread(postId: Long): Flow<ThreadSnapshot?> = database.postDetailDao().observeThread(postId).map { it?.toSnapshot() }

    override suspend fun refreshThread(
        postId: Long,
        page: Int,
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
        )
        database.postDetailDao().trimTo(MAX_CACHED_THREADS)
    }

    override suspend fun isThreadFresh(postId: Long): Boolean {
        val detail = database.postDetailDao().findDetail(postId) ?: return false
        return clock.nowMillis() - detail.cachedAtMillis < THREAD_CACHE_TTL_MILLIS
    }

    override suspend fun markThreadRead(postId: Long) {
        // Zero when the post was never in a cached list (a deep link), which is the honest baseline:
        // every reply is then genuinely new to this user.
        val commentCount = database.feedDao().commentCount(postId) ?: 0
        database.readMarkDao().markRead(postId, commentCount, clock.nowMillis())
    }

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
