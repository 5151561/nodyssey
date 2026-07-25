package io.github.nsreader.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import io.github.nsreader.core.AppClock
import io.github.nsreader.data.local.FeedPositionEntity
import io.github.nsreader.data.local.FeedPostRow
import io.github.nsreader.data.local.FeedRemoteKeyEntity
import io.github.nsreader.data.local.NodeSeekDatabase
import io.github.nsreader.data.local.toEntity
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
    private val categorySlug: String?,
    private val database: NodeSeekDatabase,
    private val remote: PostRemoteDataSource,
    private val clock: AppClock,
) : RemoteMediator<Int, FeedPostRow>() {
    /**
     * Decides whether opening the screen hits the network at all.
     *
     * `SKIP_INITIAL_REFRESH` is the acceptance criterion for phase two made executable: coming back
     * from a post within the cache window shows the stored list and issues no request, so the
     * scroll position survives because nothing invalidated it.
     */
    override suspend fun initialize(): InitializeAction {
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
                    FIRST_PAGE
                }

                // The site only paginates forwards, and refresh always restarts at page 1.
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
            val result = remote.loadList(categorySlug, page)
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
                        )
                    },
                )
                feedDao.upsertRemoteKey(
                    FeedRemoteKeyEntity(
                        feedKey = feedKey,
                        nextPage = if (result.hasNextPage) page + 1 else null,
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

/** The key under which a board's feed order is stored. The mixed front page has no slug. */
internal fun feedKeyFor(categorySlug: String?): String = categorySlug ?: FRONT_PAGE_FEED_KEY

/** Empty string is safe as a key because NodeSeek slugs are never blank. */
internal const val FRONT_PAGE_FEED_KEY = ""
