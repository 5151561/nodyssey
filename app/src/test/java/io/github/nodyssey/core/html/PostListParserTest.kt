package io.github.nodyssey.core.html

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `reads the level a locked post demands`() {
        val locked = page.posts.first { it.postId == 703692L }
        assertEquals(1, locked.lockLevel)
    }

    @Test
    fun `leaves the lock level null on an open post`() {
        val open = page.posts.first { !it.isLocked }
        assertEquals(null, open.lockLevel)
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

    /**
     * The site sends a blocked author's row and hides it in CSS, so the row arrives looking ordinary
     * apart from one class. A scraper that ignores it shows the reader exactly what they blocked.
     */
    @Test
    fun `marks the rows the server blocked`() {
        val html =
            """
            <html><body><ul class="post-list">
              <li class="blocked-post post-list-item">
                <div class="post-title"><a href="/post-11-1">blocked author</a></div>
              </li>
              <li class="post-list-item">
                <div class="post-title"><a href="/post-12-1">ordinary</a></div>
              </li>
            </ul></body></html>
            """.trimIndent()

        val posts = PostListParser.parse(html, page = 1).posts

        assertEquals(listOf(11L, 12L), posts.map { it.postId })
        assertTrue(posts.first().isBlocked)
        assertFalse(posts.last().isBlocked)
    }

    /**
     * 加精 arrives as a link into `/award` inside the title strip. The 快捷功能区 sidebar links there
     * too, on every page including this one, which is why the selector is scoped to the row.
     */
    @Test
    fun `marks the rows the site put a 推荐阅读 diamond on`() {
        val html =
            """
            <html><body>
              <div class="nsk-panel quick-access"><ul role="nav"><li>
                <a href="/award"><svg class="iconpark-icon"><use href="#diamonds"></use></svg>
                <span>推荐阅读</span></a>
              </li></ul></div>
              <ul class="post-list">
                <li class="post-list-item">
                  <div class="post-title">
                    <a href="/post-11-1">加精了的帖子</a>
                    <a href="/award" title="推荐阅读">
                      <svg class="iconpark-icon award"><use href="#diamonds"></use></svg>
                    </a>
                  </div>
                </li>
                <li class="post-list-item">
                  <div class="post-title"><a href="/post-12-1">ordinary</a></div>
                </li>
              </ul>
            </body></html>
            """.trimIndent()

        val posts = PostListParser.parse(html, page = 1).posts

        assertEquals(listOf(11L, 12L), posts.map { it.postId })
        assertTrue(posts.first().isAwarded)
        assertFalse(posts.last().isAwarded)
    }

    /** The sidebar's `/award` link must not mark the rows beside it. */
    @Test
    fun `leaves the front page un-awarded`() {
        assertTrue(page.posts.none { it.isAwarded })
    }

    @Test
    fun `leaves the front page unblocked`() {
        assertTrue(page.posts.none { it.isBlocked })
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
