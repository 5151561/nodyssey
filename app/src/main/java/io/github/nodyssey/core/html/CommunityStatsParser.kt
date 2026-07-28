package io.github.nodyssey.core.html

import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException
import org.jsoup.Jsoup

/** Reads the server-rendered member total from NodeSeek's 用户数目 panel. */
object CommunityStatsParser {
    private val memberCountPattern = Regex("目前论坛共有\\s*([\\d,]+)\\s*位\\s*seeker")

    fun parseMemberCount(html: String): Long {
        val text =
            Jsoup
                .parse(html)
                .selectFirst(Selectors.COMMUNITY_MEMBER_COUNT)
                ?.text()
                .orEmpty()
        val count =
            memberCountPattern
                .find(text)
                ?.groupValues
                ?.get(1)
                ?.replace(",", "")
                ?.toLongOrNull()
        if (count == null || count <= 0L) {
            throw NodeSeekException(NodeSeekError.Unparsable)
        }
        return count
    }
}
