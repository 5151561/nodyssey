package io.github.nodyssey.core.html

import com.fleeksoft.ksoup.Ksoup
import io.github.nodyssey.core.NodeSeekSite
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
        val document = Ksoup.parse(html, NodeSeekSite.BASE_URL)
        if (document.getElementById("nsk-frame") == null) {
            throw SiteException(SiteError.LoginRequired)
        }
        // The list parser already reads the pager, "..100"-style elided totals included.
        val parsed = PostListParser.parse(html, page)
        return parsed.copy(hasNextPage = parsed.posts.isNotEmpty() && parsed.hasNextPage)
    }
}
