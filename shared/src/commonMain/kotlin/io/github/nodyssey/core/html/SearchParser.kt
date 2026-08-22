package io.github.nodyssey.core.html

import io.github.nodyssey.model.PostListPage
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException

object SearchParser {
    /**
     * Reads `/search?q=…` with the board list's parser, then corrects the one thing it cannot know.
     *
     * The search pager lies past the end of the results: `/search?q=android&page=99` returns zero
     * rows and *still* renders `pager-next` as an `<a>` pointing at page 2 (verified live
     * 2026-08-01). Trusting it is what turned "scrolled to the bottom" into an endless walk —
     * every empty page claimed another page followed, so Paging asked for one more, forever, at a
     * rate the site's two-second throttle answers with 429 and Cloudflare eventually answers with a
     * challenge.
     *
     * So an empty page ends the search, whatever the pager says. On a genuine last page the site
     * renders `pager-next` as a disabled `<span>` and the ordinary rule already applies.
     */
    fun parsePosts(html: String, page: Int): PostListPage {
        val document = SiteHtml.parse(html)
        if (document.getElementById("nsk-frame") == null) {
            throw SiteException(SiteError.LoginRequired)
        }
        // Before the empty-list reading below, because this page *is* an empty list as far as the
        // markup goes: the site answers a one-character query with the ordinary frame, no rows, and
        // the sentence in [Selectors.SEARCH_TOO_SHORT_MARKERS]. Without this it reads as
        // "没搜到相关帖子" — an answer to a search that never ran.
        if (Selectors.SEARCH_TOO_SHORT_MARKERS.any { html.contains(it) }) {
            throw SiteException(SiteError.QueryTooShort)
        }
        // The list parser already reads the pager, "..100"-style elided totals included.
        val parsed = PostListParser.parse(html, page)
        return parsed.copy(hasNextPage = parsed.posts.isNotEmpty() && parsed.hasNextPage)
    }
}
