package io.github.nodyssey.core.html

import com.fleeksoft.ksoup.Ksoup
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the inline folding the renderer depends on.
 *
 * The block vocabulary is already covered through [PostDetailParserTest]; what is new here is the
 * quote reference, which only exists because two adjacent anchors read badly as two blue links.
 */
class RichContentParserTest {
    private fun parse(html: String): List<RichNode> = RichContentParser.parse(Ksoup.parse("<article>$html</article>").selectFirst("article"))

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

    /**
     * `<br>` and the newline the site writes after it are one break, not a break and an indent.
     *
     * jsoup folds that source newline into a leading space on the text that follows, and keeping
     * it indented every line after a `<br>` by a space no browser draws — collapsible whitespace
     * at the head of a line box is thrown away. Measured on post-584268, whose opening paragraph
     * carries three of them.
     */
    @Test
    fun `drops the space a source newline leaves after a hard break`() {
        val nodes = parse("<p>\u4eca\u5e74\u53ef\u4ee5\u5148\u884c\u8eba\u5e73\u4e86\u3002<br>\n\u8bf4\u5230\u8eba\u5e73\uff0c\u8bb2\u771f\u3002</p>")

        val inlines = inlinesOf(nodes)
        assertEquals(InlineNode.LineBreak, inlines[1])
        assertEquals("\u8bf4\u5230\u8eba\u5e73\uff0c\u8bb2\u771f\u3002", (inlines[2] as InlineNode.Text).text)
    }

    /** A space the author actually typed mid-line is content, and survives. */
    @Test
    fun `keeps a space that is not at the head of a line`() {
        val nodes = parse("<p>\u524d\u9762 \u540e\u9762</p>")

        assertEquals("\u524d\u9762 \u540e\u9762", (inlinesOf(nodes).single() as InlineNode.Text).text)
    }

    /** A bare mention is still just a link; only the pair means "replying to that floor". */
    @Test
    fun `leaves a mention with no floor link alone`() {
        val nodes = parse("""<p><a href="/member?t=hogue">@hogue</a> 说得对</p>""")

        val inlines = inlinesOf(nodes)
        assertTrue(inlines.first() is InlineNode.Link, "got $inlines")
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

    /**
     * The shape of `/post-287967-1`: result screenshots filed in a 2×2 layout table. A cell has no
     * block to promote an image into, and dropping it there lost all four screenshots.
     */
    @Test
    fun `keeps an ordinary image inside a table cell`() {
        val nodes =
            parse(
                """<table><tbody><tr><td><img src="/attachments/v4.png" alt="IPv4测试结果"></td>""" +
                    """<td>文字说明</td></tr></tbody></table>""",
            )

        val table = nodes.single() as RichNode.Table
        val image = table.content[0][0].filterIsInstance<InlineNode.Image>().single()
        assertTrue(image.url.endsWith("/attachments/v4.png"))
        assertEquals("IPv4测试结果", image.alt)
    }

    /** Formatting around an image keeps it in the inline flow, where it must survive as an image. */
    @Test
    fun `keeps an image nested in formatting as an inline image`() {
        val nodes = parse("""<p><strong>看<img src="/attachments/badge.svg"></strong>后文</p>""")

        val image = inlinesOf(nodes).filterIsInstance<InlineNode.Image>().single()
        assertTrue(image.url.endsWith("/attachments/badge.svg"))
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
        assertTrue(link.url.endsWith("/jump?to=https%3A%2F%2Fnodequality.com%2Fr%2Fabc"), "got ${link.url}")
    }

    /** Only the kinds we draw are ours. An unknown app scheme is an ordinary link and stays one. */
    @Test
    fun `another nsapp scheme is left as a link`() {
        val nodes = parse("""<p><a href="/somewhere" data-href="nsapp://other?id=1">别的</a></p>""")

        assertEquals(1, nodes.filterIsInstance<RichNode.Paragraph>().size)
        assertTrue(nodes.filterIsInstance<RichNode.VotePlaceholder>().isEmpty())
        assertEquals("别的", (inlinesOf(nodes).first() as InlineNode.Link).text)
    }

    // --- 星辰收款码 -----------------------------------------------------------

    /**
     * The marker as the site's own editor writes it, key order and all.
     *
     * Everything the card shows comes out of this one string — there is no second request that could
     * correct a field read wrong, and the numbers are money.
     */
    @Test
    fun `a receive code marker becomes a block carrying every field`() {
        val nodes =
            parse(
                """<p><a href="javascript://void(0)" data-href="nsapp://stardust-receive?""" +
                    """member_id=52425&amp;ref_id=100&amp;description=%E8%AF%B7%E6%88%91%E5%96%9D%E6%9D%AF""" +
                    """%20%E5%92%96%E5%95%A1&amp;diff=2&amp;onetime=true">x</a></p>""",
            )

        assertEquals(
            RichNode.StardustReceive(
                memberId = 52425,
                refId = 100,
                amount = 2,
                description = "请我喝杯 咖啡",
                onetime = true,
            ),
            nodes.single(),
        )
    }

    /** `onetime=false` is the ordinary code, and absent means the same thing. */
    @Test
    fun `a receive code without onetime is not one-off`() {
        val nodes =
            parse(
                """<a href="javascript://void(0)" data-href=
                   "nsapp://stardust-receive?member_id=9&amp;ref_id=1&amp;description=&amp;diff=5">x</a>""",
            )

        val code = nodes.single() as RichNode.StardustReceive
        assertFalse(code.onetime)
        assertEquals("", code.description)
    }

    /**
     * The site abandons a marker whose `member_id`, `ref_id` or `diff` is not a bare run of digits,
     * and leaves it on the page as a link. Drawing a card where the web shows text would mean the two
     * disagree about what somebody is being asked to pay.
     */
    @Test
    fun `a receive code with a non-numeric field stays an ordinary link`() {
        val nodes =
            parse(
                """<p><a href="/somewhere" data-href=
                   "nsapp://stardust-receive?member_id=52425&amp;ref_id=abc&amp;diff=2">别的</a></p>""",
            )

        assertTrue(nodes.filterIsInstance<RichNode.StardustReceive>().isEmpty())
        assertEquals("别的", (inlinesOf(nodes).first() as InlineNode.Link).text)
    }

    /** Wrapped in emphasis it still must not reach the inline flow as raw `nsapp://` text. */
    @Test
    fun `a receive code nested inside emphasis is dropped rather than linked`() {
        val nodes =
            parse(
                """<p><strong><a href="javascript://void(0)" data-href=
                   "nsapp://stardust-receive?member_id=9&amp;ref_id=1&amp;diff=5">x</a></strong>后文</p>""",
            )

        val inlines = nodes.filterIsInstance<RichNode.Paragraph>().flatMap { it.inlines }
        assertTrue(inlines.filterIsInstance<InlineNode.Link>().isEmpty())
        assertTrue(inlines.filterIsInstance<InlineNode.Text>().none { it.text.contains("nsapp") })
        // Lifted out of the emphasis and drawn as its own card, exactly as a nested vote marker is.
        assertEquals(1, nodes.filterIsInstance<RichNode.StardustReceive>().size)
    }
}
