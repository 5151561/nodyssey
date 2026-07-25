package io.github.nsreader.data

import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.html.PostDetailParser
import io.github.nsreader.core.html.PostListParser
import io.github.nsreader.core.net.NodeSeekClient
import io.github.nsreader.model.PostDetail
import io.github.nsreader.model.PostListPage

interface PostRepository {
    suspend fun loadList(categorySlug: String?, page: Int): PostListPage

    suspend fun loadDetail(postId: Long, page: Int): PostDetail
}

class NetworkPostRepository(
    private val client: NodeSeekClient,
) : PostRepository {

    override suspend fun loadList(categorySlug: String?, page: Int): PostListPage {
        val html = client.getHtml(NodeSeekSite.listPath(categorySlug, page))
        return PostListParser.parse(html, page)
    }

    override suspend fun loadDetail(postId: Long, page: Int): PostDetail {
        val html = client.getHtml(NodeSeekSite.postPath(postId, page))
        return PostDetailParser.parse(html, postId, page)
    }
}
