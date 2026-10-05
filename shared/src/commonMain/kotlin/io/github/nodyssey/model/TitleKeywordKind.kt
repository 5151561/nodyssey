package io.github.nodyssey.model

/**
 * What a title keyword is for. Stored by name in `title_keywords`, so renaming a constant is a
 * migration.
 */
enum class TitleKeywordKind {
    /** Hide matching threads from the feeds. */
    BLOCK,

    /** Notify when a new thread matches. */
    ALERT,

    /**
     * Hide threads whose title a regular expression finds a match in. Kept as typed rather than
     * lower-cased — `\D` is not `\d` — and matched case-insensitively in Kotlin, because SQLite has
     * no `REGEXP` to run it with; see `TitleRegexHitEntity`.
     */
    BLOCK_REGEX,
}
