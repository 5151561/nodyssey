package io.github.nodyssey.data.local

import io.github.nodyssey.model.TitleKeywordKind

/**
 * The reader's [TitleKeywordKind.BLOCK_REGEX] patterns, compiled once per use.
 *
 * Case-insensitive, like the plain keywords beside them. A pattern that no longer compiles — one
 * stored by a build whose regex engine was more lenient — is skipped rather than allowed to throw
 * inside a feed write.
 */
class TitleRegexRules(
    patterns: List<String>,
) {
    private val regexes = patterns.mapNotNull { compileOrNull(it) }

    val isEmpty: Boolean get() = regexes.isEmpty()

    fun matches(title: String): Boolean = regexes.any { it.containsMatchIn(title) }

    companion object {
        fun compileOrNull(pattern: String): Regex? =
            try {
                Regex(pattern, RegexOption.IGNORE_CASE)
            } catch (_: IllegalArgumentException) {
                null
            }
    }
}

/**
 * Brings `title_regex_hits` up to date for posts that were just written, inside the caller's write
 * transaction so the page and its hits become visible together.
 */
suspend fun TitleKeywordDao.matchRegexHits(posts: List<PostTitle>) {
    if (posts.isEmpty()) return
    val rules = TitleRegexRules(list(TitleKeywordKind.BLOCK_REGEX).map { it.keyword })
    if (rules.isEmpty) return
    // A title edited since the last visit can stop matching, so the old answer goes first.
    deleteRegexHits(posts.map { it.postId })
    insertRegexHits(posts.filter { rules.matches(it.title) }.map { TitleRegexHitEntity(it.postId) })
}
