package io.github.nodyssey.ui.common

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

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
     * Where the caret lands inside [suffix] once text was selected.
     *
     * Usually the end of the insertion, which is `suffix.length`. Link and image are the exceptions:
     * they point at the URL slot, because with the text already written that is the only thing left
     * to type.
     */
    val caretInSuffix: Int = suffix.length,
)

/**
 * Applies [insertion] to the current selection and places the caret where typing continues.
 *
 * With nothing selected the caret goes just after the prefix, so the placeholder is sitting right
 * there to be typed over. With a selection it goes to [MarkdownInsertion.caretInSuffix].
 */
internal fun TextFieldValue.applyMarkdown(insertion: MarkdownInsertion): TextFieldValue {
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(0, text.length)
    val selected = text.substring(start, end)

    val body = selected.ifEmpty { insertion.placeholder }
    val replacement = insertion.prefix + body + insertion.suffix
    val caretOffset =
        if (selected.isEmpty()) {
            insertion.prefix.length
        } else {
            insertion.prefix.length + selected.length + insertion.caretInSuffix
        }

    val updated = text.replaceRange(start, end, replacement)
    return TextFieldValue(updated, TextRange((start + caretOffset).coerceIn(0, updated.length)))
}

/** The URL slot in `](https://)`, i.e. just past the `](`. Shared by the link and image actions. */
internal const val MARKDOWN_LINK_SUFFIX = "](https://)"
internal const val MARKDOWN_LINK_CARET = 2
