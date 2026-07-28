package io.github.nsreader.data

import io.github.nsreader.core.AppDispatchers
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.html.CommunityStatsParser
import io.github.nsreader.core.net.HtmlSource
import kotlinx.coroutines.withContext

fun interface CommunityRepository {
    suspend fun memberCount(): Long
}

/** Fetches the public 首页 and reads the member total rendered by NodeSeek itself. */
class NetworkCommunityRepository(
    private val htmlSource: HtmlSource,
    private val dispatchers: AppDispatchers,
) : CommunityRepository {
    override suspend fun memberCount(): Long {
        val html = htmlSource.getHtml(NodeSeekSite.listPath(categorySlug = null, page = 1))
        return withContext(dispatchers.default) {
            CommunityStatsParser.parseMemberCount(html)
        }
    }
}
