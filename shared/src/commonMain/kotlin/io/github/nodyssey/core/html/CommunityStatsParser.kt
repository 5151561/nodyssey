package io.github.nodyssey.core.html

import com.fleeksoft.ksoup.Ksoup
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException

/** Reads the server-rendered member total from NodeSeek's 用户数目 panel. */
object CommunityStatsParser {
    private val memberCountPattern = Regex("目前论坛共有\\s*([\\d,]+)\\s*位\\s*seeker")

    fun parseMemberCount(html: String): Long {
        val text =
            Ksoup
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
            throw SiteException(SiteError.Unparsable)
        }
        return count
    }
}
