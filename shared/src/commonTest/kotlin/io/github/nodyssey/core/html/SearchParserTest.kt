package io.github.nodyssey.core.html

import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchParserTest {
    @Test
    fun `post search reads real list rows and server page count`() {
        val page = SearchParser.parsePosts(Fixtures.load("search-results.html"), page = 1)

        assertEquals(3, page.totalPages)
        assertEquals(1, page.posts.size)
        assertEquals(838790L, page.posts.single().postId)
        assertEquals("daily", page.posts.single().categorySlug)
        assertTrue(page.hasNextPage)
    }

    /**
     * A one-character query, which the site refuses rather than answers.
     *
     * The page it refuses with is the ordinary frame with no rows in it, so before this it read as
     * "没搜到相关帖子" — an answer to a search that never ran. Its 404 is no help either: the body
     * carries `id="nsk-body"`, which classifies it as real content before any status is consulted.
     */
    @Test
    fun `a term the site will not search is its own failure, not an empty result`() {
        val html = Fixtures.load("search-too-short.html")

        val failure =
            assertFailsWith<SiteException> { SearchParser.parsePosts(html, page = 1) }

        assertEquals(SiteError.QueryTooShort, failure.error)
    }

    /**
     * The endless-walk regression. Past the last page of results the site serves nothing but still
     * renders `pager-next` as a link, so "there are more pages" had to stop being read off the
     * pager alone — every empty page used to buy another request, at a rate the site answers with
     * 429 and Cloudflare eventually answers with a challenge.
     */
    @Test
    fun `a page with no results ends the search even while the pager still offers a next link`() {
        val html = Fixtures.load("search-results-past-end.html")

        assertTrue(html.contains("class=\"pager-next\" href"), "fixture no longer reproduces the live pager")
        val page = SearchParser.parsePosts(html, page = 99)

        assertEquals(emptyList<Long>(), page.posts.map { it.postId })
        assertFalse(page.hasNextPage)
    }
}
