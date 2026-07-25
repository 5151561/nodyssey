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

    @Test
    fun `parses the post header`() {
        assertEquals("绿云抢鸡竞赛又要开始了，一波传家宝又要来袭", detail.title)
        assertEquals("ipv4", detail.body.authorName)
        assertEquals(34378L, detail.body.authorUid)
        assertEquals("#0", detail.body.floor)
        assertEquals("日常", detail.body.categoryTitle)
        assertEquals("2026-04-27 15:57:00", detail.body.createdAtTitle)
        assertTrue(detail.body.isOriginalPoster)
    }

    @Test
    fun `separates the attachment image from the inline stickers`() {
        val blockImages = detail.body.nodes.filterIsInstance<RichNode.BlockImage>()
        assertEquals(1, blockImages.size)
        assertEquals(
            "https://cdn.nodeimage.com/i/FBICDKhbCSRbkZ5Kx5qyiyit927K3vCg.webp",
            blockImages.first().url,
        )

        val stickers = detail.body.nodes
            .filterIsInstance<RichNode.Paragraph>()
            .flatMap { it.inlines }
            .filterIsInstance<InlineNode.Sticker>()
        assertEquals(3, stickers.size)
        assertTrue(stickers.all { it.url.startsWith("https://www.nodeseek.com/static/image/sticker/") })
    }

    @Test
    fun `keeps the text that follows inline stickers`() {
        val text = detail.body.nodes
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
    fun `parses a long post without dropping content`() {
        val long =
            PostDetailParser.parse(Fixtures.load("post-705039-1.html"), postId = 705039L, page = 1)
        assertTrue(long.title.isNotBlank())
        assertTrue(long.body.nodes.isNotEmpty())
        assertTrue(long.comments.isNotEmpty())
        long.comments.forEach { comment ->
            assertTrue("comment without author", comment.authorName.isNotBlank())
        }
    }
}
