package io.github.plaza.core.richtext

/**
 * Small, deterministic Markdown subset, used by editor previews and by the fields a site hands over
 * as Markdown rather than as HTML — a profile page's Readme above all.
 *
 * What it is aiming at is `markdownit({breaks: true})`, which is what the forums this was written
 * against run: the default preset, so tables and strikethrough are in, `linkify` is **off** — a bare
 * URL stays text on the site, so it stays text here — and a single newline is a line break rather
 * than a space.
 */
fun parseMarkdown(markdown: String): List<RichNode> {
    val lines = markdown.lines()
    val result = mutableListOf<RichNode>()
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        when {
            line.isBlank() -> index++

            line.startsWith("```") -> {
                val language = line.removePrefix("```").trim().ifBlank { null }
                val code = mutableListOf<String>()
                index++
                while (index < lines.size && !lines[index].startsWith("```")) {
                    code += lines[index++]
                }
                if (index < lines.size) index++
                result += RichNode.CodeBlock(code.joinToString("\n"), language)
            }

            HEADING.matches(line) -> {
                val match = requireNotNull(HEADING.matchEntire(line))
                val level = match.groupValues[1].length
                result += splitImages(parseInlines(match.groupValues[2])) { RichNode.Heading(level, it) }
                index++
            }

            DIVIDER.matches(line.trim()) -> {
                result += RichNode.Divider
                index++
            }

            line.startsWith(">") -> {
                val quoted = mutableListOf<String>()
                while (index < lines.size && lines[index].startsWith(">")) {
                    quoted += lines[index++].removePrefix(">").removePrefix(" ")
                }
                result += RichNode.Quote(parseMarkdown(quoted.joinToString("\n")))
            }

            UNORDERED_LIST.matches(line) || ORDERED_LIST.matches(line) -> {
                val ordered = ORDERED_LIST.matches(line)
                val matcher = if (ordered) ORDERED_LIST else UNORDERED_LIST
                val items = mutableListOf<List<RichNode>>()
                while (index < lines.size && matcher.matches(lines[index])) {
                    val text = requireNotNull(matcher.matchEntire(lines[index])).groupValues[1]
                    // Parsed as a document rather than as a paragraph: the site's own readmes write
                    // `- #### 支持ipv6转发`, and a list item that only ever holds inlines shows the
                    // hashes.
                    items += parseMarkdown(text)
                    index++
                }
                result += RichNode.ListBlock(ordered = ordered, items = items)
            }

            isTableStart(lines, index) -> {
                val cells = mutableListOf(tableCells(line).map { parseInlines(it) })
                // The header's underline carries only the alignment, which this table does not draw.
                index += 2
                while (index < lines.size && lines[index].isNotBlank() && lines[index].contains('|')) {
                    cells += tableCells(lines[index]).map { parseInlines(it) }
                    index++
                }
                result += RichNode.Table(cells)
            }

            blockImage(line.trim()) != null -> {
                result += requireNotNull(blockImage(line.trim()))
                index++
            }

            else -> {
                val paragraph = mutableListOf(line)
                index++
                while (index < lines.size && lines[index].isNotBlank() && !startsBlock(lines, index)) {
                    paragraph += lines[index++]
                }
                val inlines = mutableListOf<InlineNode>()
                paragraph.forEachIndexed { lineIndex, value ->
                    if (lineIndex > 0) inlines += InlineNode.LineBreak
                    inlines += parseInlines(value)
                }
                result += splitImages(inlines, RichNode::Paragraph)
            }
        }
    }
    return result
}

/**
 * An inline run split around the images in it, each of which becomes a block of its own.
 *
 * A paragraph or a heading is a place an image can be drawn at full width, and the renderer draws an
 * image left in the inline flow as a labelled link instead of a picture — so a screenshot written
 * beside a few words of text was one the reader could tap but never see. Only a table cell keeps its
 * images inline, because a cell has no block position to promote them into.
 *
 * This is the rule the HTML parser applies to the same Markdown once the site has rendered it, which
 * is what keeps an editor preview showing what the post will look like.
 */
