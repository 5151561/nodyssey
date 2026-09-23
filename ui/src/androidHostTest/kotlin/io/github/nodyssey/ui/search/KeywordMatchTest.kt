package io.github.nodyssey.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The keyword the search results were chosen for, found in the titles that came back. */
class KeywordMatchTest {
    @Test
    fun `every occurrence is matched, whatever case the title used`() {
        val title = "Zouter JP 首发，zouter 香港也有"
        val second = title.indexOf("zouter")

        assertEquals(listOf(0 until 6, second until second + 6), matchRanges(title, "zouter"))
    }

    @Test
    fun `a title without the keyword has no matches`() {
        assertTrue(matchRanges("腾讯云轻量", "zouter").isEmpty())
    }

    @Test
    fun `no keyword means no matches`() {
        assertTrue(matchRanges("腾讯云轻量", null).isEmpty())
        assertTrue(matchRanges("腾讯云轻量", "   ").isEmpty())
    }
}
