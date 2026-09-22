package io.github.plaza.core.richtext

/**
 * What one run of Markdown *source* is, to something that draws it while it is being typed.
 *
 * [parseMarkdown] answers a different question — what the post will look like once the site has
 * rendered it — and the offsets that would tie a node back to the characters it came from are gone
 * by the time it has an answer. An editor cannot use that: it must leave the source exactly as the
 * author typed it, because the source is what gets posted, and style it in place.
 */
enum class MarkdownSpanKind {
    /** The syntax itself: the `#`, the `**`, a fence, a bullet, the brackets around a link. */
    Marker,

    Heading,

    Bold,

    Italic,

    Strikethrough,

    /** A code span, or everything between a pair of fences. */
    Code,

    /** What a `>` line quotes, the marker aside. */
    Quote,

    /** A link's `[label]` — the part the reader will end up seeing. */
    LinkText,

    /** Its `(destination)`, which they will not. */
    LinkUrl,
}

/** [kind], over `[start, end)` of the source it was scanned out of. */
data class MarkdownSpan(
    val kind: MarkdownSpanKind,
    val start: Int,
    val end: Int,
)

/**
 * Every styled run in [markdown], in the same dialect [parseMarkdown] reads.
 *
 * A second walk over the source rather than a by-product of the parser, for the reason above — but
 * not a second set of *rules*. Everything that is easy to get subtly wrong is shared: [emphasisAt]
 * decides what opens a bold run here too, so `2 * 3 * 4` stays arithmetic in the editor and in the
 * preview or in neither, and [linkSpan] decides what is a link, so `[x]()` is text in both. What is
 * written twice is only the shape of the walk.
 *
 * Spans may overlap, and are emitted outermost first: that is the order a renderer has to apply them
 * in for the `code` inside a quoted line to win over the quote's colour.
 */
fun markdownSpans(markdown: String): List<MarkdownSpan> {
    // A guard, not a tuned number. This runs once per keystroke, and a body longer than this is one
    // that was pasted in rather than written — colouring it is worth less than the typing it costs.
    if (markdown.length > MAX_SPANNED_LENGTH) return emptyList()

    val spans = mutableListOf<MarkdownSpan>()
    val lines = sourceLines(markdown)
    var index = 0
    while (index < lines.size) {
        index =
            if (lines[index].text.startsWith("```")) {
                fencedBlock(markdown, lines, index, spans)
            } else {
                lineSpans(lines[index].text, lines[index].start, spans)
                index + 1
            }
    }
    return spans
}

/**
 * Past which a body is not highlighted at all.
 *
 * All or nothing rather than a prefix: half a coloured document reads as a bug, and the line the
 * cursor is on is the one place the highlighting would be missing from.
 */
private const val MAX_SPANNED_LENGTH = 20_000

/** One line of the source, and where it starts in it. */
private class SourceLine(
    val start: Int,
    val text: String,
) {
    val end: Int get() = start + text.length
}

/**
 * [markdown] cut at its newlines, each piece knowing its own offset.
 *
 * Not `lines()`, which also splits on a lone `\r` and hands back strings with no way to find them
 * again. Every span in the result points into the string the editor holds, so an offset that is off
 * by one is a style on the wrong character.
 */
private fun sourceLines(markdown: String): List<SourceLine> =
    buildList {
        var start = 0
        while (true) {
            val newline = markdown.indexOf('\n', start)
            if (newline < 0) {
                add(SourceLine(start, markdown.substring(start)))
                return@buildList
            }
            add(SourceLine(start, markdown.substring(start, newline)))
            start = newline + 1
        }
    }

/**
 * The fenced block opening at [open], and the index of the first line after it.
 *
 * The one construct that does not fit on a line, which is why it is here and not in [lineSpans]:
 * between the fences everything is code whatever else it looks like, and the `# ` at the start of a
 * shell transcript is a prompt, not a heading. An unclosed fence runs to the end of the source —
 * both what the parser does with one and what the author is looking at while still typing it.
 */
