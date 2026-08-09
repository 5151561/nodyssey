package io.github.nodyssey.ui.composer

import io.github.plaza.designsys.editor.EditorAction

/**
 * Which keys each surface picks, side by side.
 *
 * Kept together rather than one list per screen file, because the interesting thing about them is the
 * comparison — every entry is an argument about what that surface is for, and those arguments are only
 * checkable next to each other:
 *
 * - [Post] leads with a list and a link; a topic is written, not spoken.
 * - [Reply] trades those for a quote and an @, the two things a reply does that a topic does not.
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
            EditorAction.BOLD,
            EditorAction.CODE,
            EditorAction.LIST,
            EditorAction.LINK,
            EditorAction.IMAGE,
            EditorAction.EMOJI,
        )

    val Reply =
        listOf(
            EditorAction.BOLD,
            EditorAction.CODE,
            EditorAction.QUOTE,
            EditorAction.MENTION,
            EditorAction.IMAGE,
            EditorAction.EMOJI,
        )

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
