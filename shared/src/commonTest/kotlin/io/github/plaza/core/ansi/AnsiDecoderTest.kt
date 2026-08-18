package io.github.plaza.core.ansi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val ESC = "\u001B"

/**
 * The escape grammar on its own, with no markup in front of it.
 *
 * How a site smuggles an `ESC` through its HTML is a separate question, and its own test lives
 * beside the parser that answers it.
 */
class AnsiDecoderTest {

    @Test
    fun `keeps the colour of the run the escape opened`() {
        val decoded = AnsiDecoder.decode("$ESC[36m架构：$ESC[32mx86_64$ESC[0m")

        assertEquals("架构：x86_64", decoded.text)
        assertEquals(2, decoded.spans.size)
        val label = decoded.spans[0]
        assertEquals(0 to 3, label.start to label.end)
        assertEquals(6, label.fg)
        val value = decoded.spans[1]
        assertEquals("x86_64", decoded.text.substring(value.start, value.end))
        assertEquals(2, value.fg)
    }

    @Test
    fun `carries background and bold the way a report draws its badges`() {
        val decoded = AnsiDecoder.decode("$ESC[41m$ESC[1m ✘ VT-x $ESC[0m")

        // The badge's own trailing space goes with the block's trailing whitespace; mid-line, which
        // is where the reports actually put these, it survives.
        assertEquals(" ✘ VT-x", decoded.text)
        val span = decoded.spans.single()
        assertEquals(1, span.bg)
        assertTrue(span.bold)
    }

    @Test
    fun `a reset closes every attribute at once`() {
        val decoded = AnsiDecoder.decode("$ESC[1m$ESC[4mbold$ESC[0mplain")

        assertEquals("boldplain", decoded.text)
        val span = decoded.spans.single()
        assertEquals("bold", decoded.text.substring(span.start, span.end))
        assertTrue(span.bold)
        assertTrue(span.underline)
    }

    /** Reports use backspace to overstrike their bar graphs; a text layout cannot honour it. */
    @Test
    fun `drops control characters that are not newline or tab`() {
        assertEquals("945|", AnsiDecoder.decode("945\r|").text)
    }

    @Test
    fun `counts CJK and emoji as two columns`() {
        // 80 ASCII columns is a benchmark report's own rule width.
        assertEquals(80, AnsiDecoder.decode("+".repeat(80)).columns)
        assertEquals(10, AnsiDecoder.decode("容器虚拟化").columns)
        assertEquals(2, AnsiDecoder.decode("💻").columns)
        // The widest line wins, not the last one.
        assertEquals(6, AnsiDecoder.decode("ab\n容器虚\nc").columns)
    }

    @Test
    fun `leaves ordinary code alone`() {
        val decoded = AnsiDecoder.decode("curl -sL https://example.invalid/x.sh | bash\n\tindented")

        assertEquals("curl -sL https://example.invalid/x.sh | bash\n\tindented", decoded.text)
        assertTrue(decoded.spans.isEmpty())
    }

    @Test
    fun `a truncated escape takes its parameters with it`() {
        assertEquals("done", AnsiDecoder.decode("done$ESC[36").text)
    }
}
