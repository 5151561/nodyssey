package io.github.nsreader.core.html

import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.model.PostContent
import io.github.nsreader.model.PostDetail
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Scrapes a post page (`/post-703863-1`): the opening post plus one page of comments. */
object PostDetailParser {

    fun parse(html: String, postId: Long, page: Int): PostDetail {
        val document = Jsoup.parse(html, NodeSeekSite.BASE_URL)

        val title = document.selectFirst(Selectors.DETAIL_TITLE)?.text()?.trim()
            ?: document.selectFirst(Selectors.DETAIL_TITLE_FALLBACK)?.text()?.trim()
            ?: ""

        val bodyElement = document.selectFirst(Selectors.DETAIL_BODY_ITEM)
        val body = bodyElement?.let { parseContent(it, isBody = true) } ?: emptyContent()
        val comments = document.select(Selectors.DETAIL_COMMENTS).map { parseContent(it, isBody = false) }

        val pager = document.selectFirst(Selectors.DETAIL_PAGER)
        val totalPages = pager?.select(Selectors.DETAIL_PAGER_POSITIONS)
            ?.mapNotNull { it.text().trim().toIntOrNull() }
            ?.maxOrNull()
            ?: 1

        return PostDetail(
            postId = postId,
            title = title,
            body = body,
            comments = comments,
            page = page,
            totalPages = maxOf(totalPages, page),
            hasNextPage = pager?.selectFirst(Selectors.DETAIL_PAGER_NEXT) != null,
        )
    }

    private fun parseContent(element: Element, isBody: Boolean): PostContent {
        val authorLink = element.selectFirst(Selectors.CONTENT_AUTHOR)
        val createdAt = element.selectFirst(Selectors.CONTENT_CREATED_AT)
        val posterBadge = element.selectFirst(Selectors.CONTENT_POSTER_BADGE)

        return PostContent(
            commentId = element.attr("data-comment-id").toLongOrNull(),
            floor = element.selectFirst(Selectors.CONTENT_FLOOR)?.text()?.trim()?.ifBlank { null },
            authorName = authorLink?.text()?.trim().orEmpty(),
            authorUid = NodeSeekSite.parseUid(authorLink?.attr("href")),
            avatarUrl = NodeSeekSite.absoluteUrl(
                element.selectFirst(Selectors.CONTENT_AVATAR)?.attr("src"),
            ),
            // The opening post carries the 楼主 badge; comments only carry it when the OP replies.
            isOriginalPoster = isBody || posterBadge != null,
            badges = element.select(Selectors.CONTENT_BADGES)
                .map { it.text().trim() }
                .filter { it.isNotEmpty() }
                .distinct(),
            createdAtText = createdAt?.text()?.trim()?.ifBlank { null },
            createdAtTitle = createdAt?.attr("title")?.ifBlank { null },
            categoryTitle = element.selectFirst(Selectors.CONTENT_CATEGORY)?.text()?.trim()
                ?.ifBlank { null },
            nodes = RichContentParser.parse(element.selectFirst(Selectors.CONTENT_ARTICLE)),
        )
    }

    private fun emptyContent() = PostContent(
        commentId = null,
        floor = null,
        authorName = "",
        authorUid = null,
        avatarUrl = null,
        isOriginalPoster = true,
        badges = emptyList(),
        createdAtText = null,
        createdAtTitle = null,
        categoryTitle = null,
        nodes = emptyList(),
    )
}
