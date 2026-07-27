package io.github.nsreader.notifications

import io.github.nsreader.data.NotificationCounts
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

        val fresh = newlyUnreadCounts(previous, current)

        assertEquals(2, fresh.replies)
        assertEquals(0, fresh.mentions)
        // A total that dropped means things were read elsewhere, not that news arrived.
        assertEquals(0, fresh.messages)
    }

    @Test
    fun `first poll against an empty snapshot reports the full backlog`() {
        val fresh = newlyUnreadCounts(NotificationCounts(), NotificationCounts(replies = 3))

        assertEquals(3, fresh.replies)
        assertEquals(0, fresh.mentions)
        assertEquals(0, fresh.messages)
    }
}
