package io.github.nodyssey.ui.postlist

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.github.nodyssey.data.Board
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where a dragged pill lands, decided without a Compose tree.
 *
 * The strip lays its pills out in a wrapped grid, so this works off settled slot rectangles and a hit
 * test rather than a scroll axis. The fixture below is that grid flattened to one row of 100dp slots,
 * which is all the geometry the decision actually needs.
 */
class BoardReorderTest {

    private val front = BoardSlot(Board(null, "综合", null), parked = false)
    private val daily = BoardSlot(Board("daily", "日常", null), parked = false)
    private val tech = BoardSlot(Board("tech", "技术", null), parked = false)
    private val trade = BoardSlot(Board("trade", "交易", null), parked = true)

    private val slots = listOf(front, daily, tech, trade)

    private val bounds =
        mapOf(
            FRONT_PAGE_KEY to Rect(0f, 0f, 100f, 40f),
            "daily" to Rect(100f, 0f, 200f, 40f),
            "tech" to Rect(200f, 0f, 300f, 40f),
            "trade" to Rect(300f, 0f, 400f, 40f),
        )

    private fun over(key: String) = bounds.getValue(key).center

    @Test
    fun `dragging a pill onto another takes that slot`() {
        val moved = slots.reorderedFor("tech", over("daily"), bounds)
        assertEquals(listOf(front, tech, daily, trade), moved)
    }

    @Test
    fun `hovering over its own slot changes nothing`() {
        assertNull(slots.reorderedFor("tech", over("tech"), bounds))
    }

    /** Between two pills, or off the end of the last row: nowhere to land, so nothing moves. */
    @Test
    fun `a pointer over no pill at all changes nothing`() {
        assertNull(slots.reorderedFor("tech", Offset(600f, 20f), bounds))
    }

    /** 综合 is the front page, not a board. Nothing may be dropped into its slot. */
    @Test
    fun `the locked front page slot refuses a drop`() {
        assertNull(slots.reorderedFor("tech", over(FRONT_PAGE_KEY), bounds))
    }

    /**
     * Parking is what the corner badge does, deliberately and reversibly. A pill dragged over the
     * parked tail must not park itself as a side effect of where the finger happened to stop.
     */
    @Test
    fun `the parked tail refuses a drop`() {
        assertNull(slots.reorderedFor("tech", over("trade"), bounds))
    }

    @Test
    fun `a drag with no pill held changes nothing`() {
        assertNull(slots.reorderedFor(null, over("daily"), bounds))
    }

    /**
     * A move lands the held pill in the slot the finger is already in. Once the strip has laid itself
     * out again the pointer is therefore over the held pill, and the next drag event finds nothing to
     * swap with — without that the two pills would trade places for as long as the finger sat still.
     *
     * The relaid-out bounds are the point, so the test swaps them the way a layout pass would.
     */
    @Test
    fun `a move settles once the strip has laid out again`() {
        // The finger does not move between the two calls; the pills move under it.
        val finger = over("daily")
        val moved = slots.reorderedFor("tech", finger, bounds)!!
        val relaidOut =
            bounds + mapOf("tech" to bounds.getValue("daily"), "daily" to bounds.getValue("tech"))
        assertNull(moved.reorderedFor("tech", finger, relaidOut))
    }
}
