package io.github.nodyssey.notifications

import io.github.nodyssey.data.NotificationCounts
import io.github.nodyssey.data.NotificationTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPollingTest {
    @Test
    fun `quiet window covers the overnight wrap`() {
        assertTrue(isInQuietHours(23 * 60))
        assertTrue(isInQuietHours(0))
        assertTrue(isInQuietHours(6 * 60 + 59))
        assertFalse(isInQuietHours(7 * 60))
        assertFalse(isInQuietHours(12 * 60))
        assertFalse(isInQuietHours(22 * 60 + 59))
    }

    @Test
    fun `only increases count as newly unread`() {
        val previous = NotificationCounts(replies = 2, mentions = 1, messages = 5)
        val current = NotificationCounts(replies = 4, mentions = 1, messages = 3)

        assertEquals(2, newlyUnreadCount(previous, current, NotificationTab.INTERACTIONS))
        // A total that dropped means things were read elsewhere, not that news arrived.
        assertEquals(0, newlyUnreadCount(previous, current, NotificationTab.MESSAGES))
    }

    @Test
    fun `first poll against an empty snapshot reports the full backlog`() {
        val current = NotificationCounts(replies = 3)

        assertEquals(3, newlyUnreadCount(NotificationCounts(), current, NotificationTab.INTERACTIONS))
        assertEquals(0, newlyUnreadCount(NotificationCounts(), current, NotificationTab.MESSAGES))
    }

    @Test
    fun `a comment filed under both groups counts once`() {
        // One reply that opens with `@name #7`: the server raised it in each group, so both numbers
        // went up — and the merged load found the two rows are one comment.
        val previous = NotificationCounts()
        val current = NotificationCounts(replies = 1, mentions = 1, overlap = 1)

        assertEquals(1, newlyUnreadCount(previous, current, NotificationTab.INTERACTIONS))
    }

    @Test
    fun `both groups growing is what asks for the lists`() {
        val previous = NotificationCounts(replies = 1, mentions = 1)

        assertTrue(needsMergedLoad(previous, NotificationCounts(replies = 2, mentions = 2)))
        // A reply that named nobody, or an @ that replied to nothing: no new pair either way.
        assertFalse(needsMergedLoad(previous, NotificationCounts(replies = 2, mentions = 1)))
        assertFalse(needsMergedLoad(previous, NotificationCounts(replies = 1, mentions = 2)))
    }

    @Test
    fun `a carried pair count cannot outlive the rows it was counting`() {
        val previous = NotificationCounts(replies = 3, mentions = 2, overlap = 2)

        // Nothing read: the pairs the last merged load found are still there.
        assertEquals(2, carriedOverlap(previous, NotificationCounts(replies = 3, mentions = 2)))
        // Read on the site down to one @: at most one of them can still be a pair, and a stale 2
        // would hide the next arrival by making the 互动 total look one lower than it is.
        assertEquals(1, carriedOverlap(previous, NotificationCounts(replies = 3, mentions = 1)))
        assertEquals(0, carriedOverlap(previous, NotificationCounts()))
    }
}
