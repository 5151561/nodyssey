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
    fun `从页面和配置中识别置顶而不误判普通评论`() {
        assertEquals(listOf("#6", "#7", "#8", "#10"), detail.comments.filter { it.isPinned }.map { it.floor })
        val replies = buildCommentReplies(42, detail.comments)
        assertTrue(detail.comments.indices.flatMap(replies::directRepliesOf).none { detail.comments[it].isPinned })
    }
}
