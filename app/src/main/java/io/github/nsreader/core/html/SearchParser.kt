package io.github.nsreader.core.html

import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.model.PostSummary
import org.jsoup.Jsoup

data class PostSearchPage(
    val posts: List<PostSummary>,
    val totalPages: Int,
)

object SearchParser {
    fun parsePosts(html: String, page: Int): PostSearchPage {
        val document = Jsoup.parse(html, NodeSeekSite.BASE_URL)
        if (document.getElementById("nsk-frame") == null) {
            throw NodeSeekException(NodeSeekError.LoginRequired)
        }
        val parsed = PostListParser.parse(html, page)
        return PostSearchPage(
            posts = parsed.posts,
            totalPages =
            document
                .select(Selectors.LIST_PAGER_POSITIONS)
                .mapNotNull { it.text().trim().toIntOrNull() }
                .maxOrNull()
                ?.coerceAtLeast(page)
                ?: page,
        )
    }
}
