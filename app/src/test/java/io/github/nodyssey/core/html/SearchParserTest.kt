package io.github.nodyssey.core.html

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchParserTest {
    @Test
    fun `post search reads real list rows and server page count`() {
        val page = SearchParser.parsePosts(Fixtures.load("search-results.html"), page = 1)

        assertEquals(3, page.totalPages)
        assertEquals(1, page.posts.size)
        assertEquals(838790L, page.posts.single().postId)
        assertEquals("daily", page.posts.single().categorySlug)
    }
}
