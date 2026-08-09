package io.github.nodyssey.core.html

import io.github.nodyssey.model.AnsiSpan
import io.github.plaza.core.TerminalColumns
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Recovers the terminal output NodeSeek hides inside `<code class="language-ansi">`.
 *
 * The site cannot put a raw `ESC` in its HTML, so it encodes every control character as an empty
 * `<span data-ansicode="27">` and leaves the rest of the sequence as ordinary text. Jsoup's
 * `wholeText()` drops empty elements, which is why reading the code element as text yields a stream
 * with the escapes missing and their parameters left behind — the literal `[36m` that leaks into
 * the post body today. The fix is to read the element, not its text: [sourceOf] walks the children
 * and turns each `data-ansicode` back into the character it stands for, and only then is there an
 * ANSI stream to [decode].
 */
object AnsiParser {

    private const val CONTROL_CODE_ATTR = "data-ansicode"
    private const val ESC = '\u001B'

    /** A CSI sequence runs until the first byte in this range, which also says what it does. */
    private val FINAL_BYTE = '@'..'~'

    data class Decoded(
        val text: String,
        val spans: List<AnsiSpan>,
        val columns: Int,
    )

    /** Reassembles the escape-bearing source of a `<pre>`/`<code>` element. */
    fun sourceOf(element: Element): String = buildString { appendSource(element) }

    private fun StringBuilder.appendSource(node: Node) {
        for (child in node.childNodes()) {
            when (child) {
                is TextNode -> append(child.wholeText)

                is Element -> {
                    val code = child.attr(CONTROL_CODE_ATTR).toIntOrNull()
                    // These carry the control character in an attribute and are empty otherwise, so
                    // recursing into one would add nothing.
                    if (code != null) append(code.toChar()) else appendSource(child)
                }

                else -> Unit
            }
        }
    }

    /**
     * Strips the escape sequences and returns what they were saying about the text they enclosed.
     *
     * Ordinary code goes through this unchanged apart from stray control characters, so there is no
     * need to know in advance whether a block is a terminal report.
     */
    fun decode(source: String): Decoded {
        val text = StringBuilder(source.length)
        val spans = mutableListOf<AnsiSpan>()

        var fg: Int? = null
        var bg: Int? = null
        var bold = false
        var underline = false
        var runStart = 0

        fun closeRun() {
            val end = text.length
            if (end > runStart && (fg != null || bg != null || bold || underline)) {
                spans += AnsiSpan(runStart, end, fg, bg, bold, underline)
            }
            runStart = end
        }

        fun applySgr(parameters: String) {
            // `ESC[m` is `ESC[0m`, and so is any parameter we cannot read.
            parameters.split(';').forEach { parameter ->
                when (val code = parameter.trim().toIntOrNull() ?: 0) {
                    0 -> {
                        fg = null
                        bg = null
                        bold = false
                        underline = false
                    }

                    1 -> bold = true

                    4 -> underline = true

                    22 -> bold = false

                    24 -> underline = false

                    39 -> fg = null

                    49 -> bg = null

                    in 30..37 -> fg = code - 30

                    in 40..47 -> bg = code - 40

                    in 90..97 -> fg = code - 90 + BRIGHT_OFFSET

                    in 100..107 -> bg = code - 100 + BRIGHT_OFFSET

                    // Italic is parsed and dropped: Chinese has no italic form, which is the same
                    // reason `InlineStyle.italic` becomes weight rather than slant.
                    else -> Unit
                }
            }
        }

        var index = 0
        while (index < source.length) {
            val char = source[index]
            when {
                char == ESC && source.getOrNull(index + 1) == '[' -> {
                    var end = index + 2
                    while (end < source.length && source[end] !in FINAL_BYTE) end++
                    // A sequence running off the end of the source is truncated output, and the
                    // parameters behind it are not text either.
                    if (end >= source.length) break

                    if (source[end] == 'm') {
                        closeRun()
                        applySgr(source.substring(index + 2, end))
                    }
                    index = end + 1
                }

                char == '\n' || char == '\t' -> {
                    text.append(char)
                    index++
                }

                // Backspace, carriage return and the rest are overstrike instructions a Compose
                // text layout cannot honour; drawn literally they are worse than absent.
                char.code < 0x20 || char.code == 0x7F -> index++

                else -> {
                    text.append(char)
                    index++
                }
            }
        }
        closeRun()

        val trimmed = text.toString().trimEnd()
        return Decoded(
            text = trimmed,
            spans = spans.mapNotNull { span -> span.clampedTo(trimmed.length) },
            columns = TerminalColumns.widthOf(trimmed),
        )
    }

    private fun AnsiSpan.clampedTo(length: Int): AnsiSpan? = when {
        start >= length -> null
        end > length -> copy(end = length)
        else -> this
    }

    /** Palette indices 8–15 are the bright half of the sixteen. */
    private const val BRIGHT_OFFSET = 8
}
