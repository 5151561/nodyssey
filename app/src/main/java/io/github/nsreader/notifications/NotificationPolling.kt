package io.github.nsreader.notifications

import io.github.nsreader.data.NotificationCounts

/**
 * The decisions the poll worker makes, kept free of Android types so they run as plain JVM tests.
 */

/** Fixed 23:00–07:00 window — board f4 shows the range as copy, not as an editable control. */
const val QUIET_START_MINUTE = 23 * 60
const val QUIET_END_MINUTE = 7 * 60

/** True inside the overnight quiet window. The window wraps midnight, hence the OR. */
fun isInQuietHours(minuteOfDay: Int): Boolean =
    minuteOfDay >= QUIET_START_MINUTE || minuteOfDay < QUIET_END_MINUTE

/**
 * How many unread items appeared since the last poll, per group.
 *
 * Clamped at zero rather than signed: a count that *dropped* means the user read things on the
 * site, which is not an event worth a notification. The unread endpoint reports totals, so a read
 * and a new arrival inside one interval can cancel out — an accepted blind spot; the next arrival
 * still notifies.
 */
fun newlyUnreadCounts(
    previous: NotificationCounts,
    current: NotificationCounts,
): NotificationCounts =
    NotificationCounts(
        replies = (current.replies - previous.replies).coerceAtLeast(0),
        mentions = (current.mentions - previous.mentions).coerceAtLeast(0),
        messages = (current.messages - previous.messages).coerceAtLeast(0),
    )