private fun splitImages(
    inlines: List<InlineNode>,
    run: (List<InlineNode>) -> RichNode,
): List<RichNode> {
    if (inlines.none { it is InlineNode.Image }) return listOf(run(inlines))

    val blocks = mutableListOf<RichNode>()
    val pending = mutableListOf<InlineNode>()

    fun flush() {
        val trimmed = pending.trimEdges()
        if (trimmed.isNotEmpty()) blocks += run(trimmed)
        pending.clear()
    }

    for (node in inlines) {
        if (node is InlineNode.Image) {
            flush()
            blocks += RichNode.BlockImage(url = node.url, alt = node.alt)
        } else {
            pending += node
        }
    }
    flush()
    return blocks
}

/**
 * Drops the breaks and blank text an image leaves at the edges of the run beside it — without them
 * the text under a lifted image would start on a blank line the source never wrote.
 */
private fun List<InlineNode>.trimEdges(): List<InlineNode> {
    fun blank(node: InlineNode) =
        node is InlineNode.LineBreak || (node is InlineNode.Text && node.text.isBlank())

    var start = 0
    var end = size
    while (start < end && blank(this[start])) start++
    while (end > start && blank(this[end - 1])) end--
    return subList(start, end).toList()
}

/**
 * The first [limit] lines of [markdown], extended so that a table is never cut in half.
 *
 * A pipe table is the one block whose opening line means nothing without the next one: cut between
 * them and a collapsed Readme shows `|区域|价格|月流量|` as prose. Rows are a single line each, so
 * finishing the table costs the preview far less height than the alternative reads as a bug.
 */
fun collapseMarkdown(
    markdown: String,
    limit: Int,
): String {
    val lines = markdown.lines()
    if (lines.size <= limit) return markdown

    var cut = limit
    var index = 0
    while (index < cut) {
        if (!isTableStart(lines, index)) {
            index++
            continue
        }
        var end = index + 2
        while (end < lines.size && lines[end].isNotBlank() && lines[end].contains('|')) end++
        if (end > cut) cut = end
        index = end
    }
    return lines.take(cut).joinToString("\n")
}

private fun startsBlock(
    lines: List<String>,
    index: Int,
): Boolean {
    val line = lines[index]
    return line.startsWith("```") ||
        line.startsWith(">") ||
        HEADING.matches(line) ||
        DIVIDER.matches(line.trim()) ||
        UNORDERED_LIST.matches(line) ||
        ORDERED_LIST.matches(line) ||
        isTableStart(lines, index) ||
        blockImage(line.trim()) != null
}

// --- Tables -------------------------------------------------------------------------------------

/**
 * Whether [index] opens a pipe table.
 *
 * The underline decides it, and its cell count has to match the header's — that is the rule the
 * site's parser applies, and it is what keeps a paragraph that merely contains a `|` from being read
 * as a one-column table.
 */
private fun isTableStart(
    lines: List<String>,
    index: Int,
): Boolean {
    val header = lines[index]
    if (!header.contains('|')) return false
    val underline = lines.getOrNull(index + 1)?.trim() ?: return false
    if (!TABLE_UNDERLINE.matches(underline)) return false
    return tableCells(header).size == tableCells(underline).size
}

/** Splits one table row on its unescaped pipes, dropping the optional leading and trailing one. */
private fun tableCells(line: String): List<String> {
    val trimmed = line.trim()
    val body = trimmed
        .removePrefix("|")
        .let { if (it.endsWith("|") && !it.endsWith("\\|")) it.dropLast(1) else it }

    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var index = 0
    while (index < body.length) {
        val char = body[index]
        when {
            char == '\\' && body.getOrNull(index + 1) == '|' -> {
                cell.append('|')
                index += 2
            }

            char == '|' -> {
                cells += cell.toString().trim()
                cell.clear()
                index++
            }

            else -> {
                cell.append(char)
                index++
            }
        }
    }
    cells += cell.toString().trim()
    return cells
}

// --- Inline -------------------------------------------------------------------------------------

/**
 * One run of inline Markdown, parsed by scanning rather than by racing regexes.
 *
 * The regexes this replaced could not nest: `**[TG机器人](https://t.me/…)**` matched the bold rule
 * first, and the whole link — brackets, URL and all — came out as bold prose. Emphasis and links now
 * recurse into their own content, and [style] is what the enclosing run has accumulated so far.
 */
