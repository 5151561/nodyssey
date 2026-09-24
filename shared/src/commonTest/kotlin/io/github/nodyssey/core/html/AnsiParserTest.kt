package io.github.nodyssey.core.html

import com.fleeksoft.ksoup.Ksoup
import io.github.plaza.core.ansi.AnsiDecoder
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
