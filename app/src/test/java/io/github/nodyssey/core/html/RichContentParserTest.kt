package io.github.nodyssey.core.html

import io.github.nodyssey.model.InlineNode
import io.github.nodyssey.model.RichNode
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

    @Test
    fun `splits ordinary images out of a text paragraph as full width blocks`() {
        val nodes =
            parse("""<p>图片之前<img src="/attachments/result.png" alt="测速结果">图片之后</p>""")

        assertEquals(3, nodes.size)
        assertEquals("图片之前", ((nodes[0] as RichNode.Paragraph).inlines.single() as InlineNode.Text).text)
        assertTrue(nodes[1] is RichNode.BlockImage)
        assertTrue((nodes[1] as RichNode.BlockImage).url.endsWith("/attachments/result.png"))
        assertEquals("图片之后", ((nodes[2] as RichNode.Paragraph).inlines.single() as InlineNode.Text).text)
    }

    @Test
    fun `keeps only site stickers inline`() {
        val nodes =
            parse(
                """<p>表情<img class="sticker" src="/static/image/sticker/ac/01.png" alt="ac01">仍在本行</p>""",
            )

        assertEquals(1, nodes.size)
        val inlines = inlinesOf(nodes)
        assertEquals(1, inlines.filterIsInstance<InlineNode.Sticker>().size)
    }

    @Test
    fun `renders every bare ordinary image as its own block`() {
        val nodes =
            parse(
                """<p><a href="/full/a.png"><img src="/thumb/a.png"></a><img src="/b.png"></p>""",
            )

        assertEquals(2, nodes.size)
        assertTrue(nodes.all { it is RichNode.BlockImage })
    }

    // --- Vote placeholder ----------------------------------------------------

    /**
     * The exact markup a vote post arrives as, captured from `/post-857694-1`. The marker shares a
     * paragraph with the body text, so it has to split it — a card cannot live in an AnnotatedString.
     */
    @Test
    fun `a vote marker becomes a block and splits the paragraph it shared`() {
        val nodes =
            parse(
                """<p><a href="javascript://void(0)" data-href="nsapp://vote?id=2871">""" +
                    """nsapp://vote?id=2871</a><br>三家的价格和权益差不多</p>""",
            )

        assertEquals(2, nodes.size)
        assertEquals(RichNode.VotePlaceholder(2871), nodes[0])
        assertEquals("三家的价格和权益差不多", (nodes[1] as RichNode.Paragraph).inlines.first().let { (it as InlineNode.Text).text })
    }

    /** What the app used to do with a vote: render `nsapp://vote?id=2871` as a blue link. */
    @Test
    fun `a vote marker never renders as a link or as its own raw text`() {
        val nodes =
            parse(
                """<p><a href="javascript://void(0)" data-href="nsapp://vote?id=2871">nsapp://vote?id=2871</a></p>""",
            )

        val inlines = nodes.filterIsInstance<RichNode.Paragraph>().flatMap { it.inlines }
        assertTrue(inlines.filterIsInstance<InlineNode.Link>().isEmpty())
        assertTrue(inlines.filterIsInstance<InlineNode.Text>().none { it.text.contains("nsapp") })
    }

    /** A body that is nothing but the marker leaves it hanging directly off the article. */
    @Test
    fun `a bare vote anchor at article level is still a block`() {
        val nodes = parse("""<a href="javascript://void(0)" data-href="nsapp://vote?id=99">x</a>""")

        assertEquals(listOf(RichNode.VotePlaceholder(99)), nodes)
    }

    /** The last line of defence: wrapped in anything, it still must not reach the inline flow. */
    @Test
    fun `a vote anchor nested inside emphasis is dropped rather than linked`() {
        val nodes =
            parse(
                """<p><strong><a href="javascript://void(0)" data-href="nsapp://vote?id=7">x</a></strong>后文</p>""",
            )

        val inlines = nodes.filterIsInstance<RichNode.Paragraph>().flatMap { it.inlines }
        assertTrue(inlines.filterIsInstance<InlineNode.Link>().isEmpty())
        assertTrue(inlines.filterIsInstance<InlineNode.Text>().none { it.text.contains("nsapp") })
    }

    /**
     * A 拼车 post files its NodeQuality reports in a table column, and reading the cell as `text()`
     * left the reader a blue-less "点击查看 NQ" that went nowhere.
     */
    @Test
    fun `keeps the link inside a table cell`() {
        val nodes =
            parse(
                """
                <table><tbody>
                <tr><th>车次</th><th>报告</th></tr>
                <tr><td><strong>一号车</strong></td>
                <td><a href="/jump?to=https%3A%2F%2Fnodequality.com%2Fr%2Fabc">点击查看 NQ</a></td></tr>
                </tbody></table>
                """.trimIndent(),
            )

        val table = nodes.single() as RichNode.Table
        assertEquals("车次", (table.content[0][0].single() as InlineNode.Text).text)
        assertTrue((table.content[1][0].single() as InlineNode.Text).style.bold)
        val link = table.content[1][1].single() as InlineNode.Link
        assertEquals("点击查看 NQ", link.text)
        assertTrue("got ${link.url}", link.url.endsWith("/jump?to=https%3A%2F%2Fnodequality.com%2Fr%2Fabc"))
    }

    /** Only `nsapp://vote` is ours. Another app scheme is an ordinary link and stays one. */
    @Test
    fun `another nsapp scheme is left as a link`() {
        val nodes = parse("""<p><a href="/somewhere" data-href="nsapp://other?id=1">别的</a></p>""")

        assertEquals(1, nodes.filterIsInstance<RichNode.Paragraph>().size)
        assertTrue(nodes.filterIsInstance<RichNode.VotePlaceholder>().isEmpty())
        assertEquals("别的", (inlinesOf(nodes).first() as InlineNode.Link).text)
    }
}
