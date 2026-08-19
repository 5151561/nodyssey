package io.github.plaza.core.ansi

import io.github.plaza.core.TerminalColumns
import kotlinx.serialization.Serializable

/**
 * One run of SGR-styled text, as a half-open range into the text it belongs to.
 *
 * [fg] and [bg] are palette indices 0–15 (the eight ANSI colours then their bright variants), not
 * packed colours: a report's `✔ 解锁` green has to become the *theme's* green, and only the renderer
 * knows what that is.
 */
@Serializable
data class AnsiSpan(
    val start: Int,
    val end: Int,
    val fg: Int? = null,
    val bg: Int? = null,
    val bold: Boolean = false,
    val underline: Boolean = false,
)

/**
 * Turns an ANSI escape stream into plain text plus the styling the escapes were describing.
 *
 * Pure string work, and nothing about it is specific to one forum: the escape grammar is the
 * terminal's. Recovering the stream in the first place is not — a site that cannot put a raw `ESC`
 * in its HTML has to encode it somehow, and that trick belongs to whoever knows the site's markup.
 * So the caller hands this a source string and keeps the recovery to itself.
 */
object AnsiDecoder {

    private const val ESC = '\u001B'

    /** A CSI sequence runs until the first byte in this range, which also says what it does. */
    private val FINAL_BYTE = '@'..'~'

    data class Decoded(
        val text: String,
        val spans: List<AnsiSpan>,
        val columns: Int,
    )

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
                    // reason a renderer turns emphasis into weight rather than slant.
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
