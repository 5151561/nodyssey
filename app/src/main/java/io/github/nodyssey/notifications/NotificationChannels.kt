package io.github.nodyssey.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import io.github.nodyssey.R
import io.github.nodyssey.data.NotificationTab

/**
 * The two Android notification channels, one per group the app actually shows (board f4).
 *
 * A channel per group is the point of the design: the in-app switches decide what the app posts,
 * and the system channels give the user the OS-level override on top. The grouping is the screen's
 * — [NotificationTab] — and not the site's three, because the site files one comment under both
 * @我 and 回复主题 and two channels would post it twice. Registration is idempotent, so [ensure]
 * runs on every app start.
 */
object NotificationChannels {
    const val INTERACTIONS = "interactions"
    const val MESSAGES = "messages"

    /**
     * What the per-site-group design left on devices that ran it.
     *
     * Deleted rather than left alone: a channel with no notifications behind it still shows up in
     * the system settings list, where two dead entries would read as switches that stopped working.
     * Deleting one also clears whatever it had posted, which is why only [RETIRED_MESSAGES_ID] —
     * the one id that changed channel rather than disappearing — needs cancelling by hand.
     */
    private val RETIRED_CHANNELS = listOf("mentions", "replies")

    /** `NotificationCategory.MESSAGES.ordinal`, the id 私信 used to be posted under. */
    private const val RETIRED_MESSAGES_ID = 2

    fun channelId(tab: NotificationTab): String =
        when (tab) {
            NotificationTab.INTERACTIONS -> INTERACTIONS
            NotificationTab.MESSAGES -> MESSAGES
        }

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                channel(context, INTERACTIONS, R.string.notifications_interactions),
                channel(context, MESSAGES, R.string.notifications_messages),
            ),
        )
        RETIRED_CHANNELS.forEach(manager::deleteNotificationChannel)
        manager.cancel(RETIRED_MESSAGES_ID)
    }

    private fun channel(
        context: Context,
        id: String,
        nameRes: Int,
    ): NotificationChannel =
        NotificationChannel(id, context.getString(nameRes), NotificationManager.IMPORTANCE_DEFAULT)
}
