package io.github.nodyssey.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import io.github.nodyssey.data.local.FeedPositionEntity
import io.github.nodyssey.data.local.FeedPostRow
import io.github.nodyssey.data.local.FeedRemoteKeyEntity
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.local.toEntity
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.PostListPage
import io.github.plaza.core.AppClock
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.CancellationException

/**
 * Fills the database from the network so the list can read only from the database.
 *
 * The mediator never returns data. Its whole contract is "write pages into Room"; Room invalidates
 * the paging source and the UI updates. That is what makes returning to the list free and aeroplane
 * mode readable — both were impossible while the list was held in a ViewModel field.
 *
 * Page keys are stored rather than derived from [PagingState]. NodeSeek's pagination is a rendered
 * `pager-next` link, not an offset we can compute, and the last-loaded page has to survive process
 * death anyway.
 */
@OptIn(ExperimentalPagingApi::class)
class FeedRemoteMediator(
    private val feedKey: String,
    private val database: NodeSeekDatabase,
    private val clock: AppClock,
    /**
     * Which of the site's pages a refresh starts from. [FIRST_PAGE] for every list the reader simply
     * opened; anything else is 首页翻页栏 having been sent somewhere.
     *
     * A jump is a different feed as far as the stored rows are concerned — page 40 does not append
     * onto page 1, it replaces it — so it arrives as a new mediator rather than as a method call, the
     * same way switching board or sort order does.
     */
    private val startPage: Int = FIRST_PAGE,
    /**
     * How one page of this feed is fetched.
     *
     * A lambda rather than a `categorySlug` + `sort` pair because search is the same feed read from
     * a different route: `/search?q=…` serves the same markup, pages the same way and must obey the
     * same "one request per page, then write it to Room" rule. Everything below is about the
     * database and applies unchanged to both.
     */
    private val loadPage: suspend (page: Int) -> PostListPage,
) : RemoteMediator<Int, FeedPostRow>() {
    /**
     * Decides whether opening the screen hits the network at all.
     *
     * `SKIP_INITIAL_REFRESH` is the acceptance criterion for phase two made executable: coming back
     * from a post within the cache window shows the stored list and issues no request, so the
     * scroll position survives because nothing invalidated it.
     *
     * Freshness is the second question, not the first. A feed stored from page 1 is no answer at all
     * to "show me page 40", however recently it was written, so a stored window that starts somewhere
     * other than [startPage] refreshes regardless of the clock — including on the way back, when the
     * reader leaves page 40 and asks for page 1 again.
     */
    override suspend fun initialize(): InitializeAction {
        if (database.feedDao().firstLoadedPage(feedKey) != startPage) {
            return InitializeAction.LAUNCH_INITIAL_REFRESH
        }
        val refreshedAt =
            database.feedDao().remoteKey(feedKey)?.refreshedAtMillis
                ?: return InitializeAction.LAUNCH_INITIAL_REFRESH
        return if (clock.nowMillis() - refreshedAt < CACHE_TTL_MILLIS) {
            InitializeAction.SKIP_INITIAL_REFRESH
        } else {
            InitializeAction.LAUNCH_INITIAL_REFRESH
        }
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, FeedPostRow>,
    ): MediatorResult {
        val previousKey = database.feedDao().remoteKey(feedKey)

        val page =
            when (loadType) {
                LoadType.REFRESH -> {
                    startPage
                }

                // The site only paginates forwards, and a refresh restarts the whole window. Reaching
                // the page before a jumped-to one is 翻页栏's job, not scrolling upwards.
                LoadType.PREPEND -> {
                    return MediatorResult.Success(endOfPaginationReached = true)
                }

                LoadType.APPEND -> {
                    previousKey?.nextPage
                        // A null next page means the site showed no `pager-next`: genuinely the end. No key
                        // at all means refresh has not run yet, so this is not the end — Paging will retry.
                        ?: return MediatorResult.Success(endOfPaginationReached = previousKey != null)
                }
            }

        return try {
            val result = loadPage(page)
            val now = clock.nowMillis()

            database.withTransaction {
                val feedDao = database.feedDao()
                if (loadType == LoadType.REFRESH) feedDao.clearFeed(feedKey)

                val baseSortIndex = feedDao.nextSortIndex(feedKey)
                feedDao.upsertPosts(result.posts.map { it.toEntity(now) })
                feedDao.insertPositions(
                    result.posts.mapIndexed { offset, post ->
                        FeedPositionEntity(
                            feedKey = feedKey,
                            postId = post.postId,
                            sortIndex = baseSortIndex + offset,
                            page = page,
                        )
                    },
                )
                feedDao.upsertRemoteKey(
                    FeedRemoteKeyEntity(
                        feedKey = feedKey,
                        nextPage = if (result.hasNextPage) page + 1 else null,
                        // The pager's own highest number, which only 首页翻页栏 reads. A page that
                        // renders no pager reports itself as the total, so a one-page feed stays 1.
                        totalPages = maxOf(result.totalPages, page),
                        // Only a refresh resets the clock. If appending bumped it, scrolling a long
                        // list would keep pushing the staleness window out and the feed would never
                        // refresh again.
                        refreshedAtMillis =
                        if (loadType == LoadType.REFRESH) {
                            now
                        } else {
                            previousKey?.refreshedAtMillis ?: now
                        },
                    ),
                )
                // Rows the cleared feed left behind are dropped here, inside the same transaction,
                // so the table cannot grow without bound across refreshes.
                if (loadType == LoadType.REFRESH) feedDao.deleteOrphanedPosts()
            }

            MediatorResult.Success(endOfPaginationReached = !result.hasNextPage)
        } catch (e: CancellationException) {
            // Paging cancels the mediator on refresh and on scope death. Reporting that as an error
            // would render a failure the user never caused — the same bug the ViewModels already
            // guard against with `runCatchingExceptCancellation`.
            throw e
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }

    companion object {
        const val FIRST_PAGE = 1

        /**
         * How long a stored feed counts as fresh.
         *
         * Short enough that a returning user is not reading yesterday's front page, long enough that
         * flicking between a post and the list does not re-request anything.
         */
        const val CACHE_TTL_MILLIS = 5 * 60 * 1000L
    }
}

/**
 * The key under which a board's feed order is stored. The mixed front page has no slug.
 *
 * Sort order is part of the key because the two orders are genuinely different lists — sharing a key
 * would make switching sort append one ordering onto the other. The default order contributes no
 * suffix, so keys written before sorting existed still resolve.
 */
internal fun feedKeyFor(
    categorySlug: String?,
    sort: FeedSort = FeedSort.LAST_REPLY,
): String {
    val base = categorySlug ?: FRONT_PAGE_FEED_KEY
    return when (sort) {
        FeedSort.LAST_REPLY -> base
        FeedSort.POST_TIME -> "$base|postTime"
    }
}

/** Empty string is safe as a key because NodeSeek slugs are never blank. */
internal const val FRONT_PAGE_FEED_KEY = ""

/**
 * The key under which one search's results are stored.
 *
 * Prefixed so [SEARCH_FEED_KEY_PREFIX] can sweep them: a board feed is one of fifteen and worth
 * keeping, while a search feed is one per query typed and would otherwise accumulate forever.
 * Everything the query means is in the key — text, board, order — so changing any of them is a
 * different feed rather than an append onto the previous answer.
 */
internal fun searchFeedKeyFor(
    query: String,
    categorySlug: String?,
    sort: FeedSort,
): String =
    SEARCH_FEED_KEY_PREFIX +
        listOf(
            if (sort == FeedSort.POST_TIME) "postTime" else "",
            categorySlug.orEmpty(),
            query.trim(),
        ).joinToString("|")

/**
 * `:` cannot appear in a board slug, so no board feed key can begin with this.
 *
 * Not `"search|"`: board keys are `slug` or `slug|postTime`, so a board actually called `search`
 * would produce `search|postTime` and get swept along with the searches.
 */
internal const val SEARCH_FEED_KEY_PREFIX = "search:"
