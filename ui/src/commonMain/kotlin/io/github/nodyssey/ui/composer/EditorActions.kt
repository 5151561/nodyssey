package io.github.nodyssey.ui.composer

import io.github.plaza.designsys.editor.EditorAction

/**
 * Which keys each surface picks, side by side.
 *
 * Kept together rather than one list per screen file, because the interesting thing about them is the
 * comparison — every entry is an argument about what that surface is for, and those arguments are only
 * checkable next to each other:
 *
 * - [Post] and [Reply] are the same, and are everything: one strip that scrolls, with what gets
 *   *inserted* — a new picture, one already on the image host, a sticker, an @ — ahead of the
 *   formatting keys, the commonest first. (Boards 1d and 2c moved the formatting keys to a 格式
 *   card, "先写，后排版"; on 2026-10-08 the strip came back.)
 * - [Message] is the shortest, and only that: every key is available through its wrench, images
 *   included — `message/send` carries `content` as Markdown and the thread renders images in it.
 *   The image host's key is on it by default (asked for, 2026-10-08); the photo picker is not, and
 *   stays one wrench away.
 *   No preview, because a message renders into a bubble the moment it is sent; getting it wrong
 *   costs a second message, not a deleted topic.
 * - [Signature] omits images and quotes because NodeSeek's own helper text says signatures support
 *   neither. Offering keys the server will strip is the failure the reduced set exists to avoid.
 * - [Readme] is the longest of the fixed sets, because a Readme is a document: it is the one field the space page runs
 *   through the full Markdown renderer, so headings, lists and quotes all land. It stops short of
 *   images and emoji only because the profile form hosts neither a picker nor a panel.
 */
object EditorActions {
    val Post =
        listOf(
            EditorAction.IMAGE,
            EditorAction.HOSTED_IMAGE,
            EditorAction.EMOJI,
            EditorAction.MENTION,
            EditorAction.BOLD,
            EditorAction.LINK,
            EditorAction.QUOTE,
            EditorAction.CODE,
            EditorAction.LIST,
            EditorAction.HEADING,
            EditorAction.STRIKETHROUGH,
            EditorAction.ITALIC,
        )

    val Reply = Post

    val Message =
        listOf(
            EditorAction.BOLD,
            EditorAction.CODE,
            EditorAction.LINK,
            EditorAction.HOSTED_IMAGE,
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
