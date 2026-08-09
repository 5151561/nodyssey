package io.github.bbs1.ui.composer

import io.github.plaza.designsys.editor.EditorAction

/**
 * The formatting keys both composers show.
 *
 * Two of the shared toolbar's keys are deliberately absent. [EditorAction.IMAGE] would need an upload
 * endpoint, and the API plugin has none — the forum's own editor takes image links, not files, so an
 * image key here would open a picker with nowhere to send the result. [EditorAction.EMOJI] would need
 * a sticker set, and unlike the other app's forum, bbs1org serves none: its posts use whatever emoji
 * the keyboard has. Add either the day the server grows the endpoint behind it.
 */
val Bbs1EditorActions: List<EditorAction> =
    listOf(
        EditorAction.BOLD,
        EditorAction.ITALIC,
        EditorAction.HEADING,
        EditorAction.QUOTE,
        EditorAction.LIST,
        EditorAction.CODE,
        EditorAction.LINK,
        EditorAction.MENTION,
    )
