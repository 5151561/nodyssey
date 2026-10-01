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
}
