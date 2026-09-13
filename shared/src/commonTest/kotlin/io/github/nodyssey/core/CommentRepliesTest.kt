package io.github.nodyssey.core

import io.github.nodyssey.model.PostContent
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentRepliesTest {
    @Test
    fun `直接回复保留原始评论索引和顺序`() {
        val comments = listOf(comment(1), comment(2), comment(3, 1), comment(4, 1), comment(5, 3), comment(6))
        val replies = buildCommentReplies(42, comments)

        assertEquals(listOf(2, 3), replies.directRepliesOf(0))
        assertEquals(listOf(4), replies.directRepliesOf(2))
        assertTrue(replies.directRepliesOf(1).isEmpty())
        assertEquals(listOf("#1", "#2", "#3", "#4", "#5", "#6"), comments.map { it.floor })
    }

    @Test
    fun `跨页引用先独立显示父楼层载入后再归组`() {
        val reply = comment(11, 2)
        val partial = buildCommentReplies(42, listOf(reply))
        assertTrue(partial.directRepliesOf(0).isEmpty())

        val complete = buildCommentReplies(42, listOf(comment(2), reply))
        assertEquals(listOf(1), complete.directRepliesOf(0))
    }

    @Test
    fun `直接回复排除间接回复并随分页载入更新`() {
        val comments = listOf(comment(1), comment(2), comment(3, 1), comment(4, 3), comment(11, 1), comment(12, 4))
        val partial = buildCommentReplies(42, comments.take(4))
        val complete = buildCommentReplies(42, comments)

        assertEquals(listOf(2), partial.directRepliesOf(0))
        assertEquals(listOf(2, 4), complete.directRepliesOf(0))
        assertEquals(listOf(3), complete.directRepliesOf(2))
        assertTrue(complete.directRepliesOf(1).isEmpty())
        assertEquals(listOf("#1", "#2", "#3", "#4", "#11", "#12"), comments.map { it.floor })
    }

    @Test
    fun `回复和开头引用块都可以建立父子关系`() {
        val quoted = comment(3).copy(
            nodes = listOf(RichNode.Quote(listOf(RichNode.Quote(listOf(paragraph(reference(1))))))),
        )
        val spaced = comment(4).copy(
            nodes = listOf(paragraph(InlineNode.Text(" ")), paragraph(InlineNode.LineBreak, reference(1))),
        )
        val replies = buildCommentReplies(42, listOf(comment(1), comment(2, 1), quoted, spaced))

        assertEquals(listOf(1, 2, 3), replies.directRepliesOf(0))
    }

    @Test
    fun `仅提及用户或正文中途引用不认作父评论`() {
        val mention = comment(2).copy(nodes = listOf(paragraph(InlineNode.Link("@reader", "/space/1"))))
        val midParagraph = comment(3).copy(nodes = listOf(paragraph(InlineNode.Text("正文 "), reference(1))))
        val laterParagraph = comment(4).copy(nodes = listOf(paragraph(InlineNode.Text("正文")), paragraph(reference(1))))
        val signature = comment(5).copy(signatureNodes = listOf(paragraph(reference(1))))

        assertNoReplies(listOf(comment(1), mention, midParagraph, laterParagraph, signature))
    }

    @Test
    fun `多处不同引用不能猜测唯一父评论`() {
        val ambiguous = comment(3).copy(nodes = listOf(paragraph(reference(1), InlineNode.Text(" "), reference(2))))
        assertNoReplies(listOf(comment(1), comment(2), ambiguous))
    }

    @Test
    fun `跨帖外站和楼层标签不符的引用不计入回复`() {
        val refs = listOf(
            reference(1).copy(url = "/post-99-1#1"),
            reference(1).copy(url = "https://example.com/post-42-1#1"),
            reference(1).copy(url = "/post-42-1#2"),
            reference(1).copy(url = "/post-42-1"),
        )
        assertNoReplies(listOf(comment(1)) + refs.mapIndexed { index, ref -> comment(index + 3).copy(nodes = listOf(paragraph(ref))) })
    }

    @Test
    fun `自引用向后引用和引用楼主都不形成评论环`() {
        assertNoReplies(listOf(comment(1, 2), comment(2, 2), comment(3, 0)))
    }

    @Test
    fun `缺失楼层和重复楼层不参与有歧义的归组`() {
        assertNoReplies(listOf(comment(1), comment(2, 1), comment(2, 1), comment(3, 2)))
        assertNoReplies(listOf(comment(1), comment(2, 1).copy(floor = null)))
    }

    @Test
    fun `置顶评论独立显示并能承接后续回复`() {
        val replies = buildCommentReplies(42, listOf(comment(1), comment(2, 1).copy(isPinned = true), comment(3, 2)))
        assertTrue(replies.directRepliesOf(0).isEmpty())
        assertEquals(listOf(2), replies.directRepliesOf(1))
    }

    @Test
    fun `长回复链每条只保留自己的直接回复`() {
        val count = 20_000
        val replies = buildCommentReplies(42, (1..count).map { floor -> comment(floor, (floor - 1).takeIf { it > 0 }) })
        for (index in 0 until count - 1) {
            assertEquals(listOf(index + 1), replies.directRepliesOf(index))
        }
        assertTrue(replies.directRepliesOf(count - 1).isEmpty())
    }

    @Test
    fun `不存在的评论返回空结果`() {
        val replies = buildCommentReplies(42, emptyList())
        assertTrue(replies.directRepliesOf(1).isEmpty())
        assertTrue(replies.directRepliesOf(-1).isEmpty())
    }

    private fun assertNoReplies(comments: List<PostContent>) {
        val replies = buildCommentReplies(42, comments)
        assertTrue(comments.indices.all { replies.directRepliesOf(it).isEmpty() })
    }

    private fun reference(floor: Int) = InlineNode.QuoteRef("reader", "#$floor", "/post-42-1#$floor")

    private fun paragraph(vararg inlines: InlineNode) = RichNode.Paragraph(inlines.toList())

    private fun comment(floor: Int, parent: Int? = null) = PostContent(
        commentId = floor.toLong(),
        floor = "#$floor",
        authorName = "reader-$floor",
        authorUid = floor.toLong(),
        avatarUrl = null,
        isOriginalPoster = false,
        badges = emptyList(),
        createdAtText = null,
        createdAtTitle = null,
        categoryTitle = null,
        nodes = listOf(paragraph(*(parent?.let { arrayOf(reference(it), InlineNode.Text(" reply $floor")) } ?: arrayOf(InlineNode.Text("reply $floor"))))),
    )
}
