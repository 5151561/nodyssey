package io.github.nodyssey.data

/**
 * A board tab. `slug == null` is the mixed front page.
 *
 * Here rather than beside `CategoryRepository`, which is still in `:app`, because `BoardEntity` is
 * the thing that maps to it and the whole Room layer moved down in step A6. The repository follows
 * in A7, at which point this file is simply already in the right module.
 */
data class Board(
    val slug: String?,
    val title: String,
    val description: String?,
    val adminOnly: Boolean = false,
)
