package io.github.nsreader.core.html

import io.github.nsreader.model.InlineNode
import io.github.nsreader.model.RichNode
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the inline folding the renderer depends on.
 *
 * The block vocabulary is already covered through [PostDetailParserTest]; what is new here is the
 * quote reference, which only exists because two adjacent anchors read badly as two blue links.
 */
class RichContentParserTest {
    private fun parse(html: String): List<RichNode> = RichContentParser.parse(Jsoup.parse("<article>$html</article>").selectFirst("article"))

    private fun inlinesOf(nodes: List<RichNode>): List<InlineNode> = (nodes.first() as RichNode.Paragraph).inlines

    @Test
    fun `folds a mention followed by a floor link into one quote reference`() {
        val nodes =
            parse(
                """<p><a href="/member?t=hogue">@hogue</a> <a href="/post-703863-1#2">#2</a> 只等了几分钟</p>""",
            )

        val inlines = inlinesOf(nodes)
        val ref = inlines.first() as InlineNode.QuoteRef
        assertEquals("hogue", ref.name)
        assertEquals("#2", ref.floor)
        assertTrue(ref.url.endsWith("/post-703863-1#2"))
        assertEquals("只等了几分钟", (inlines[1] as InlineNode.Text).text.trim())
    }

    /** Quote references live inside `blockquote` as often as inline, and must fold there too. */
    @Test
    fun `folds a quote reference nested in a blockquote`() {
        val nodes =
            parse(
                """<blockquote><p><a href="/member?t=zhh123">@zhh123</a> <a href="/post-1-1#3">#3</a> 原文</p></blockquote>""",
            )

        val quote = nodes.first() as RichNode.Quote
        val inlines = (quote.children.first() as RichNode.Paragraph).inlines
        assertEquals("zhh123", (inlines.first() as InlineNode.QuoteRef).name)
    }

    /** A bare mention is still just a link; only the pair means "replying to that floor". */
    @Test
    fun `leaves a mention with no floor link alone`() {
        val nodes = parse("""<p><a href="/member?t=hogue">@hogue</a> 说得对</p>""")

        val inlines = inlinesOf(nodes)
        assertTrue("got $inlines", inlines.first() is InlineNode.Link)
        assertTrue(inlines.none { it is InlineNode.QuoteRef })
    }

    /** `#2` next to an ordinary link must not be mistaken for a reply to floor two. */
    @Test
    fun `leaves an unrelated link pair alone`() {
        val nodes =
            parse("""<p><a href="https://example.com">example.com</a> <a href="/post-1-1#2">#2</a></p>""")

        assertTrue(inlinesOf(nodes).none { it is InlineNode.QuoteRef })
    }
}
