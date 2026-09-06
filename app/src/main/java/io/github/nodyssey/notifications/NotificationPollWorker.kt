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
import io.github.nodyssey.data.NotificationCounts
import io.github.nodyssey.data.NotificationRepository
import io.github.nodyssey.data.NotificationTab
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

        val fetched =
            try {
                notificationRepository.refreshCounts()
            } catch (e: SiteException) {
                return if (e.error is SiteError.Network) Result.retry() else Result.success()
            }

        val previous = settingsRepository.notificationSeenCounts()
        val counts = withOverlap(previous, fetched) ?: return Result.retry()
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

        if (settings.notifyInteractions) notify(NotificationTab.INTERACTIONS, previous, counts)
        if (settings.notifyMessages) notify(NotificationTab.MESSAGES, previous, counts)
        return Result.success()
    }

    /**
     * The counts with the one number `unread-count` cannot answer: how many of them are one comment.
     *
     * The site files a reply that opens with `@name #7` under both 回复主题 and @我, so two of its
     * unread are one thing to read — and only the lists say which. They are loaded on the runs that
     * could have gained such a pair, which is the runs where both numbers went up; every other run
     * carries the count it already had.
     *
     * Null asks the caller to retry: a merged load that failed on the network is the same transport
     * failure `refreshCounts` retries for. Anything else — a challenge page, a shape we cannot read
     * — falls back to the carried count and notifies with it, because an over-count is a smaller
     * failure than a silent one.
     */
    private suspend fun withOverlap(
        previous: NotificationCounts,
        fetched: NotificationCounts,
    ): NotificationCounts? {
        if (!needsMergedLoad(previous, fetched)) {
            return fetched.copy(overlap = carriedOverlap(previous, fetched))
        }
        return try {
            notificationRepository.interactions()
            fetched.copy(overlap = notificationRepository.counts.value.overlap)
        } catch (e: SiteException) {
            if (e.error is SiteError.Network) null else fetched.copy(overlap = carriedOverlap(previous, fetched))
        }
    }

    @SuppressLint("MissingPermission")
    private fun notify(
        tab: NotificationTab,
        previous: NotificationCounts,
        counts: NotificationCounts,
    ) {
        val count = newlyUnreadCount(previous, counts, tab)
        if (count <= 0) return
        if (!canPostNotifications()) return

        val context = applicationContext
        val openApp =
            PendingIntent.getActivity(
                context,
                tab.ordinal,
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_NOTIFICATIONS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, NotificationChannels.channelId(tab))
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle(context.getString(titleRes(tab)))
                // A quantity string, so English can say "1 new reply" without this file knowing
                // which languages have a singular. `count` twice on purpose: once to choose the
                // form, once to fill the `%1$d` inside it.
                .setContentText(context.resources.getQuantityString(bodyRes(tab), count, count))
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()
        // One notification per group, updated in place — two groups, at most two entries.
        try {
            NotificationManagerCompat.from(context).notify(tab.ordinal, notification)
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

    private fun titleRes(tab: NotificationTab): Int =
        when (tab) {
            NotificationTab.INTERACTIONS -> R.string.notifications_interactions
            NotificationTab.MESSAGES -> R.string.notifications_messages
        }

    @PluralsRes
    private fun bodyRes(tab: NotificationTab): Int =
        when (tab) {
            NotificationTab.INTERACTIONS -> R.plurals.notify_body_interactions
            NotificationTab.MESSAGES -> R.plurals.notify_body_messages
        }
}
