package io.github.nodyssey.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.PluralsRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.nodyssey.MainActivity
import io.github.nodyssey.R
import io.github.nodyssey.data.NotificationCategory
import io.github.nodyssey.data.NotificationCounts
import io.github.nodyssey.data.NotificationRepository
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.plaza.core.AppClock
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

/**
 * The background check behind board f4: the site has no push, so this polls the unread-count
 * endpoint and turns increases into system notifications.
 *
 * Everything that can be wrong quietly — signed out, polling switched off, a Cloudflare wall — ends
 * the run as success: a periodic worker that keeps retrying against a challenge page would be
 * exactly the burst of non-browser traffic the challenge exists to stop. Only transport failures
 * retry, with WorkManager's own backoff.
 *
 * Dependencies arrive through the constructor, built by `NodysseyWorkerFactory`.
 */
class NotificationPollWorker(
    context: Context,
    parameters: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
    private val notificationRepository: NotificationRepository,
    private val clock: AppClock,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val settings = settingsRepository.settings.first()
        if (!settings.notificationsEnabled) return Result.success()
        if (!sessionRepository.peek().isSignedIn) return Result.success()

        val counts =
            try {
                notificationRepository.refreshCounts()
            } catch (e: SiteException) {
                return if (e.error is SiteError.Network) Result.retry() else Result.success()
            }

        val previous = settingsRepository.notificationSeenCounts()
        // Recorded before deciding whether to post, so the quiet window "collects silently":
        // arrivals inside it never turn into a burst of stale notifications at 07:00.
        settingsRepository.setNotificationSeenCounts(counts)

        val minuteOfDay =
            Instant
                .ofEpochMilli(clock.nowMillis())
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .let { it.hour * 60 + it.minute }
        if (settings.notificationQuietHours && isInQuietHours(minuteOfDay)) return Result.success()

        val fresh = newlyUnreadCounts(previous, counts)
        if (settings.notifyMentions) notify(NotificationCategory.MENTIONS, fresh)
        if (settings.notifyReplies) notify(NotificationCategory.REPLIES, fresh)
        if (settings.notifyMessages) notify(NotificationCategory.MESSAGES, fresh)
        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private fun notify(
        category: NotificationCategory,
        fresh: NotificationCounts,
    ) {
        val count = fresh.forCategory(category)
        if (count <= 0) return
        if (!canPostNotifications()) return

        val context = applicationContext
        val openApp =
            PendingIntent.getActivity(
                context,
                category.ordinal,
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_NOTIFICATIONS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, NotificationChannels.channelId(category))
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle(context.getString(titleRes(category)))
                // A quantity string, so English can say "1 new reply" without this file knowing
                // which languages have a singular. `count` twice on purpose: once to choose the
                // form, once to fill the `%1$d` inside it.
                .setContentText(context.resources.getQuantityString(bodyRes(category), count, count))
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()
        // One notification per group, updated in place — three site groups, at most three entries.
        try {
            NotificationManagerCompat.from(context).notify(category.ordinal, notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the explicit check above and this call.
        }
    }

    private fun canPostNotifications(): Boolean {
        val context = applicationContext
        val permitted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun titleRes(category: NotificationCategory): Int =
        when (category) {
            NotificationCategory.MENTIONS -> R.string.notifications_mentions
            NotificationCategory.REPLIES -> R.string.notifications_replies
            NotificationCategory.MESSAGES -> R.string.notifications_messages
        }

    @PluralsRes
    private fun bodyRes(category: NotificationCategory): Int =
        when (category) {
            NotificationCategory.MENTIONS -> R.plurals.notify_body_mentions
            NotificationCategory.REPLIES -> R.plurals.notify_body_replies
            NotificationCategory.MESSAGES -> R.plurals.notify_body_messages
        }
}
