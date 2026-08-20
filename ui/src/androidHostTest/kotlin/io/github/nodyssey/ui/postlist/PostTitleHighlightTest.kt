package io.github.nodyssey.ui.postlist

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The keyword the search results were chosen for, picked out of the titles that came back. */
class PostTitleHighlightTest {
    private val marked = Color.Red

    @Test
    fun `every occurrence is marked, whatever case the title used`() {
        val title = "Zouter JP 首发，zouter 香港也有"

        val highlighted = highlighted(title, "zouter", marked)

        assertEquals(title, highlighted.text)
        assertEquals(
            listOf(0 to 6, title.indexOf("zouter") to title.indexOf("zouter") + 6),
            highlighted.spanStyles.map { it.start to it.end },
        )
        assertTrue(highlighted.spanStyles.all { it.item.color == marked })
    }

    @Test
    fun `a title without the keyword is left as plain text`() {
        val highlighted = highlighted("腾讯云轻量", "zouter", marked)

        assertEquals("腾讯云轻量", highlighted.text)
        assertTrue(highlighted.spanStyles.isEmpty())
    }

    /** Every list but the search results passes null, and none of them may pay for a rebuild. */
    @Test
    fun `no keyword means no styling`() {
        assertTrue(highlighted("腾讯云轻量", null, marked).spanStyles.isEmpty())
        assertTrue(highlighted("腾讯云轻量", "   ", marked).spanStyles.isEmpty())
    }
}
