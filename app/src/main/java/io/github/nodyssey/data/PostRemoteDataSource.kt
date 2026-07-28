package io.github.nodyssey.data

import io.github.nodyssey.core.AppDispatchers
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.html.PostDetailParser
import io.github.nodyssey.core.html.PostListParser
import io.github.nodyssey.core.net.NodeSeekClient
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.PostDetail
import io.github.nodyssey.model.PostListPage
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

    suspend fun loadDetail(
        postId: Long,
        page: Int,
    ): PostDetail
}

class NetworkPostDataSource(
    private val client: NodeSeekClient,
    private val dispatchers: AppDispatchers,
) : PostRemoteDataSource {
    override suspend fun loadList(
        categorySlug: String?,
        page: Int,
        sort: FeedSort,
    ): PostListPage {
        val html = client.getHtml(NodeSeekSite.listPath(categorySlug, page, sort))
        // Parsing an 80 KB page is real CPU work and must never land on the main thread.
        return withContext(dispatchers.default) { PostListParser.parse(html, page) }
    }

    override suspend fun loadDetail(
        postId: Long,
        page: Int,
    ): PostDetail {
        val html = client.getHtml(NodeSeekSite.postPath(postId, page))
        return withContext(dispatchers.default) { PostDetailParser.parse(html, postId, page) }
    }
}
