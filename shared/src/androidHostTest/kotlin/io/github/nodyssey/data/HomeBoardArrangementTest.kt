package io.github.nodyssey.data

import io.github.nodyssey.data.settings.homeBoardArrangement
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The strip's saved arrangement, applied to whatever boards the site currently has.
 *
 * The interesting cases are all disagreements between the two: a ranking written months ago against
 * a board list the API has since changed. None of them may lose a board or invent one.
 */
class HomeBoardArrangementTest {

    private val daily = Board("daily", "日常", null)
    private val tech = Board("tech", "技术", null)
    private val trade = Board("trade", "交易", null)
    private val boards = listOf(daily, tech, trade)

    @Test
    fun `no saved arrangement leaves the api order alone`() {
        val arrangement = homeBoardArrangement(boards, emptyList(), emptySet())
        assertEquals(boards, arrangement.enabled)
        assertEquals(emptyList<Board>(), arrangement.parked)
    }

    @Test
    fun `the saved order is the order`() {
        val arrangement = homeBoardArrangement(boards, listOf("trade", "daily", "tech"), emptySet())
        assertEquals(listOf(trade, daily, tech), arrangement.enabled)
    }

    @Test
    fun `a parked board leaves the enabled half and keeps its rank in the tail`() {
        val arrangement =
            homeBoardArrangement(boards, listOf("trade", "daily", "tech"), setOf("trade", "tech"))
        assertEquals(listOf(daily), arrangement.enabled)
        assertEquals(listOf(trade, tech), arrangement.parked)
    }

    /**
     * A board added to the site after the user last rearranged the strip has no rank. Dropping it
     * would be a board silently going missing, so it is appended and enabled.
     */
    @Test
    fun `a board the ranking has never seen is appended and enabled`() {
        val added = Board("photo-share", "贴图", null)
        val arrangement =
            homeBoardArrangement(
                boards + added,
                order = listOf("trade", "daily", "tech"),
                parked = setOf("trade"),
            )
        assertEquals(listOf(daily, tech, added), arrangement.enabled)
        assertEquals(listOf(trade), arrangement.parked)
    }

    /** The mirror image: a rank for a board the API stopped returning is simply not a board. */
    @Test
    fun `a rank for a board that no longer exists is dropped`() {
        val arrangement =
            homeBoardArrangement(boards, listOf("meaningless", "trade", "daily", "tech"), emptySet())
        assertEquals(listOf(trade, daily, tech), arrangement.enabled)
    }

    /**
     * Parking is stored as a set beside the ranking, so a slug can in principle be parked without
     * being ranked — a half-written store, or a downgrade. It must still park rather than reappear.
     */
    @Test
    fun `a parked board with no rank still parks`() {
        val arrangement = homeBoardArrangement(boards, emptyList(), setOf("tech"))
        assertEquals(listOf(daily, trade), arrangement.enabled)
        assertEquals(listOf(tech), arrangement.parked)
    }
}
