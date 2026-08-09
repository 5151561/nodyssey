package io.github.nodyssey.data

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.html.PostDetailParser
import io.github.nodyssey.core.html.PostListParser
import io.github.nodyssey.core.html.SearchParser
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.PostDetail
import io.github.nodyssey.model.PostListPage
import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.MinIntervalGate
import io.github.plaza.core.net.SiteHtmlClient
import kotlinx.coroutines.withContext

/**
 * Fetches and parses pages. Nothing more.
 *
 * This used to *be* the repository, which is why the list lived in memory and vanished on the way
 * back from a post. It is now strictly a writer into Room: [PostRepository] owns the data, this owns
 * the transport.
 */
interface PostRemoteDataSource {
    suspend fun loadList(
        categorySlug: String?,
        page: Int,
        sort: FeedSort,
    ): PostListPage

    /**
     * One page of `/search?q=…`, which is a board listing at a different route — same markup, same
     * parser, same one-request-per-page contract as [loadList].
     */
    suspend fun loadSearch(
        query: String,
        page: Int,
        categorySlug: String?,
        sort: FeedSort,
    ): PostListPage

    suspend fun loadDetail(
        postId: Long,
        page: Int,
    ): PostDetail
}

class NetworkPostDataSource(
    private val client: SiteHtmlClient,
    private val dispatchers: AppDispatchers,
    clock: AppClock = AppClock.System,
) : PostRemoteDataSource {
    /**
     * The site's published throttle, honoured on our side instead of discovered as a 429.
     *
     * Only search is gated. Board and post pages have never answered 429, and spacing them would
     * slow ordinary scrolling for a limit that does not apply to them.
     */
    private val searchGate = MinIntervalGate(SEARCH_MIN_INTERVAL_MILLIS, clock)

    override suspend fun loadList(
        categorySlug: String?,
        page: Int,
        sort: FeedSort,
    ): PostListPage {
        val html = client.getHtml(NodeSeekSite.listPath(categorySlug, page, sort))
        // Parsing an 80 KB page is real CPU work and must never land on the main thread.
        return withContext(dispatchers.default) { PostListParser.parse(html, page) }
    }

    override suspend fun loadSearch(
        query: String,
        page: Int,
        categorySlug: String?,
        sort: FeedSort,
    ): PostListPage {
        val html =
            searchGate.spaced {
                client.getHtml(NodeSeekSite.postSearchPath(query.trim(), page, categorySlug, sort))
            }
        return withContext(dispatchers.default) { SearchParser.parsePosts(html, page) }
    }

    override suspend fun loadDetail(
        postId: Long,
        page: Int,
    ): PostDetail {
        val html = client.getHtml(NodeSeekSite.postPath(postId, page))
        return withContext(dispatchers.default) { PostDetailParser.parse(html, postId, page) }
    }

    companion object {
        /** `/search` answers a second request inside two seconds with 429; see [MinIntervalGate]. */
        const val SEARCH_MIN_INTERVAL_MILLIS = 2_000L
    }
}
