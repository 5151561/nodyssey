package io.github.nsreader.ui.composer

import io.github.nsreader.model.InlineNode
import io.github.nsreader.model.InlineStyle
import io.github.nsreader.model.RichNode

/** Small, deterministic Markdown subset used by the editor preview. */
internal fun parseMarkdown(markdown: String): List<RichNode> {
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
                result += RichNode.Heading(match.groupValues[1].length, parseInlines(match.groupValues[2]))
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
                    items += listOf(RichNode.Paragraph(parseInlines(text)))
                    index++
                }
                result += RichNode.ListBlock(ordered = ordered, items = items)
            }

            BLOCK_IMAGE.matches(line.trim()) -> {
                val match = requireNotNull(BLOCK_IMAGE.matchEntire(line.trim()))
                result += RichNode.BlockImage(url = match.groupValues[2], alt = match.groupValues[1].ifBlank { null })
                index++
            }

            else -> {
                val paragraph = mutableListOf(line)
                index++
                while (index < lines.size && lines[index].isNotBlank() && !startsBlock(lines[index])) {
                    paragraph += lines[index++]
                }
                val inlines = mutableListOf<InlineNode>()
                paragraph.forEachIndexed { lineIndex, value ->
                    if (lineIndex > 0) inlines += InlineNode.LineBreak
                    inlines += parseInlines(value)
                }
                result += RichNode.Paragraph(inlines)
            }
        }
    }
    return result
}

private fun startsBlock(line: String): Boolean =
    line.startsWith("```") ||
        line.startsWith(">") ||
        HEADING.matches(line) ||
        DIVIDER.matches(line.trim()) ||
        UNORDERED_LIST.matches(line) ||
        ORDERED_LIST.matches(line) ||
        BLOCK_IMAGE.matches(line.trim())

private fun parseInlines(text: String): List<InlineNode> {
    val result = mutableListOf<InlineNode>()
    var remaining = text
    while (remaining.isNotEmpty()) {
        val candidates = INLINE_PATTERNS.mapNotNull { pattern -> pattern.find(remaining)?.let { pattern to it } }
        val next = candidates.minByOrNull { it.second.range.first }
        if (next == null) {
            result += InlineNode.Text(remaining)
            break
        }
        val (pattern, match) = next
        if (match.range.first > 0) result += InlineNode.Text(remaining.substring(0, match.range.first))
        result += when (pattern) {
            INLINE_IMAGE -> InlineNode.Link(match.groupValues[1].ifBlank { "图片" }, match.groupValues[2])
            LINK -> InlineNode.Link(match.groupValues[1], match.groupValues[2])
            CODE -> InlineNode.Text(match.groupValues[1], InlineStyle(code = true))
            BOLD -> InlineNode.Text(match.groupValues[1], InlineStyle(bold = true))
            STRIKE -> InlineNode.Text(match.groupValues[1], InlineStyle(strikethrough = true))
            else -> InlineNode.Text(match.value)
        }
        remaining = remaining.substring(match.range.last + 1)
    }
    return result
}

private val HEADING = Regex("^(#{1,6})\\s+(.+)$")
private val DIVIDER = Regex("^(-{3,}|\\*{3,})$")
private val UNORDERED_LIST = Regex("^\\s*[-*+]\\s+(.+)$")
private val ORDERED_LIST = Regex("^\\s*\\d+[.)]\\s+(.+)$")
private val BLOCK_IMAGE = Regex("^!\\[([^]]*)]\\(([^)]+)\\)$")
private val INLINE_IMAGE = Regex("!\\[([^]]*)]\\(([^)]+)\\)")
private val LINK = Regex("(?<!!)\\[([^]]+)]\\(([^)]+)\\)")
private val CODE = Regex("`([^`]+)`")
private val BOLD = Regex("\\*\\*([^*]+)\\*\\*")
private val STRIKE = Regex("~~([^~]+)~~")
private val INLINE_PATTERNS = listOf(INLINE_IMAGE, LINK, CODE, BOLD, STRIKE)
