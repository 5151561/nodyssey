package io.github.nsreader.ui.composer

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

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
 * Two shapes, both operating on [TextFieldValue] rather than a plain string so the caret lands
 * somewhere useful afterwards — an editor that formats correctly but drops the caret at the end of
 * the document makes people stop using the toolbar:
 *
 * - **Wrapping** (bold, inline code, links) surrounds the selection, or inserts a placeholder and
 *   selects it so the next keystroke replaces it.
 * - **Line prefixes** (heading, quote, list) toggle at the start of the caret's line, so tapping
 *   the same key twice undoes it instead of stacking `>> `.
 */
fun applyMarkdown(
    value: TextFieldValue,
    action: EditorAction,
): TextFieldValue = when (action) {
    EditorAction.BOLD -> value.wrap("**", "**", "加粗文字")
    EditorAction.CODE -> value.wrap("`", "`", "code")
    EditorAction.LINK -> value.link()
    EditorAction.MENTION -> value.insert("@")
    EditorAction.HEADING -> value.togglePrefix("## ")
    EditorAction.QUOTE -> value.togglePrefix("> ")
    EditorAction.LIST -> value.togglePrefix("- ")
    EditorAction.IMAGE, EditorAction.EMOJI, EditorAction.PREVIEW -> value
}

/** Drops [text] in at the caret, replacing the selection. Used by the emoji panel. */
fun insertText(
    value: TextFieldValue,
    text: String,
): TextFieldValue = value.insert(text)

/**
 * Appends an uploaded image on a line of its own.
 *
 * Not inserted at the caret: uploads finish while typing continues, and dropping `![…](…)` into the
 * middle of the sentence being written is both surprising and hard to undo. A block image also has
 * to start its own line to render as one.
 */
fun appendBlock(
    body: String,
    block: String,
): String = when {
    body.isBlank() -> block
    body.endsWith("\n\n") -> body + block
    body.endsWith("\n") -> body + "\n" + block
    else -> body + "\n\n" + block
}

/** Removes an attachment's Markdown again when its cell is dismissed. */
fun removeBlock(
    body: String,
    block: String,
): String = body
    .replace("\n\n$block", "")
    .replace("\n$block", "")
    .replace(block, "")

private fun TextFieldValue.wrap(
    prefix: String,
    suffix: String,
    placeholder: String,
): TextFieldValue {
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(0, text.length)
    val selected = text.substring(start, end)
    val content = selected.ifEmpty { placeholder }
    val replaced = text.replaceRange(start, end, prefix + content + suffix)
    val contentStart = start + prefix.length
    return TextFieldValue(replaced, TextRange(contentStart, contentStart + content.length))
}

private fun TextFieldValue.link(): TextFieldValue {
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(0, text.length)
    val label = text.substring(start, end).ifEmpty { "链接文字" }
    val replaced = text.replaceRange(start, end, "[$label](https://)")
    // Caret inside the empty URL: the label is the easy part, the address is what needs typing.
    val cursor = start + label.length + 3 + URL_SCHEME.length
    return TextFieldValue(replaced, TextRange(cursor.coerceIn(0, replaced.length)))
}

private fun TextFieldValue.insert(insertion: String): TextFieldValue {
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(0, text.length)
    val replaced = text.replaceRange(start, end, insertion)
    val cursor = (start + insertion.length).coerceIn(0, replaced.length)
    return TextFieldValue(replaced, TextRange(cursor))
}

private fun TextFieldValue.togglePrefix(prefix: String): TextFieldValue {
    val caret = selection.min.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', (caret - 1).coerceAtLeast(0))
        .let { if (it < 0 || text.isEmpty()) 0 else it + 1 }
    val hasPrefix = text.startsWith(prefix, lineStart)
    val replaced = if (hasPrefix) {
        text.removeRange(lineStart, lineStart + prefix.length)
    } else {
        text.replaceRange(lineStart, lineStart, prefix)
    }
    val shift = if (hasPrefix) -prefix.length else prefix.length
    val cursor = (caret + shift).coerceIn(lineStart.coerceAtMost(replaced.length), replaced.length)
    return TextFieldValue(replaced, TextRange(cursor))
}

private const val URL_SCHEME = "https://"
