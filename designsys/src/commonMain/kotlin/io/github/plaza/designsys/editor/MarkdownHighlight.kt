package io.github.plaza.designsys.editor

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.github.plaza.core.richtext.MarkdownSpanKind
import io.github.plaza.core.richtext.markdownSpans

/**
 * Draws the Markdown syntax an editor already holds: bold reads bold, a quote recedes, a fence turns
 * monospaced — while the text stays exactly the source that will be posted.
 *
 * An [OutputTransformation] on purpose, and this is the whole design. Compose 1.12 added
 * `TextFieldBuffer.addStyle`, and it can be called from three places: `TextFieldState.edit {}`, an
 * `InputTransformation`, and here. The first two make the styling *part of the state* — tracked
 * ranges that survive later edits, which is what a WYSIWYG editor wants and precisely what this one
 * must not have. The body of a post here is Markdown; the string in the field is what gets sent to
 * the site, and `SpanStyle`s attached to it would be formatting with no representation in what is
 * posted — bold that the site never sees. An output transformation is discarded after each frame:
 * the state keeps the `**`, the reader of the editor sees the weight.
 *
 * Nothing is inserted or removed either, so the transformed text is the untransformed text and offset
 * mapping stays the identity — which is what keeps an IME's composing region where the IME put it.
 * A transformation that reflowed the source would have to be tested against 拼音 input before it
 * could be trusted; this one has nothing to reflow.
 *
 * Where the highlighting disagrees with the preview it is a bug in one of them, which is why every
 * rule about what the syntax *means* lives in [markdownSpans] beside the parser, and this file only
 * says what each kind looks like.
 */
@Composable
fun rememberMarkdownHighlight(): OutputTransformation {
    val palette =
        MarkdownHighlightPalette(
            // The syntax itself is not content: it stays legible, because the author has to be able
            // to fix a `**` they left unclosed, but it gives the line's weight back to the words.
            marker = MaterialTheme.colorScheme.outline,
            quote = MaterialTheme.colorScheme.onSurfaceVariant,
            link = MaterialTheme.colorScheme.primary,
            // The same token the post renderer sets a code span against, so the editor and the
            // rendered post do not disagree about what code looks like.
            codeBackground = MaterialTheme.colorScheme.surfaceContainer,
        )
    // Keyed on the palette because `BasicTextField` remembers its transformation by identity: a new
    // instance per recomposition would re-transform the whole body on every frame.
    return remember(palette) { MarkdownHighlight(palette) }
}

/** What each [MarkdownSpanKind] is drawn in, resolved from the theme once per composition. */
@Immutable
private data class MarkdownHighlightPalette(
    val marker: Color,
    val quote: Color,
    val link: Color,
    val codeBackground: Color,
)

private class MarkdownHighlight(
    private val palette: MarkdownHighlightPalette,
) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        // `toString` rather than the buffer's own `CharSequence`: the scan indexes it repeatedly and
        // reads whole lines out of it, and the buffer's is a gap buffer that charges for both.
        markdownSpans(asCharSequence().toString()).forEach { span ->
            addStyle(palette.styleFor(span.kind), span.start, span.end)
        }
    }
}

/**
 * The look of one kind of syntax.
 *
 * Italic is weight rather than a slant, and bold is the heavier weight — the post renderer's rule,
 * for its reason: the forum's Chinese type has no italic form and a synthesised slant at body size
 * is close to illegible. A heading is weight for the same reason it is weight in a post, and not
 * also a larger size: the line it is on is being edited, and reflowing what is under the cursor as a
 * `#` is typed moves the text out from under the caret.
 */
private fun MarkdownHighlightPalette.styleFor(kind: MarkdownSpanKind): SpanStyle =
    when (kind) {
        MarkdownSpanKind.Marker -> SpanStyle(color = marker)

        MarkdownSpanKind.Heading -> SpanStyle(fontWeight = FontWeight.Bold)

        MarkdownSpanKind.Bold -> SpanStyle(fontWeight = FontWeight.Bold)

        MarkdownSpanKind.Italic -> SpanStyle(fontWeight = FontWeight.Medium)

        MarkdownSpanKind.Strikethrough -> SpanStyle(textDecoration = TextDecoration.LineThrough)

        MarkdownSpanKind.Code -> SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)

        MarkdownSpanKind.Quote -> SpanStyle(color = quote)

        MarkdownSpanKind.LinkText -> SpanStyle(color = link)

        // Not the link colour: the destination is not the part that will be tappable, and colouring
        // it like one makes a line of Markdown read as two links.
        MarkdownSpanKind.LinkUrl -> SpanStyle(color = marker)
    }
