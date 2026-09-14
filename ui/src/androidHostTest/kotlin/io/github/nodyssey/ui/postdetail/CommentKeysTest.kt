package io.github.nodyssey.ui.postdetail

import org.junit.Assert.assertEquals
import org.junit.Test

class CommentKeysTest {
    @Test
    fun `重复楼层和编号仍有独立列表键`() {
        val comments = listOf(replyComment(1), replyComment(1), replyComment(2).copy(commentId = null), replyComment(2).copy(commentId = null))
        assertEquals(comments.size, comments.commentKeys().toSet().size)
    }

    @Test
    fun `前页载入后原有评论的展开键保持稳定`() {
        val comments = replyChain(5)
        assertEquals(comments.drop(2).commentKeys(), comments.commentKeys().drop(2))
    }
}
