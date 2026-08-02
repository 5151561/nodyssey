package io.github.nodyssey.ui.composer

import androidx.compose.foundation.text.input.TextFieldBuffer
import io.github.nodyssey.ui.common.MARKDOWN_LINK_CARET
import io.github.nodyssey.ui.common.MARKDOWN_LINK_SUFFIX
import io.github.nodyssey.ui.common.MarkdownCaret
import io.github.nodyssey.ui.common.MarkdownInsertion
import io.github.nodyssey.ui.common.applyMarkdown
import io.github.nodyssey.ui.common.toggleLinePrefix

/**
 * Everything the editor toolbar can do.
 *
 * The site's own editor carries fifteen keys (§1.6). A phone toolbar that tried to match it would
 * either scroll — hiding the actions people actually use behind a swipe — or shrink below the 48dp
 * target, so the boards pick four to six per surface and this enum is the union of those picks.
 *
 * Everything here mutates text at the caret. Preview does not — it changes what the whole screen
 * shows — and it is deliberately absent: it lives in each surface's own chrome, alongside 发布 and
 * the close button, rather than competing with 加粗 for a slot.
 *
 * [IMAGE] and [EMOJI] open something rather than rewriting text; the screen handles them.
 */
enum class EditorAction {
    BOLD,
    ITALIC,
    STRIKETHROUGH,
    HEADING,
    CODE,
    QUOTE,
    LIST,
    LINK,
    MENTION,
    IMAGE,
    EMOJI,
}

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
}

/**
 * True when the key opens a panel that stays open, rather than performing a one-shot edit.
 *
 * The toolbar needs this statically, not just from whichever keys are currently lit: an unlit 表情
 * key is still a checkbox in the "off" position, while 加粗 is never a checkbox at all, and giving
 * the latter a toggled role would tell a screen reader something untrue.
 */
internal val EditorAction.opensPanel: Boolean
    get() = this in setOf(EditorAction.IMAGE, EditorAction.EMOJI)

/**
 * Applies one toolbar action to the current selection.
 *
 * Two shapes, both leaving the caret somewhere useful — an editor that formats correctly but drops
 * the caret at the end of the document makes people stop using the toolbar:
 *
 * - **Wrapping** (bold, inline code, links) surrounds the selection, or inserts a placeholder and
 *   selects it so the next keystroke replaces it.
 * - **Line prefixes** (heading, quote, list) toggle at the start of the caret's line, so tapping
 *   the same key twice undoes it instead of stacking `>> `.
 *
 * The mechanics live in `ui/common`; this file only says which insertion each key stands for.
 */
internal fun TextFieldBuffer.applyMarkdown(action: EditorAction) {
    when (action) {
        EditorAction.BOLD -> applyMarkdown(BOLD)
        EditorAction.ITALIC -> applyMarkdown(ITALIC)
        EditorAction.STRIKETHROUGH -> applyMarkdown(STRIKETHROUGH)
        EditorAction.CODE -> applyMarkdown(CODE)
        EditorAction.LINK -> applyMarkdown(LINK)
        EditorAction.MENTION -> applyMarkdown(MENTION)
        EditorAction.HEADING -> toggleLinePrefix("## ")
        EditorAction.QUOTE -> toggleLinePrefix("> ")
        EditorAction.LIST -> toggleLinePrefix("- ")
        EditorAction.IMAGE, EditorAction.EMOJI -> Unit
    }
}

private val BOLD =
    MarkdownInsertion(
        prefix = "**",
        suffix = "**",
        placeholder = "加粗文字",
        caret = MarkdownCaret.SELECT_CONTENT,
    )

private val ITALIC =
    MarkdownInsertion(
        prefix = "*",
        suffix = "*",
        placeholder = "斜体文字",
        caret = MarkdownCaret.SELECT_CONTENT,
    )

private val STRIKETHROUGH =
    MarkdownInsertion(
        prefix = "~~",
        suffix = "~~",
        placeholder = "删除线",
        caret = MarkdownCaret.SELECT_CONTENT,
    )

private val CODE =
    MarkdownInsertion(
        prefix = "`",
        suffix = "`",
        placeholder = "code",
        caret = MarkdownCaret.SELECT_CONTENT,
    )

/** The caret goes into the empty address, never onto the label: the scheme is already typed. */
private val LINK =
    MarkdownInsertion(
        prefix = "[",
        suffix = MARKDOWN_LINK_SUFFIX,
        placeholder = "链接文字",
        caretInSuffix = MARKDOWN_LINK_CARET + URL_SCHEME.length,
        caret = MarkdownCaret.IN_SUFFIX,
    )

/** A bare `@` with no placeholder: the name follows immediately, typed by hand. */
private val MENTION = MarkdownInsertion(prefix = "@", placeholder = "")

private const val URL_SCHEME = "https://"