private fun fencedBlock(
    markdown: String,
    lines: List<SourceLine>,
    open: Int,
    spans: MutableList<MarkdownSpan>,
): Int {
    val fence = lines[open]
    spans.mark(MarkdownSpanKind.Marker, fence.start, fence.end)

    var close = open + 1
    while (close < lines.size && !lines[close].text.startsWith("```")) close++
    val closing = lines.getOrNull(close)

    // From the line after the opening fence to the newline before the closing one, so neither fence
    // is inside the code it delimits.
    val codeStart = lines.getOrNull(open + 1)?.start ?: fence.end
    val codeEnd = closing?.start?.minus(1) ?: markdown.length
    spans.mark(MarkdownSpanKind.Code, codeStart, codeEnd)
    if (closing != null) spans.mark(MarkdownSpanKind.Marker, closing.start, closing.end)

    return close + 1
}

/**
 * One line's block syntax, and then the inline syntax in whatever that leaves.
 *
 * [offset] is where [line] starts in the source, because the spans have to point back into it.
 * Recurses through the block prefixes for the same reason the parser re-parses what they hold:
 * `> - **x**` is a quote around a list around bold text, and each prefix only knows how to strip
 * itself.
 */
private fun lineSpans(
    line: String,
    offset: Int,
    spans: MutableList<MarkdownSpan>,
) {
    val heading = HEADING.matchEntire(line)
    val list = UNORDERED_LIST.matchEntire(line) ?: ORDERED_LIST.matchEntire(line)
    when {
        heading != null -> {
            val content = heading.groupValues[2]
            val contentStart = offset + line.length - content.length
            spans.mark(MarkdownSpanKind.Marker, offset, contentStart)
            spans.mark(MarkdownSpanKind.Heading, contentStart, offset + line.length)
            inlineSpans(content, contentStart, spans)
        }

        DIVIDER.matches(line.trim()) -> spans.mark(MarkdownSpanKind.Marker, offset, offset + line.length)

        line.startsWith(">") -> {
            // One `>` and the space after it per level, which is what the parser strips per pass.
            var cut = 0
            while (cut < line.length && line[cut] == '>') {
                cut++
                if (line.getOrNull(cut) == ' ') cut++
            }
            spans.mark(MarkdownSpanKind.Marker, offset, offset + cut)
            spans.mark(MarkdownSpanKind.Quote, offset + cut, offset + line.length)
            lineSpans(line.substring(cut), offset + cut, spans)
        }

        list != null -> {
            val content = list.groupValues[1]
            val contentStart = offset + line.length - content.length
            spans.mark(MarkdownSpanKind.Marker, offset, contentStart)
            lineSpans(content, contentStart, spans)
        }

        else -> inlineSpans(line, offset, spans)
    }
}

/**
 * The inline syntax in [line], which starts at [offset] in the source.
 *
 * The same order of tests as the parser's own inline pass, and on a line at a time for the same
 * reason: it is handed one line, so nothing it recognises can reach across a newline. Scanning the
 * whole source at once would let a stray `*` find its partner three paragraphs down and italicise
 * everything in between — which the preview beside it would not do.
 */
private fun inlineSpans(
    line: String,
    offset: Int,
    spans: MutableList<MarkdownSpan>,
) {
    var index = 0
    while (index < line.length) {
        val char = line[index]
        val next = line.getOrNull(index + 1)
        index =
            when {
                // A backslash escapes punctuation only, so `C:\path` keeps its backslash.
                char == '\\' && next != null && !next.isLetterOrDigit() && !next.isWhitespace() -> {
                    spans.mark(MarkdownSpanKind.Marker, offset + index, offset + index + 1)
                    index + 2
                }

                char == '`' -> codeSpan(line, index, offset, spans)

                char == '!' && next == '[' -> linkSpans(line, index, offset, spans, image = true)

                char == '[' -> linkSpans(line, index, offset, spans, image = false)

                else -> emphasisSpans(line, index, offset, spans)
            }
    }
}

