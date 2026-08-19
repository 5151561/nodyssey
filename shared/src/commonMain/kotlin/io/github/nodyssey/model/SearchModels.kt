package io.github.nodyssey.model

enum class SearchTarget { POSTS, USERS }

data class SearchHistoryEntry(
    val query: String,
    val target: SearchTarget,
    /**
     * The single board the search was scoped to, or null for the whole site.
     *
     * Singular because the server is: `/search` accepts exactly one `category` and applies it on
     * its side. The multi-select this replaced could only be honoured by fetching unfiltered pages
     * and discarding rows locally, so a quiet board meant walking page after page — the request
     * storm that kept ending in a Cloudflare challenge.
     */
    val categorySlug: String? = null,
    /**
     * The site's own two orders, the same pair the board lists use.
     *
     * `/search` takes the boards' `sortBy` verbatim — 新评论 is `replyTime`, 新帖子 is `postTime` — and
     * defaults to the latter when the parameter is absent, which is why [FeedSort.POST_TIME] is the
     * default here and not the feed's. There is no relevance order to offer: the label this replaced
     * said 相关度 while sending `sortBy=replyTime`.
     */
    val sort: FeedSort = FeedSort.POST_TIME,
) {
    val key: String
        get() = listOf(target.name, query, categorySlug.orEmpty(), sort.name).joinToString("\u001E")
}
