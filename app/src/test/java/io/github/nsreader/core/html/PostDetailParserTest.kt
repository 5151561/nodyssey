package io.github.nsreader.core.html

import io.github.nsreader.model.InlineNode
import io.github.nsreader.model.RichNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostDetailParserTest {

    private val detail =
        PostDetailParser.parse(Fixtures.load("post-703863-1.html"), postId = 703863L, page = 1)

    /** Page 1 always carries the opening post; a null here is itself a parser failure. */
    private val body = requireNotNull(detail.body)

    @Test
    fun `parses the post header`() {
        assertEquals("绿云抢鸡竞赛又要开始了，一波传家宝又要来袭", detail.title)
        assertEquals("ipv4", body.authorName)
        assertEquals(34378L, body.authorUid)
        assertEquals("#0", body.floor)
        assertEquals("日常", body.categoryTitle)
        assertEquals("2026-04-27 15:57:00", body.createdAtTitle)
        assertTrue(body.isOriginalPoster)
    }

    @Test
    fun `separates the attachment image from the inline stickers`() {
        val blockImages = body.nodes.filterIsInstance<RichNode.BlockImage>()
        assertEquals(1, blockImages.size)
        assertEquals(
            "https://cdn.nodeimage.com/i/FBICDKhbCSRbkZ5Kx5qyiyit927K3vCg.webp",
            blockImages.first().url,
        )

        val stickers = body.nodes
            .filterIsInstance<RichNode.Paragraph>()
            .flatMap { it.inlines }
            .filterIsInstance<InlineNode.Sticker>()
        assertEquals(3, stickers.size)
        assertTrue(stickers.all { it.url.startsWith("https://www.nodeseek.com/static/image/sticker/") })
    }

    @Test
    fun `keeps the text that follows inline stickers`() {
        val text = body.nodes
            .filterIsInstance<RichNode.Paragraph>()
            .flatMap { it.inlines }
            .filterIsInstance<InlineNode.Text>()
            .joinToString("") { it.text }
        assertTrue(text.contains("喊上你的五指小姐姐一起抢吧"))
    }

    @Test
    fun `parses the comment list`() {
        assertTrue(detail.comments.isNotEmpty())
        val first = detail.comments.first()
        assertEquals("ggbeng", first.authorName)
        assertEquals(24520L, first.authorUid)
        assertEquals("#4", first.floor)
        assertEquals(9727591L, first.commentId)
        assertFalse(first.isOriginalPoster)
        assertTrue(first.badges.isEmpty())
    }

    @Test
    fun `reads the header badges`() {
        assertEquals(listOf("楼主"), body.badges)

        val opReply = detail.comments.first { it.isOriginalPoster }
        assertEquals(listOf("楼主"), opReply.badges)

        // The role-tag structure differs from is-poster (text sits in a nested span) — both parse.
        val dev = detail.comments.first { it.authorName == "ggchaos" }
        assertEquals(listOf("Dev"), dev.badges)
    }

    @Test
    fun `floors without an edited marker read as unedited`() {
        assertFalse(body.isEdited)
        assertTrue(detail.comments.none { it.isEdited })
    }

    @Test
    fun `turns br separated lines into line breaks rather than lost text`() {
        val inlines = detail.comments.first().nodes
            .filterIsInstance<RichNode.Paragraph>()
            .flatMap { it.inlines }
        assertTrue(inlines.any { it is InlineNode.LineBreak })
        val text = inlines.filterIsInstance<InlineNode.Text>().joinToString("") { it.text }
        assertTrue(text.contains("如果你是建站"))
        assertTrue(text.contains("如果你喜欢绿帽"))
    }

    @Test
    fun `reads the pager`() {
        assertEquals(1, detail.page)
        assertTrue(detail.totalPages >= 4)
        assertTrue(detail.hasNextPage)
    }

    @Test
    fun `reads the total from the elided last-page shortcut on a long thread`() {
        // A long thread's pager renders the far end as `<span class="ellipsis">..</span>37`,
        // whose text is not a number — the total must come from the href instead.
        val html =
            """
            <html><body>
            <div class="nsk-pager post-top-pager"><div role="navigation">
              <span href="/post-1-1" class="pager-pos pager-cur">1</span>
              <a href="/post-1-2" class="pager-pos">2</a>
              <a href="/post-1-5" class="pager-pos">5</a>
              <a href="/post-1-37" class="pager-pos"><span class="ellipsis">..</span>37</a>
              <a href="/post-1-2" rel="next" class="pager-next"></a>
            </div></div>
            </body></html>
            """.trimIndent()
        val parsed = PostDetailParser.parse(html, postId = 1L, page = 1)
        assertEquals(37, parsed.totalPages)
    }

    @Test
    fun `parses a long post without dropping content`() {
        val long =
            PostDetailParser.parse(Fixtures.load("post-705039-1.html"), postId = 705039L, page = 1)
        assertTrue(long.title.isNotBlank())
        assertTrue(requireNotNull(long.body).nodes.isNotEmpty())
        assertTrue(long.comments.isNotEmpty())
        long.comments.forEach { comment ->
            assertTrue("comment without author", comment.authorName.isNotBlank())
        }
    }
}
