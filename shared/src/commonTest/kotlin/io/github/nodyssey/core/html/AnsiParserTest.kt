package io.github.nodyssey.core.html

import com.fleeksoft.ksoup.Ksoup
import io.github.plaza.core.ansi.AnsiDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What NodeSeek's markup hides, not what the escapes mean — that half is
 * [io.github.plaza.core.ansi.AnsiDecoderTest].
 */
class AnsiParserTest {

    private fun code(html: String) = requireNotNull(Ksoup.parse("<pre><code>$html</code></pre>").selectFirst("code"))

    private fun decode(html: String) = AnsiDecoder.decode(AnsiParser.sourceOf(code(html)))

    /**
     * The bug this class exists for: the escapes are empty elements, so reading the code element as
     * text loses them and leaves their parameters behind as visible `[36m`.
     */
    @Test
    fun `rebuilds the escape characters that jsoup text would drop`() {
        val html = """<span data-ansicode="27"></span>[36m容器/虚拟化：<span data-ansicode="27"></span>[0m"""

        // The old reading, kept so that a regression back to `wholeText()` fails here.
        assertEquals("[36m容器/虚拟化：[0m", code(html).wholeText())

        assertEquals("容器/虚拟化：", decode(html).text)
    }

    @Test
    fun `keeps the colour of the run the escape opened`() {
        val html = """<span data-ansicode="27"></span>[36m架构：<span data-ansicode="27"></span>[32mx86_64<span data-ansicode="27"></span>[0m"""

        val decoded = decode(html)

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
    fun `carries background and bold the way the reports draw their badges`() {
        val decoded =
            decode(
                """<span data-ansicode="27"></span>[41m<span data-ansicode="27"></span>[1m ✘ VT-x <span data-ansicode="27"></span>[0m""",
            )

        // The badge's own trailing space goes with the block's trailing whitespace; mid-line, which
        // is where the reports actually put these, it survives.
        assertEquals(" ✘ VT-x", decoded.text)
        val span = decoded.spans.single()
        assertEquals(1, span.bg)
        assertTrue(span.bold)
    }

    @Test
    fun `a reset closes every attribute at once`() {
        val html = """<span data-ansicode="27"></span>[1m<span data-ansicode="27"></span>[4mbold<span data-ansicode="27"></span>[0mplain"""

        val decoded = decode(html)

        assertEquals("boldplain", decoded.text)
        val span = decoded.spans.single()
        assertEquals("bold", decoded.text.substring(span.start, span.end))
        assertTrue(span.bold)
        assertTrue(span.underline)
    }

    /** The reports use backspace to overstrike their bar graphs; a text layout cannot honour it. */
    @Test
    fun `drops control characters that are not newline or tab`() {
        val html = """945<span data-ansicode="8"></span><span data-ansicode="13"></span>|"""

        assertEquals("945|", decode(html).text)
    }
}
