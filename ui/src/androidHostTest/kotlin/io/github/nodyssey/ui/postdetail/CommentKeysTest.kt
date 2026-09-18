package io.github.nodyssey.ui.postdetail

import org.junit.Assert.assertEquals
import org.junit.Test

class CommentKeysTest {
    @Test
    fun `重复楼层和编号仍有独立列表键`() {
        val comments = listOf(replyComment(1), replyComment(1), replyComment(2).copy(commentId = null), replyComment(2).copy(commentId = null))
        val pages = listOf(1, 2, 1, 2)
        assertEquals(comments.size, comments.commentKeys(pages).toSet().size)
    }

    @Test
    fun `前页载入后原有评论的展开键保持稳定`() {
        val comments = replyChain(5)
        val pages = List(comments.size) { 1 }
        assertEquals(comments.drop(2).commentKeys(pages.drop(2)), comments.commentKeys(pages).drop(2))
    }

    /**
     * 一条楼层同时出现在两个已加载页，是 `extend` 只删本页 + `toSnapshot` 不按 commentId 去重
     * 的结构性后果。原有那一行的键不能因此改名：改了 LazyColumn 就会重建它，而挂在旧串上的
     * 展开态、testTag 和等待滚动的目标全部静默失效。
     */
    @Test
    fun `后到的快照里出现重复时原有行的键不变`() {
        val loaded = replyChain(3)
        val before = loaded.commentKeys(List(loaded.size) { 2 })

        // 前一页载入，其中带着一份 #2 的旧副本（上方有楼层被删，它从第 2 页挪到了第 1 页）。
        val after = listOf(replyComment(2)) + loaded
        val keys = after.commentKeys(listOf(1) + List(loaded.size) { 2 })

        assertEquals(before, keys.drop(1))
        assertEquals(after.size, keys.toSet().size)
    }
}
