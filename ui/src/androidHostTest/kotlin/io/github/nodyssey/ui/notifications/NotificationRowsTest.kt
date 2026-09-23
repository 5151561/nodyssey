package io.github.nodyssey.ui.notifications

import io.github.nodyssey.data.ForumNotification
import io.github.nodyssey.data.NotificationCategory
import io.github.nodyssey.data.NotificationSource
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

/** 5a's day headings: which card each interaction lands in, and which slice of it it draws. */
class NotificationRowsTest {
    private val zone = TimeZone.UTC

    /** 2026-07-26 10:00 UTC. */
    private val now = 1_785_060_000_000L

    private fun item(
        id: String,
        millis: Long?,
    ) = ForumNotification(
        id = id,
        sources = listOf(NotificationSource(NotificationCategory.REPLIES, id.toLong(), isUnread = true)),
        commentId = null,
        postId = 1,
        floor = null,
        actorUid = 1,
        actorName = "a",
        avatarUrl = null,
        threadTitle = null,
        createdAtMillis = millis,
        createdAtText = null,
    )

    @Test
    fun `today and earlier each get a heading and one card`() {
        val rows =
            notificationRows(
                listOf(item("1", now - 60_000L), item("2", now - 2 * HOUR), item("3", now - 30 * HOUR)),
                now,
                zone,
            )

        assertEquals(
            listOf(
                NotificationListRow.Day(NotificationDay.TODAY),
                NotificationListRow.Item(item("1", now - 60_000L), first = true, last = false),
                NotificationListRow.Item(item("2", now - 2 * HOUR), first = false, last = true),
                NotificationListRow.Day(NotificationDay.EARLIER),
                NotificationListRow.Item(item("3", now - 30 * HOUR), first = true, last = true),
            ),
            rows,
        )
    }

    /** Calendar days, not 24 hours: 00:30 today is 今天 even though it is nearly ten hours back. */
    @Test
    fun `just after midnight is still today`() {
        val rows = notificationRows(listOf(item("1", now - 9 * HOUR - 30 * 60_000L)), now, zone)

        assertEquals(NotificationListRow.Day(NotificationDay.TODAY), rows.first())
    }

    /** A row with no time has no day to claim, so it does not get to sit among the new ones. */
    @Test
    fun `a row with no parsable time goes to earlier`() {
        val rows = notificationRows(listOf(item("1", null)), now, zone)

        assertEquals(NotificationListRow.Day(NotificationDay.EARLIER), rows.first())
    }

    private companion object {
        const val HOUR = 60 * 60_000L
    }
}
