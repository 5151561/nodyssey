package io.github.nodyssey.ui.space

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 加入天数 is the site's headline statistic for an account, and the endpoint gives it in two shapes.
 *
 * The expected values are derived from the same clock the code reads, so the test does not go stale a
 * day after it is written.
 */
class JoinedDaysTest {
    private val now = 1_784_000_000_000L
    private val today: LocalDate =
        java.time.Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()

    @Test
    fun `counts days from an ISO instant`() {
        val joined = today.minusDays(143)
        assertEquals(143, joinedDays("${joined}T14:29:22.000Z", now))
    }

    @Test
    fun `counts days from a plain date`() {
        val joined = today.minusDays(7)
        assertEquals(7, joinedDays(joined.toString(), now))
    }

    @Test
    fun `returns null rather than a wrong number for an unusable value`() {
        assertNull(joinedDays(null, now))
        assertNull(joinedDays("", now))
        assertNull(joinedDays("昨天", now))
        assertNull(joinedDays("2026-13-45", now))
    }

    /** A registration date in the future is a clock disagreement, not a negative age. */
    @Test
    fun `never reports a negative age`() {
        assertEquals(0, joinedDays(today.plusDays(3).toString(), now))
    }
}
