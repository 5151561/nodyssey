package io.github.nsreader.data

import io.github.nsreader.core.AppDispatchers
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.html.PostDetailParser
import io.github.nsreader.core.html.PostListParser
import io.github.nsreader.core.net.NodeSeekClient
import io.github.nsreader.model.FeedSort
import io.github.nsreader.model.PostDetail
import io.github.nsreader.model.PostListPage
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
