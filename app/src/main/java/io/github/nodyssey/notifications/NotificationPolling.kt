package io.github.nodyssey.notifications

import io.github.nodyssey.data.NotificationCounts
import io.github.nodyssey.data.NotificationTab

// The decisions the poll worker makes stay free of Android types so they run as plain JVM tests.

/** Fixed 23:00–07:00 window — board f4 shows the range as copy, not as an editable control. */
const val QUIET_START_MINUTE = 23 * 60
const val QUIET_END_MINUTE = 7 * 60

/** True inside the overnight quiet window. The window wraps midnight, hence the OR. */
fun isInQuietHours(minuteOfDay: Int): Boolean =
    minuteOfDay >= QUIET_START_MINUTE || minuteOfDay < QUIET_END_MINUTE

/**
 * How many unread items appeared since the last poll, in the group the screen shows.
 *
 * Per [NotificationTab] rather than per site group, because that is what one notification stands
 * for: 回复主题 and @我 hold the same comment twice, and counting them separately is what used to
 * post two notifications about one reply. [NotificationCounts.interactions] is that pair already
 * discounted, so the subtraction here is between two totals that both mean "things to read".
 *
 * Clamped at zero rather than signed: a count that *dropped* means the user read things on the
 * site, which is not an event worth a notification. The unread endpoint reports totals, so a read
 * and a new arrival inside one interval can cancel out — an accepted blind spot; the next arrival
 * still notifies.
 */
fun newlyUnreadCount(
    previous: NotificationCounts,
    current: NotificationCounts,
    tab: NotificationTab,
): Int = (current.forTab(tab) - previous.forTab(tab)).coerceAtLeast(0)

/**
 * The pair count to keep when the merged list was not loaded this run.
 *
 * `unread-count` answers per site group and knows nothing about which of them are the same comment,
 * so a run that did not load the two lists has no new answer — and dropping the number to zero
 * would make every such run read the pairs it already knew about as fresh arrivals. Carried, then,
 * but never past what the new totals can hold: a pair is one unread row in each group, so reading
 * some of them on the site has to take the pairs down with them.
 */
fun carriedOverlap(
    previous: NotificationCounts,
    current: NotificationCounts,
): Int = previous.overlap.coerceIn(0, minOf(current.replies, current.mentions))

/**
 * Whether this run has to load the two lists to know what the counts mean.
 *
 * A pair is one unread row in *each* group, so a new pair shows up as both numbers going up. When
 * only one of them moved, whatever pairs exist are the ones already counted — and the merged load,
 * two requests the poll would otherwise not make, can be skipped.
 */
fun needsMergedLoad(
    previous: NotificationCounts,
    current: NotificationCounts,
): Boolean = current.replies > previous.replies && current.mentions > previous.mentions
