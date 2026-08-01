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

/**
 * The strip split in two: the pills that are on, and the pills parked behind them.
 *
 * Two lists rather than one list plus a predicate because that is what the strip draws — the parked
 * boards are always the tail, greyed, and the boundary between the halves is the thing the edit mode
 * moves pills across.
 */
data class HomeBoardArrangement(
    val enabled: List<Board>,
    val parked: List<Board>,
)

/**
 * Applies the user's saved strip arrangement to the boards the site currently has.
 *
 * The stored order is a *ranking*, not a list of boards: it can name slugs the API has since dropped,
 * and the API can return boards it has never heard of. Neither is an error, and neither may lose a
 * board — so ranked-and-still-real boards come first in their saved order, and anything the ranking
 * has no opinion about is appended, enabled, in the order the API gave it. A board added to the site
 * next month therefore shows up rather than silently going missing.
 *
 * [boards] is the real boards only, already narrowed by [visibleHomeBoards]: the site's own 首页版块
 * switches and this arrangement are separate mechanisms, and a board the account switched off is gone
 * from the strip entirely rather than parked in it. 综合 is not a board, has no slug, and is prepended
 * by whoever draws the strip — it is never reordered and never parked.
 */
fun homeBoardArrangement(
    boards: List<Board>,
    order: List<String>,
    parked: Set<String>,
): HomeBoardArrangement {
    if (order.isEmpty() && parked.isEmpty()) return HomeBoardArrangement(boards, emptyList())
    val bySlug = boards.associateBy { it.slug }
    val ranked = order.mapNotNull { bySlug[it] }
    val rankedSlugs = order.toSet()
    val unranked = boards.filterNot { it.slug in rankedSlugs }
    // `partition` keeps relative order within each half, so freshly discovered boards land at the end
    // of the enabled half rather than jumping to the front of it.
    val (off, on) = (ranked + unranked).partition { it.slug in parked }
    return HomeBoardArrangement(enabled = on, parked = off)
}
