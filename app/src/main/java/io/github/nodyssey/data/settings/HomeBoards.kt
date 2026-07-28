package io.github.nodyssey.data.settings

import io.github.nodyssey.data.Board

/**
 * The only boards the site's 首页版块 group lets an account switch off: 交易 / 生活 / 贴图
 * (additions.md §1.3). Every other board is always on, which is why the preference is stored as a
 * hidden set and why the settings page draws exactly three switches instead of a checklist.
 *
 * Order matters: it is the order d6 5/5 lists them in.
 */
val OPTIONAL_HOME_BOARD_SLUGS: List<String> = listOf("trade", "life", "photo-share")

/**
 * Narrows a board list to what the home strip shows: everything except the switched-off boards.
 *
 * Lives beside the preference it interprets rather than next to the screen that edits it: the home
 * feed applies this on every emission and has nothing else to do with 账号设置, and a UI package
 * importing another UI package is the first edge that has to be cut when features become modules.
 *
 * Callers pass only the real boards — 综合 is not one, has no slug, and is prepended by whoever draws
 * the strip. [hidden] can only ever thin the list by the three optional boards; a slug that is not in
 * [OPTIONAL_HOME_BOARD_SLUGS] is ignored, so no stored value can make the strip look broken.
 */
fun visibleHomeBoards(boards: List<Board>, hidden: Set<String>): List<Board> {
    if (hidden.isEmpty()) return boards
    val effective = hidden intersect OPTIONAL_HOME_BOARD_SLUGS.toSet()
    if (effective.isEmpty()) return boards
    return boards.filterNot { it.slug in effective }
}
