package io.github.nodyssey.core.html

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.model.PostContent
import io.github.nodyssey.model.PostDetail
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Scrapes a post page (`/post-703863-1`): the opening post plus one page of comments. */
object PostDetailParser {

    fun parse(html: String, postId: Long, page: Int): PostDetail {
        val document = Jsoup.parse(html, NodeSeekSite.BASE_URL)

        val title = document.selectFirst(Selectors.DETAIL_TITLE)?.text()?.trim()
            ?: document.selectFirst(Selectors.DETAIL_TITLE_FALLBACK)?.text()?.trim()
            ?: ""

        // The counts, this account's own marks and the block flags come from the page's `__config__`
        // blob, keyed by the same `data-comment-id` the markup carries. Read once for the page, not
        // once per floor.
        val config = PostConfigParser.parse(document)

        // Null rather than a blank placeholder: page 2 onwards has no opening post, and the cache
        // must not mistake "not on this page" for "the author wrote nothing".
        val body = document.selectFirst(Selectors.DETAIL_BODY_ITEM)
            ?.let { parseContent(it, isBody = true, config = config) }
        val comments = document.select(Selectors.DETAIL_COMMENTS)
            .map { parseContent(it, isBody = false, config = config) }

        val pager = document.selectFirst(Selectors.DETAIL_PAGER)
        // The href, not the text: the last-page shortcut on a long thread renders as "..37", which
        // `toIntOrNull` rejected — the total then crept up page by page as the user scrolled.
        val totalPages = pager?.select(Selectors.DETAIL_PAGER_POSITIONS)
            ?.mapNotNull { pos ->
                NodeSeekSite.parsePostRoute(pos.attr("href"))?.page
                    ?: pos.text().filter(Char::isDigit).toIntOrNull()
            }
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
            collected = config.collected,
            collectionCount = config.collectionCount,
        )
    }

    /**
     * Matches the `edited Xmin ago` marker next to the floor time. Anchored to the start so that a
     * comment merely containing the word cannot trip it — the search is already scoped to the
     * header strip, this narrows it to the marker itself.
     */
    private val EDITED_MARKER = Regex("""^(edited\b|已编辑)""", RegexOption.IGNORE_CASE)

    private fun parseContent(
        element: Element,
        isBody: Boolean,
        config: PostConfig,
    ): PostContent {
        val commentId = element.attr("data-comment-id").toLongOrNull()
        val authorLink = element.selectFirst(Selectors.CONTENT_AUTHOR)
        val createdAt = element.selectFirst(Selectors.CONTENT_CREATED_AT)
        val posterBadge = element.selectFirst(Selectors.CONTENT_POSTER_BADGE)
        val editedText = element.selectFirst(Selectors.CONTENT_INFO)?.let(::findEditedMarker)

        return PostContent(
            commentId = commentId,
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
            isEdited = editedText != null,
            editedAtText = editedText,
            signatureNodes = RichContentParser.parse(element.selectFirst(Selectors.CONTENT_SIGNATURE)),
            reactions = commentId?.let(config.reactions::get),
            // Either source will do, and they answer for different pages: the blob covers a floor the
            // markup renders unmarked, the class covers a page whose blob we could not read.
            isBlocked =
            element.hasClass(Selectors.BLOCKED_COMMENT_CLASS) ||
                commentId in config.blockedCommentIds,
        )
    }

    /**
     * By marker text rather than by class: the marker's own class has not been captured from a
     * device yet, so this checks every element in the header strip — plus the strip's bare text,
     * in case the marker is not wrapped at all — and takes whatever starts like the marker.
     */
    private fun findEditedMarker(contentInfo: Element): String? =
        (contentInfo.select("*").map { it.ownText() } + contentInfo.ownText())
            .map { it.trim() }
            .firstOrNull { EDITED_MARKER.containsMatchIn(it) }
}
