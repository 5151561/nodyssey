package io.github.nsreader.core.html

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostListParserTest {

    private val page = PostListParser.parse(Fixtures.load("page-1.html"), page = 1)

    @Test
    fun `parses every row on the front page`() {
        assertEquals(49, page.posts.size)
    }

    @Test
    fun `parses the first row end to end`() {
        val first = page.posts.first()
        assertEquals(703692L, first.postId)
        assertEquals("【问👀】115非VIP账号海外上传速度如何 🍊", first.title)
        assertEquals("橘子海", first.authorName)
        assertEquals(17843L, first.authorUid)
        assertEquals("https://www.nodeseek.com/avatar/17843.png", first.avatarUrl)
        assertEquals("日常", first.categoryTitle)
        assertEquals("daily", first.categorySlug)
        assertEquals(39, first.viewCount)
        assertEquals(4, first.commentCount)
        assertEquals("24s ago", first.lastActiveText)
        assertEquals("2026-04-27 13:46:32", first.lastActiveTitle)
    }

    @Test
    fun `detects the lock badge`() {
        val locked = page.posts.first { it.postId == 703692L }
        assertTrue(locked.isLocked)
    }

    @Test
    fun `reports a next page when the pager offers one`() {
        assertTrue(page.hasNextPage)
    }

    @Test
    fun `every row carries an id, title and author`() {
        page.posts.forEach { post ->
            assertTrue("empty title for ${post.postId}", post.title.isNotBlank())
            assertTrue("bad id", post.postId > 0)
            assertNotNull("no avatar for ${post.postId}", post.avatarUrl)
        }
    }
}
