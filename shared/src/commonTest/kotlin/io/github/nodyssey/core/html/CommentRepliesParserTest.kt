package io.github.nodyssey.core.html

import io.github.nodyssey.core.CommentReplyTarget
import io.github.nodyssey.core.buildCommentReplies
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentRepliesParserTest {
    private val detail = PostDetailParser.parse(Fixtures.load("post-comment-replies.html"), postId = 42, page = 1)

    @Test
    fun `页面中的普通回复和引用回复按对应楼层建立关系`() {
        val replies = buildCommentReplies(42, detail.comments)
        assertEquals((1..10).map { "#$it" }, detail.comments.map { it.floor })
        assertEquals(listOf(2), replies.directRepliesOf(0))
        assertEquals(listOf(3), replies.directRepliesOf(2))
        assertEquals(CommentReplyTarget(1, 0), replies.replyTargetOf(2))
        assertEquals(CommentReplyTarget(3, 2), replies.replyTargetOf(3))
        assertTrue(replies.directRepliesOf(1).isEmpty())
    }

    @Test
    fun `引用其他帖子的楼层不建立本帖的回复关系`() {
        val replies = buildCommentReplies(42, detail.comments)
        assertEquals(null, replies.replyTargetOf(4))
    }

    /** 置顶只认 `__config__` 的 `pined`；站点渲染置顶楼层的 markup 至今没有抓取样本。 */
    @Test
    fun `置顶从配置读出而不误判普通评论`() {
        assertEquals(listOf("#6", "#7", "#8", "#10"), detail.comments.filter { it.isPinned }.map { it.floor })
    }

    @Test
    fun `置顶评论保留自己的回复目标但不进别人的回复列表`() {
        val replies = buildCommentReplies(42, detail.comments)
        // #6 引用 #1，入口留着……
        assertEquals(CommentReplyTarget(1, 0), replies.replyTargetOf(5))
        // ……但 #1 的「N 条回复」里只有 #3，没有置顶的 #6 和 #7。
        assertEquals(listOf(2), replies.directRepliesOf(0))
        assertTrue(detail.comments.indices.flatMap(replies::directRepliesOf).none { detail.comments[it].isPinned })
    }
}
