package io.github.nodyssey.model

enum class SearchTarget { POSTS, USERS }

enum class SearchSort { RELEVANCE, TIME }

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
    val sort: SearchSort = SearchSort.RELEVANCE,
) {
    val key: String
        get() = listOf(target.name, query, categorySlug.orEmpty(), sort.name).joinToString("\u001E")
}
