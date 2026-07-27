package io.github.nsreader.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import io.github.nsreader.R
import io.github.nsreader.data.NotificationCategory

/**
 * The three Android notification channels, one per site notification group (board f4).
 *
 * A channel per group is the point of the design: the in-app switches decide what the app posts,
 * and the system channels give the user the OS-level override on top. Registration is idempotent,
 * so [ensure] runs on every app start.
 */
object NotificationChannels {
    const val MENTIONS = "mentions"
    const val REPLIES = "replies"
    const val MESSAGES = "messages"

    fun channelId(category: NotificationCategory): String =
        when (category) {
            NotificationCategory.MENTIONS -> MENTIONS
            NotificationCategory.REPLIES -> REPLIES
            NotificationCategory.MESSAGES -> MESSAGES
        }

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                channel(context, MENTIONS, R.string.notifications_mentions),
                channel(context, REPLIES, R.string.notifications_replies),
                channel(context, MESSAGES, R.string.notifications_messages),
            ),
        )
    }

    private fun channel(
        context: Context,
        id: String,
        nameRes: Int,
    ): NotificationChannel =
        NotificationChannel(id, context.getString(nameRes), NotificationManager.IMPORTANCE_DEFAULT)
}
