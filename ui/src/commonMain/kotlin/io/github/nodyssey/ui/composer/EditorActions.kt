package io.github.nodyssey.ui.composer

import io.github.plaza.designsys.editor.EditorAction

/**
 * Which keys each surface picks, side by side.
 *
 * Kept together rather than one list per screen file, because the interesting thing about them is the
 * comparison — every entry is an argument about what that surface is for, and those arguments are only
 * checkable next to each other:
 *
 * - [Post] and [Reply] are the same three, and none of them formats: since "先写，后排版" (boards 1d
 *   and 2c) these two editors keep the formatting keys on a 格式 card that opens on demand, so what
 *   is left on their bar is what gets *inserted* — a picture, a sticker, an @. Anyone who wants
 *   加粗 one tap away can still pin it there with the wrench; that is what the arrangement is for
 *   now. (They used to differ — a topic led with a list and a link, a reply with a quote — and that
 *   argument moved to the card, which offers all of them to both.)
 * - [Message] is the shortest, and only that: every key is available through its wrench, images
 *   included — `message/send` carries `content` as Markdown and the thread renders images in it.
 *   No preview, because a message renders into a bubble the moment it is sent; getting it wrong
 *   costs a second message, not a deleted topic.
 * - [Signature] omits images and quotes because NodeSeek's own helper text says signatures support
 *   neither. Offering keys the server will strip is the failure the reduced set exists to avoid.
 * - [Readme] is the longest, because a Readme is a document: it is the one field the space page runs
 *   through the full Markdown renderer, so headings, lists and quotes all land. It stops short of
 *   images and emoji only because the profile form hosts neither a picker nor a panel.
 */
object EditorActions {
    val Post =
        listOf(
            EditorAction.IMAGE,
            EditorAction.EMOJI,
            EditorAction.MENTION,
        )

    val Reply = Post

    val Message =
        listOf(
            EditorAction.BOLD,
            EditorAction.CODE,
            EditorAction.LINK,
            EditorAction.EMOJI,
        )

    val Signature =
        listOf(
            EditorAction.BOLD,
            EditorAction.ITALIC,
            EditorAction.STRIKETHROUGH,
            EditorAction.LINK,
            EditorAction.CODE,
        )

    val Readme =
        listOf(
            EditorAction.BOLD,
            EditorAction.ITALIC,
            EditorAction.STRIKETHROUGH,
            EditorAction.HEADING,
            EditorAction.LIST,
            EditorAction.QUOTE,
            EditorAction.LINK,
            EditorAction.CODE,
        )
}
