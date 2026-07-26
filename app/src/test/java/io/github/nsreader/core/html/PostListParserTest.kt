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

    /** The last pager position renders as "..100"; the digits are the total. */
    @Test
    fun `reads the page total from the pager`() {
        assertEquals(100, page.totalPages)
    }

    /** A pager whose positions carry no digits must degrade to the page we are on, not to 1. */
    @Test
    fun `falls back to the current page when the pager is unreadable`() {
        val html =
            """
            <html><body><div role="navigation" aria-label="pagination">
              <span class="pager-pos pager-cur">…</span>
            </div></body></html>
            """.trimIndent()

        assertEquals(3, PostListParser.parse(html, page = 3).totalPages)
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
