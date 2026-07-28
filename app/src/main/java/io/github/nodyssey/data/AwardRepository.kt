package io.github.nodyssey.data

import io.github.nodyssey.core.AppDispatchers
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.html.PostListParser
import io.github.nodyssey.core.net.HtmlSource
import io.github.nodyssey.model.PostListPage
import kotlinx.coroutines.withContext

/**
 * 推荐阅读 — the curated ("加精") threads at `/award`.
 *
 * The one community-tools page that needs no new anything: it is server-rendered with the same markup
 * as the feed, so the feed's parser reads it and the feed's row renders it. Paged by number rather
 * than scrolled, because that is how the site indexes it and it runs to eighteen pages.
 */
interface AwardRepository {
    suspend fun page(page: Int): PostListPage
}

class NetworkAwardRepository(
    private val htmlSource: HtmlSource,
    private val dispatchers: AppDispatchers,
) : AwardRepository {
    override suspend fun page(page: Int): PostListPage {
        val html = htmlSource.getHtml(NodeSeekSite.awardPath(page))
        return withContext(dispatchers.default) { PostListParser.parse(html, page) }
    }
}