/** A `` `code` `` span at [at], or [at] + 1 when the backtick never closes. */
private fun codeSpan(
    line: String,
    at: Int,
    offset: Int,
    spans: MutableList<MarkdownSpan>,
): Int {
    val fence = line.runLengthAt(at)
    val close = line.indexOf("`".repeat(fence), at + fence)
    if (close < 0) return at + 1
    spans.mark(MarkdownSpanKind.Marker, offset + at, offset + at + fence)
    spans.mark(MarkdownSpanKind.Code, offset + at + fence, offset + close)
    spans.mark(MarkdownSpanKind.Marker, offset + close, offset + close + fence)
    return close + fence
}

/**
 * A `[label](url)`, or the `![alt](url)` before it when [image] — or [at] + 1 when it is neither.
 *
 * [linkSpan] makes that call, rather than a second copy of the three conditions it checks: a
 * destination that turns out to be empty is not a link, and an editor that coloured one anyway would
 * be promising a preview that never comes.
 */
private fun linkSpans(
    line: String,
    at: Int,
    offset: Int,
    spans: MutableList<MarkdownSpan>,
    image: Boolean,
): Int {
    val bracket = if (image) at + 1 else at
    val link = linkSpan(line, bracket) ?: return at + 1
    val labelStart = bracket + 1
    val labelEnd = labelStart + link.label.length
    val specEnd = link.end - 1

    spans.mark(MarkdownSpanKind.Marker, offset + at, offset + labelStart)
    spans.mark(MarkdownSpanKind.LinkText, offset + labelStart, offset + labelEnd)
    spans.mark(MarkdownSpanKind.Marker, offset + labelEnd, offset + labelEnd + 2)
    spans.mark(MarkdownSpanKind.LinkUrl, offset + labelEnd + 2, offset + specEnd)
    spans.mark(MarkdownSpanKind.Marker, offset + specEnd, offset + specEnd + 1)

    // A link's label is parsed as inlines, so `[**名字**](url)` is bold in the preview; an image's
    // alt text is not parsed at all, and marking it up here would style something nothing renders.
    if (!image) inlineSpans(link.label, offset + labelStart, spans)

    return link.end
}

/** The emphasis run opening at [at], or [at] + 1 when nothing there opens one. */
private fun emphasisSpans(
    line: String,
    at: Int,
    offset: Int,
    spans: MutableList<MarkdownSpan>,
): Int {
    val emphasis = emphasisAt(line, at) ?: return at + 1
    val contentStart = at + emphasis.delimiter.length
    spans.mark(MarkdownSpanKind.Marker, offset + at, offset + contentStart)

    // Asking the parser's own rule what this delimiter means instead of repeating the table: `***`
    // is bold *and* italic there, and a copy here would be one edit away from being only one of them.
    val style = emphasis.style(InlineStyle())
    // Italic before bold, and for once the order is not about nesting: `***x***` is both, the two
    // claims land on the same characters, and a renderer applying them in order has to end on the
    // stronger one — the same precedence the post renderer resolves with `bold` first in its `when`.
    val from = offset + contentStart
    val to = offset + emphasis.close
    if (style.italic) spans.mark(MarkdownSpanKind.Italic, from, to)
    if (style.bold) spans.mark(MarkdownSpanKind.Bold, from, to)
    if (style.strikethrough) spans.mark(MarkdownSpanKind.Strikethrough, from, to)

    spans.mark(
        MarkdownSpanKind.Marker,
        offset + emphasis.close,
        offset + emphasis.close + emphasis.delimiter.length,
    )
    inlineSpans(line.substring(contentStart, emphasis.close), offset + contentStart, spans)
    return emphasis.close + emphasis.delimiter.length
}

/**
 * Records [kind] over `[start, end)`, dropping the empty ones.
 *
 * Empty runs are ordinary here — `**bold**` typed one character at a time is `****` for a keystroke,
 * and an unfilled `[]()` is a link the author has not finished — and a style over no characters is
 * work for the text layout that nothing can see.
 */
private fun MutableList<MarkdownSpan>.mark(
    kind: MarkdownSpanKind,
    start: Int,
    end: Int,
) {
    if (end > start) add(MarkdownSpan(kind, start, end))
}
