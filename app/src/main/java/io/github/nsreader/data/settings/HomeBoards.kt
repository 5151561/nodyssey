package io.github.nsreader.data.settings

import io.github.nsreader.data.Board

/**
 * Narrows a board list to the user's home-strip preference.
 *
 * Lives beside the preference it interprets rather than next to the screen that edits it: the home
 * feed applies this on every emission and has nothing else to do with 账号设置, and a UI package
 * importing another UI package is the first edge that has to be cut when features become modules.
 *
 * Callers pass only the real boards — 综合 is not one, has no slug, and is prepended by whoever draws
 * the strip. An empty preference means unrestricted. A preference that no longer matches anything, as
 * happens when every chosen board is renamed server-side, also falls back to unrestricted rather than
 * to nothing: an empty strip is indistinguishable from a broken one.
 */
fun visibleHomeBoards(boards: List<Board>, preference: Set<String>): List<Board> {
    if (preference.isEmpty()) return boards
    return boards.filter { it.slug in preference }.ifEmpty { boards }
}
