package io.github.nodyssey.model

enum class SearchTarget { POSTS, USERS }

enum class SearchSort { RELEVANCE, TIME }

data class SearchHistoryEntry(
    val query: String,
    val target: SearchTarget,
    val categorySlugs: Set<String> = emptySet(),
    val sort: SearchSort = SearchSort.RELEVANCE,
) {
    val key: String
        get() = listOf(target.name, query, categorySlugs.sorted().joinToString(","), sort.name).joinToString("\u001E")
}
