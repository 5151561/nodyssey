package io.github.nsreader.data

import io.github.nsreader.core.AppDispatchers
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.html.PostDetailParser
import io.github.nsreader.core.html.PostListParser
import io.github.nsreader.core.net.NodeSeekClient
import io.github.nsreader.model.PostDetail
import io.github.nsreader.model.PostListPage
import kotlinx.coroutines.withContext

interface PostRepository {
    suspend fun loadList(categorySlug: String?, page: Int): PostListPage

    suspend fun loadDetail(postId: Long, page: Int): PostDetail
}

class NetworkPostRepository(
    private val client: NodeSeekClient,
    private val dispatchers: AppDispatchers,
) : PostRepository {

    override suspend fun loadList(categorySlug: String?, page: Int): PostListPage {
        val html = client.getHtml(NodeSeekSite.listPath(categorySlug, page))
        // Parsing an 80 KB page is real CPU work and must never land on the main thread.
        return withContext(dispatchers.default) { PostListParser.parse(html, page) }
    }

    override suspend fun loadDetail(postId: Long, page: Int): PostDetail {
        val html = client.getHtml(NodeSeekSite.postPath(postId, page))
        return withContext(dispatchers.default) { PostDetailParser.parse(html, postId, page) }
    }
}
