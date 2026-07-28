package io.github.nodyssey.ui.common

import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.text.TextRange
import java.text.BreakIterator

/**
 * One Markdown formatting action, described rather than implemented.
 *
 * Every editor in the app offers a different *set* of actions — the post composer has images and
 * quotes, a signature is not allowed either — but they all insert text the same way, and the caret
 * arithmetic is the part that silently rots when it is copied. Editors declare their own actions and
 * share [applyMarkdown].
 */
internal data class MarkdownInsertion(
    val prefix: String,
    val suffix: String = "",
    /** Inserted when nothing is selected, so the result is never a pair of bare delimiters. */
    val placeholder: String,
    /**
     * Where the caret lands inside [suffix].
     *
     * Usually the end of the insertion, which is `suffix.length`. Link is the exception: it points
     * at the URL slot, because with the text already written that is the only thing left to type.
     */
    val caretInSuffix: Int = suffix.length,
    val caret: MarkdownCaret = MarkdownCaret.AFTER_INSERTION,
)

/**
 * Where an insertion leaves the caret.
 *
 * The two editors answer this differently and always have: the post composer selects what it just
 * wrapped, the signature editor places a caret. Unifying the *arithmetic* is the point of this file;
 * unifying the *behaviour* would be a silent UX change, so the difference is named here instead.
 */
internal enum class MarkdownCaret {
    /** Selects the wrapped content, so the next keystroke replaces it. The post composer's choice. */
    SELECT_CONTENT,

    /** Just past the prefix when nothing was selected, at [MarkdownInsertion.caretInSuffix] otherwise. */
    AFTER_INSERTION,

    /** Always at [MarkdownInsertion.caretInSuffix], selection or not. Used by the composer's link. */
    IN_SUFFIX,
}

/**
 * Applies [insertion] to the current selection and leaves the caret where typing continues.
 *
 * Operates on a [TextFieldBuffer] — the buffer handed to `TextFieldState.edit {}` — rather than
 * rebuilding an immutable value. `replace` carries the selection across the edit on its own, so the
 * only offsets computed here are the ones that deliberately move the caret somewhere else.
 */
internal fun TextFieldBuffer.applyMarkdown(insertion: MarkdownInsertion) {
    val start = selection.min
    val end = selection.max
    val selected = asCharSequence().substring(start, end)

    val body = selected.ifEmpty { insertion.placeholder }
    replace(start, end, insertion.prefix + body + insertion.suffix)

    val bodyStart = start + insertion.prefix.length
    val bodyEnd = bodyStart + body.length
    when (insertion.caret) {
        MarkdownCaret.SELECT_CONTENT -> selection = TextRange(bodyStart, bodyEnd)

        MarkdownCaret.IN_SUFFIX -> placeCursorBeforeCharAt(bodyEnd + insertion.caretInSuffix)

        MarkdownCaret.AFTER_INSERTION ->
            if (selected.isEmpty()) {
                placeCursorBeforeCharAt(bodyStart)
            } else {
                placeCursorBeforeCharAt(bodyEnd + insertion.caretInSuffix)
            }
    }
}

/**
 * Toggles a line prefix (`## `, `> `, `- `) at the start of the caret's line.
 *
 * Toggling rather than inserting is what makes tapping the same key twice undo it instead of
 * stacking `>> `.
 */
internal fun TextFieldBuffer.toggleLinePrefix(prefix: String) {
    val caret = selection.min
    val text = asCharSequence()
    val lineStart = text.lastIndexOf('\n', (caret - 1).coerceAtLeast(0))
        .let { if (it < 0 || text.isEmpty()) 0 else it + 1 }
    val hasPrefix = text.startsWith(prefix, lineStart)
    if (hasPrefix) {
        delete(lineStart, lineStart + prefix.length)
    } else {
        insert(lineStart, prefix)
    }
}

/** Drops [text] in at the caret, replacing the selection. Used by the emoji panel. */
internal fun TextFieldBuffer.insertText(text: String) {
    replace(selection.min, selection.max, text)
}

/**
 * Deletes one character before the caret — the emoji panel's own backspace key.
 *
 * Steps back one grapheme cluster, not one code point: the panel's own ❤️ is base + variation
 * selector, and a code-point step would strip the selector and leave a black text-style heart.
 */
internal fun TextFieldBuffer.deleteBackwards() {
    val start = selection.min
    val end = selection.max
    if (start != end) {
        delete(start, end)
        return
    }
    if (start == 0) return
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(asCharSequence().toString())
    val previous = iterator.preceding(start).let { if (it == BreakIterator.DONE) 0 else it }
    delete(previous, start)
}

/**
 * Appends an uploaded image on a line of its own.
 *
 * Not inserted at the caret: uploads finish while typing continues, and dropping `![…](…)` into the
 * middle of the sentence being written is both surprising and hard to undo. A block image also has
 * to start its own line to render as one. Appending through the buffer is what keeps the caret where
 * the user left it — the previous version rewrote the whole body and shoved the caret to the end.
 */
internal fun TextFieldBuffer.appendBlock(block: String) {
    val body = asCharSequence()
    val separator = when {
        body.isBlank() -> ""
        body.endsWith("\n\n") -> ""
        body.endsWith("\n") -> "\n"
        else -> "\n\n"
    }
    replace(length, length, separator + block)
}

/** Removes an attachment's Markdown again when its cell is dismissed. */
internal fun TextFieldBuffer.removeBlock(block: String) {
    if (block.isEmpty()) return
    val body = asCharSequence().toString()
    // Longest separator first, so removing "\n\n![…]" does not leave a stray blank line behind.
    val index = listOf("\n\n$block", "\n$block", block)
        .firstNotNullOfOrNull { candidate ->
            body.indexOf(candidate).takeIf { it >= 0 }?.let { it to candidate.length }
        } ?: return
    delete(index.first, index.first + index.second)
}

/** The URL slot in `](https://)`, i.e. just past the `](`. Shared by the link and image actions. */
internal const val MARKDOWN_LINK_SUFFIX = "](https://)"
internal const val MARKDOWN_LINK_CARET = 2

/**
 * Edits a field from outside the composition — an upload landing, a draft being restored.
 *
 * The extra `sendApplyNotifications` is the whole difference from a plain `edit`. Snapshot writes
 * only reach observers such as the ViewModels' `snapshotFlow` mirrors once notifications are
 * dispatched, which a running frame does for free; a background coroutine gets no such frame, so
 * without this an image could land in the body and the draft would still autosave the text as it was.
 */
internal fun TextFieldState.editFromViewModel(block: TextFieldBuffer.() -> Unit) {
    edit(block)
    Snapshot.sendApplyNotifications()
}
