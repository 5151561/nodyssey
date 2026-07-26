package io.github.nsreader.core.html

import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.model.PostListPage
import io.github.nsreader.model.PostSummary
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Scrapes a topic list page (`/`, `/page-2`, `/categories/tech/page-3`). */
object PostListParser {

    fun parse(html: String, page: Int): PostListPage {
        val document = Jsoup.parse(html, NodeSeekSite.BASE_URL)
        val posts = document.select(Selectors.LIST_ITEM).mapNotNull(::parseItem)
        return PostListPage(
            posts = posts,
            page = page,
            // The pager renders `pager-next` as a disabled <span> on the last page, so
            // requiring an <a href> is what tells us whether more pages exist.
            hasNextPage = document.selectFirst(Selectors.LIST_PAGER_NEXT) != null,
            totalPages =
            document
                .select(Selectors.LIST_PAGER_POSITIONS)
                // The last position renders as "..100" — an ellipsis span glued to the number — so
                // only the digits are read; a position with none (a bare "…") is skipped.
                .mapNotNull { position -> position.text().filter(Char::isDigit).toIntOrNull() }
                .maxOrNull()
                ?.coerceAtLeast(page)
                ?: page,
        )
    }

    private fun parseItem(item: Element): PostSummary? {
        val titleLink = item.selectFirst(Selectors.LIST_TITLE_LINK) ?: return null
        val route = NodeSeekSite.parsePostRoute(titleLink.attr("href")) ?: return null

        val authorLink = item.selectFirst(Selectors.LIST_AUTHOR)
        val categoryLink = item.selectFirst(Selectors.LIST_CATEGORY)
        val lastActive = item.selectFirst(Selectors.LIST_LAST_ACTIVE)

        return PostSummary(
            postId = route.postId,
            title = titleLink.text().trim(),
            authorName = authorLink?.text()?.trim().orEmpty(),
            authorUid = NodeSeekSite.parseUid(authorLink?.attr("href")),
            avatarUrl = NodeSeekSite.absoluteUrl(item.selectFirst(Selectors.LIST_AVATAR)?.attr("src")),
            categoryTitle = categoryLink?.text()?.trim()?.ifBlank { null },
            categorySlug = categoryLink?.attr("href")?.substringAfterLast('/')?.ifBlank { null },
            viewCount = item.selectFirst(Selectors.LIST_VIEWS)?.text()?.toCountOrNull(),
            commentCount = item.selectFirst(Selectors.LIST_COMMENTS)?.text()?.toCountOrNull(),
            lastActiveText = lastActive?.text()?.trim()?.ifBlank { null },
            lastActiveTitle = lastActive?.attr("title")?.ifBlank { null },
            isPinned = item.selectFirst(Selectors.LIST_PINNED) != null,
            isLocked = item.selectFirst(Selectors.LIST_LOCKED) != null,
        )
    }

    /** Counts render as plain integers today but tolerate `1.2k`-style shorthand. */
    private fun String.toCountOrNull(): Int? {
        val cleaned = trim().replace(",", "")
        cleaned.toIntOrNull()?.let { return it }
        val multiplier = when (cleaned.lastOrNull()?.lowercaseChar()) {
            'k' -> 1_000
            'm' -> 1_000_000
            else -> return null
        }
        val value = cleaned.dropLast(1).toDoubleOrNull() ?: return null
        return (value * multiplier).toInt()
    }
}
