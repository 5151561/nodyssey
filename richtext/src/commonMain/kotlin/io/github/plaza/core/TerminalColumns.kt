package io.github.plaza.core

/**
 * Character widths as a terminal counts them.
 *
 * The benchmark reports posted in 测评 are laid out by padding with spaces on the assumption that a
 * CJK glyph or an emoji occupies two cells and everything else one. Any code that has to recover
 * that layout — measuring how wide a report is, or working out which column a table cell sits under
 * — has to count the same way, because counting characters instead reports a 80-column report as
 * fitting in fifty.
 */
object TerminalColumns {

    /** Total width of [text] in cells, taking the widest line when it spans several. */
    fun widthOf(text: String): Int {
        var widest = 0
        var current = 0
        forEachCodePoint(text) { codePoint ->
            if (codePoint == '\n'.code) {
                widest = maxOf(widest, current)
                current = 0
            } else {
                current += cellsFor(codePoint)
            }
        }
        return maxOf(widest, current)
    }

    /**
     * Column at which the character at [charIndex] of a single line begins.
     *
     * Used to line a table's cells up with the header above it: both were padded in cells, so their
     * character offsets only agree when neither contains CJK.
     */
    fun columnAt(line: String, charIndex: Int): Int {
        var column = 0
        var index = 0
        while (index < charIndex && index < line.length) {
            val codePoint = codePointAt(line, index)
            column += cellsFor(codePoint)
            index += charCount(codePoint)
        }
        return column
    }

    private inline fun forEachCodePoint(text: String, action: (Int) -> Unit) {
        var index = 0
        while (index < text.length) {
            val codePoint = codePointAt(text, index)
            action(codePoint)
            index += charCount(codePoint)
        }
    }

    /**
     * The code point starting at [index], as `String.codePointAt` would report it.
     *
     * Spelled out rather than delegated because both of the ways the JVM offers this — `Character`
     * and the `codePointAt` the Kotlin stdlib only publishes for JVM — are platform API, and the
     * rule for decoding a surrogate pair is four lines. An unpaired surrogate reads as itself, which
     * is what the JVM does too and what keeps a truncated string from throwing here.
     */
    private fun codePointAt(text: String, index: Int): Int {
        val high = text[index]
        if (high.isHighSurrogate() && index + 1 < text.length) {
            val low = text[index + 1]
            if (low.isLowSurrogate()) return 0x10000 + ((high.code - 0xD800) shl 10) + (low.code - 0xDC00)
        }
        return high.code
    }

    /** `Character.charCount`: how many `Char`s the code point occupied. */
    private fun charCount(codePoint: Int): Int = if (codePoint >= 0x10000) 2 else 1

    private fun cellsFor(codePoint: Int): Int = if (isWide(codePoint)) 2 else 1

    private fun isWide(codePoint: Int): Boolean =
        codePoint in 0x1100..0x115F ||
            codePoint in 0x2E80..0x303E ||
            codePoint in 0x3041..0x33FF ||
            codePoint in 0x3400..0x4DBF ||
            codePoint in 0x4E00..0x9FFF ||
            codePoint in 0xA000..0xA4CF ||
            codePoint in 0xAC00..0xD7A3 ||
            codePoint in 0xF900..0xFAFF ||
            codePoint in 0xFE30..0xFE6F ||
            codePoint in 0xFF00..0xFF60 ||
            codePoint in 0xFFE0..0xFFE6 ||
            codePoint in 0x1F300..0x1FAFF ||
            codePoint in 0x20000..0x3FFFD
}
