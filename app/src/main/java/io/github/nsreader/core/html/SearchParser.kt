package io.github.nsreader.core.html

import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.model.PostSummary
import org.jsoup.Jsoup

data class PostSearchPage(
    val posts: List<PostSummary>,
    val totalPages: Int,
    val hasNextPage: Boolean,
)

object SearchParser {
    fun parsePosts(html: String, page: Int): PostSearchPage {
        val document = Jsoup.parse(html, NodeSeekSite.BASE_URL)
        if (document.getElementById("nsk-frame") == null) {
            throw NodeSeekException(NodeSeekError.LoginRequired)
        }
        // The list parser already reads the pager, "..100"-style elided totals included.
        val parsed = PostListParser.parse(html, page)
        return PostSearchPage(
            posts = parsed.posts,
            totalPages = parsed.totalPages,
            hasNextPage = parsed.hasNextPage,
        )
    }
}
