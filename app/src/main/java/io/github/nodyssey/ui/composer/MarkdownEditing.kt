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
 * target, so the boards pick six or seven per surface and this enum is the union of those picks.
 * [IMAGE], [EMOJI] and [PREVIEW] open something rather than rewriting text; the screen handles them.
 */
enum class EditorAction { BOLD, HEADING, CODE, QUOTE, LIST, LINK, MENTION, IMAGE, EMOJI, PREVIEW }

/** True when the action rewrites the body itself, which is what [applyMarkdown] can act on. */
val EditorAction.isTextTransform: Boolean
    get() = this !in setOf(EditorAction.IMAGE, EditorAction.EMOJI, EditorAction.PREVIEW)

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
 * The mechanics live in `ui/common`, shared with the signature editor; this file only says which
 * insertion each key stands for.
 */
internal fun TextFieldBuffer.applyMarkdown(action: EditorAction) {
    when (action) {
        EditorAction.BOLD -> applyMarkdown(BOLD)
        EditorAction.CODE -> applyMarkdown(CODE)
        EditorAction.LINK -> applyMarkdown(LINK)
        EditorAction.MENTION -> applyMarkdown(MENTION)
        EditorAction.HEADING -> toggleLinePrefix("## ")
        EditorAction.QUOTE -> toggleLinePrefix("> ")
        EditorAction.LIST -> toggleLinePrefix("- ")
        EditorAction.IMAGE, EditorAction.EMOJI, EditorAction.PREVIEW -> Unit
    }
}

private val BOLD =
    MarkdownInsertion(
        prefix = "**",
        suffix = "**",
        placeholder = "加粗文字",
        caret = MarkdownCaret.SELECT_CONTENT,
    )

private val CODE =
    MarkdownInsertion(
        prefix = "`",
        suffix = "`",
        placeholder = "code",
        caret = MarkdownCaret.SELECT_CONTENT,
    )

/**
 * The caret goes into the empty address, never onto the label.
 *
 * Unlike the signature editor's link, which stops just past `](`: here the scheme is already typed,
 * so landing after it is one fewer thing to do.
 */
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