private fun parseInlines(
    text: String,
    style: InlineStyle = InlineStyle(),
): List<InlineNode> {
    val result = mutableListOf<InlineNode>()
    val pending = StringBuilder()
    var index = 0

    fun flush() {
        if (pending.isEmpty()) return
        result += InlineNode.Text(pending.toString(), style)
        pending.clear()
    }

    while (index < text.length) {
        val char = text[index]
        val escaped = text.getOrNull(index + 1)
        when {
            // A backslash escapes punctuation only, so `C:\path` and `\n` stay as written.
            char == '\\' && escaped != null && !escaped.isLetterOrDigit() && !escaped.isWhitespace() -> {
                pending.append(escaped)
                index += 2
            }

            char == '`' -> {
                val fence = text.runLengthAt(index)
                val close = text.indexOf("`".repeat(fence), index + fence)
                if (close < 0) {
                    pending.append(char)
                    index++
                } else {
                    flush()
                    result +=
                        InlineNode.Text(
                            text.substring(index + fence, close).trim(),
                            style.copy(code = true),
                        )
                    index = close + fence
                }
            }

            char == '!' && escaped == '[' -> {
                val span = linkSpan(text, index + 1)
                if (span == null) {
                    pending.append(char)
                    index++
                } else {
                    flush()
                    // Kept as an image node. In a table cell that is where it stays, because a cell
                    // has no block position; everywhere else [splitImages] lifts it out into one.
                    result += InlineNode.Image(span.url, span.label.ifBlank { null })
                    index = span.end
                }
            }

            char == '[' -> {
                val span = linkSpan(text, index)
                if (span == null) {
                    pending.append(char)
                    index++
                } else {
                    flush()
                    result += link(span, style)
                    index = span.end
                }
            }

            else -> {
                val emphasis = emphasisAt(text, index)
                if (emphasis == null) {
                    pending.append(char)
                    index++
                } else {
                    flush()
                    result +=
                        parseInlines(
                            text.substring(index + emphasis.delimiter.length, emphasis.close),
                            emphasis.style(style),
                        )
                    index = emphasis.close + emphasis.delimiter.length
                }
            }
        }
    }
    flush()
    return result
}

/** `[label](url "title")` as read off the source: where it ends, and what it points at. */
private class LinkSpan(
    val label: String,
    val url: String,
    val end: Int,
)

/** Reads a link starting at its `[`, or returns null when what follows is not one. */
private fun linkSpan(
    text: String,
    start: Int,
): LinkSpan? {
    val labelEnd = matchingDelimiter(text, start, '[', ']')
    if (labelEnd < 0 || text.getOrNull(labelEnd + 1) != '(') return null
    val specEnd = matchingDelimiter(text, labelEnd + 1, '(', ')')
    if (specEnd < 0) return null
    val url = linkDestination(text.substring(labelEnd + 2, specEnd))
    if (url.isEmpty()) return null
    return LinkSpan(label = text.substring(start + 1, labelEnd), url = url, end = specEnd + 1)
}

/**
 * A link, flattened to the one label and the one style [InlineNode.Link] can carry.
 *
 * `**[名字](url)**` and `[**名字**](url)` both come out bold, because in either case every piece of
 * the label agrees on its style. A label styled only in part keeps the surrounding style instead —
 * the alternative would be splitting one link into several, and half a link is not tappable as the
 * thing it is.
 */
private fun link(
    span: LinkSpan,
    style: InlineStyle,
): InlineNode.Link {
    val label = parseInlines(span.label, style)
    val text =
        label.joinToString("") {
            when (it) {
                is InlineNode.Text -> it.text
                is InlineNode.Link -> it.text
                else -> ""
            }
        }
    val labelStyle =
        label.filterIsInstance<InlineNode.Text>().map(InlineNode.Text::style).distinct().singleOrNull()
    return InlineNode.Link(
        text = text.ifBlank { span.url },
        url = span.url,
        style = labelStyle ?: style,
    )
}

/**
 * The destination out of a link's `(…)`, dropping the optional title.
 *
 * The title is what broke `[![卡片](…/card)](https://ippurity.com "点击查看IP信息")`: read whole, the
 * URL carried the quoted title along with it.
 */
private fun linkDestination(spec: String): String {
    val trimmed = spec.trim()
    if (trimmed.startsWith("<")) {
        val end = trimmed.indexOf('>')
        if (end > 0) return trimmed.substring(1, end)
    }
    return trimmed.takeWhile { !it.isWhitespace() }
}

