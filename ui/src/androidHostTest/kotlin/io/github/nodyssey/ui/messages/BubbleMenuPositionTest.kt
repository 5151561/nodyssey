package io.github.nodyssey.ui.messages

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where the 复制 / 引用 bar lands.
 *
 * Plain arithmetic, so it is worth pinning here rather than through a popup in a screen test: the
 * bar is in its own window there, and its bounds are measured against that window rather than
 * against the bubble it is supposed to be sitting on.
 */
class BubbleMenuPositionTest {
    private val position = BubbleMenuPosition(gapPx = 18, marginPx = 48)
    private val window = IntSize(width = 1080, height = 2400)
    private val bar = IntSize(width = 400, height = 144)

    @Test
    fun `the bar sits above the bubble, centred on it`() {
        val bubble = IntRect(left = 60, top = 900, right = 660, bottom = 1000)

        val offset = position.calculatePosition(bubble, window, LayoutDirection.Ltr, bar)

        assertEquals(360 - bar.width / 2, offset.x)
        assertEquals(900 - 144 - 18, offset.y)
    }

    /** The first message of a conversation has nothing above it; the bar goes under it instead. */
    @Test
    fun `a bubble at the top of the screen puts the bar below it`() {
        val bubble = IntRect(left = 60, top = 40, right = 660, bottom = 190)

        val offset = position.calculatePosition(bubble, window, LayoutDirection.Ltr, bar)

        assertEquals(190 + 18, offset.y)
    }

    /** A one-character bubble in the corner is narrower than the bar, which must not leave the window. */
    @Test
    fun `the bar stays inside the window`() {
        val narrowAtTheEdge = IntRect(left = 940, top = 900, right = 1040, bottom = 1000)

        val offset = position.calculatePosition(narrowAtTheEdge, window, LayoutDirection.Ltr, bar)

        assertEquals(window.width - bar.width - 48, offset.x)
    }
}
