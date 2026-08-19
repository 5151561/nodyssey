package io.github.nodyssey.core.html

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.model.PostListPage
import io.github.nodyssey.model.PostSummary

/** Scrapes a topic list page (`/`, `/page-2`, `/categories/tech/page-3`). */
object PostListParser {

    fun parse(html: String, page: Int): PostListPage {
        val document = Ksoup.parse(html, NodeSeekSite.BASE_URL)
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
        val lockIcon = item.selectFirst(Selectors.LIST_LOCKED)

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
            isAwarded = item.selectFirst(Selectors.LIST_AWARDED) != null,
            // The site ships the row and hides it in CSS (`.blocked-post{display:none}`), so the
            // class is the only thing that says "this account blocked the author".
            isBlocked = item.hasClass(Selectors.BLOCKED_POST_CLASS),
            // 公告行的红框「只读」与锁图标语义相同；只有真实锁图标携带等级。
            isLocked = lockIcon != null || item.selectFirst(Selectors.LIST_READ_ONLY) != null,
            lockLevel = lockIcon?.let(::parseLockLevel),
        )
    }

    /**
     * The level required to read a locked post is the bare text node right after the lock icon:
     * `<span …><svg><use href="#lock"></use></svg>1</span>`.
     */
    private fun parseLockLevel(lockIcon: Element): Int? =
        lockIcon.parents().firstOrNull { it.tagName() == "span" }
            ?.ownText()
            ?.filter(Char::isDigit)
            ?.toIntOrNull()

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