/** Index of the [close] that balances the [open] at [start], or -1 when there is none. */
private fun matchingDelimiter(
    text: String,
    start: Int,
    open: Char,
    close: Char,
): Int {
    var depth = 0
    var index = start
    while (index < text.length) {
        when {
            text[index] == '\\' -> index++

            text[index] == open -> depth++

            text[index] == close -> {
                depth--
                if (depth == 0) return index
            }
        }
        index++
    }
    return -1
}

/** An emphasis run: its delimiter, where the closing one starts, and what it does to the style. */
private class Emphasis(
    val delimiter: String,
    val close: Int,
    val style: (InlineStyle) -> InlineStyle,
)

private val EMPHASIS_DELIMITERS: List<Pair<String, (InlineStyle) -> InlineStyle>> =
    listOf(
        // Longest first, so `***` is not read as `**` followed by a stray `*`.
        "***" to { it: InlineStyle -> it.copy(bold = true, italic = true) },
        "___" to { it: InlineStyle -> it.copy(bold = true, italic = true) },
        "**" to { it: InlineStyle -> it.copy(bold = true) },
        "__" to { it: InlineStyle -> it.copy(bold = true) },
        "~~" to { it: InlineStyle -> it.copy(strikethrough = true) },
        "*" to { it: InlineStyle -> it.copy(italic = true) },
        "_" to { it: InlineStyle -> it.copy(italic = true) },
    )

/** The emphasis run opening at [start], or null when nothing there opens one. */
private fun emphasisAt(
    text: String,
    start: Int,
): Emphasis? {
    for ((delimiter, style) in EMPHASIS_DELIMITERS) {
        if (!text.startsWith(delimiter, start)) continue
        // An underscore inside a word is part of the word: `aurora_scbot` is a bot's name.
        if (delimiter.startsWith("_") && text.getOrNull(start - 1)?.isLetterOrDigit() == true) continue
        val content = start + delimiter.length
        // An opener is followed by content, never by a space: `2 * 3 * 4` is arithmetic.
        if (text.getOrNull(content)?.isWhitespace() != false) continue
        val close = closingDelimiter(text, content, delimiter)
        if (close < 0) continue
        return Emphasis(delimiter = delimiter, close = close, style = style)
    }
    return null
}

/** Where [delimiter] closes the run that starts at [content], or -1 when it never does. */
private fun closingDelimiter(
    text: String,
    content: Int,
    delimiter: String,
): Int {
    var from = content
    while (true) {
        val at = text.indexOf(delimiter, from)
        if (at < 0) return -1
        val closes =
            at > content &&
                !text[at - 1].isWhitespace() &&
                !(delimiter.startsWith("_") && text.getOrNull(at + delimiter.length)?.isLetterOrDigit() == true)
        if (closes) return at
        from = at + delimiter.length
    }
}

/** How many of the character at [start] run consecutively — a code span's fence length. */
private fun String.runLengthAt(start: Int): Int {
    var end = start
    while (end < length && this[end] == this[start]) end++
    return end - start
}

/**
 * An image on a line of its own, with or without the link a readme wraps it in.
 *
 * `[![卡片](https://…/card)](https://ippurity.com "点击查看IP信息")` is a clickable image on the site;
 * here it is the image, and the link is dropped — which is what the HTML parser already does with
 * the `<a><img></a>` the site generates from it.
 */
private fun blockImage(line: String): RichNode.BlockImage? {
    val match = BLOCK_IMAGE.matchEntire(line) ?: LINKED_BLOCK_IMAGE.matchEntire(line) ?: return null
    return RichNode.BlockImage(
        url = linkDestination(match.groupValues[2]),
        alt = match.groupValues[1].ifBlank { null },
    )
}

// Up to three leading spaces still open a block, which is the rule the site's parser follows — and
// the reason ` #### **[TG机器人](…)**` was a paragraph showing its own hashes.
private val HEADING = Regex("^ {0,3}(#{1,6})\\s+(.+)$")
private val DIVIDER = Regex("^(-{3,}|\\*{3,})$")
private val UNORDERED_LIST = Regex("^\\s*[-*+]\\s+(.+)$")
private val ORDERED_LIST = Regex("^\\s*\\d+[.)]\\s+(.+)$")
private val BLOCK_IMAGE = Regex("^!\\[([^]]*)]\\(([^)]+)\\)$")
private val LINKED_BLOCK_IMAGE = Regex("^\\[!\\[([^]]*)]\\(([^)]+)\\)]\\([^)]+\\)$")
private val TABLE_UNDERLINE = Regex("^\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)*\\|?$")
