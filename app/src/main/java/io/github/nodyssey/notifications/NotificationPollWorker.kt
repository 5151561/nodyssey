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
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.nodyssey.MainActivity
import io.github.nodyssey.R
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.BackgroundAlertChecker
import io.github.nodyssey.data.KeywordHit
import io.github.nodyssey.data.NotificationCounts
import io.github.nodyssey.data.NotificationRepository
import io.github.nodyssey.data.NotificationTab
import io.github.nodyssey.data.TrackedThreadUpdate
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.settings.UserSettings
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
    private val alerts: BackgroundAlertChecker,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val settings = settingsRepository.settings.first()
        if (!settings.notificationsEnabled) return Result.success()

        val minuteOfDay =
            Instant
                .ofEpochMilli(clock.nowMillis())
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .let { it.hour * 60 + it.minute }
        // Every check below still runs and records inside the quiet window — it "collects silently",
        // so arrivals there never turn into a burst of stale notifications at 07:00.
        val quiet = settings.notificationQuietHours && isInQuietHours(minuteOfDay)

        // The site's own notifications need an account; 提醒关键词 and 追踪新回复 read public lists
        // and do not, so a signed-out reader still gets those.
        var retry = false
        if (sessionRepository.peek().isSignedIn) retry = !checkSiteNotifications(settings, quiet)
        if (settings.notifyKeywordAlerts) {
            val hits = alertsOrRetry { alerts.checkKeywords() }
            if (hits == null) {
                retry = true
            } else if (!quiet) {
                hits.take(MAX_KEYWORD_NOTIFICATIONS).forEach(::notifyKeywordHit)
            }
        }
        if (settings.notifyTrackedThreads) {
            val updates = alertsOrRetry { alerts.checkTrackedThreads() }
            if (updates == null) {
                retry = true
            } else if (!quiet) {
                updates.forEach(::notifyTrackedThread)
            }
        }
        return if (retry) Result.retry() else Result.success()
    }

    /**
     * [check]'s answer, an empty one for anything that is not about the network — a challenge page
     * retried every quarter hour is the traffic the challenge exists to stop — or null to retry.
     */
    private suspend fun <T> alertsOrRetry(check: suspend () -> List<T>): List<T>? =
        try {
            check()
        } catch (e: SiteException) {
            if (e.error is SiteError.Network) null else emptyList()
        }

    /** The unread-count half; false asks for a retry. */
    private suspend fun checkSiteNotifications(
        settings: UserSettings,
        quiet: Boolean,
    ): Boolean {
        val fetched =
            try {
                notificationRepository.refreshCounts()
            } catch (e: SiteException) {
                return e.error !is SiteError.Network
            }

        val previous = settingsRepository.notificationSeenCounts()
        val counts = withOverlap(previous, fetched) ?: return false
        settingsRepository.setNotificationSeenCounts(counts)
        if (quiet) return true

        if (settings.notifyInteractions) notify(NotificationTab.INTERACTIONS, previous, counts)
        if (settings.notifyMessages) notify(NotificationTab.MESSAGES, previous, counts)
        return true
    }

    private fun notifyKeywordHit(hit: KeywordHit) =
        postThreadNotification(
            channel = NotificationChannels.KEYWORD_ALERTS,
            tag = "keyword-${hit.postId}",
            postId = hit.postId,
            title = applicationContext.getString(R.string.notify_keyword_title, hit.keyword),
            body = hit.title,
        )

    private fun notifyTrackedThread(update: TrackedThreadUpdate) =
        postThreadNotification(
            channel = NotificationChannels.TRACKED_THREADS,
            // Tagged by thread, so a second batch of replies updates the same entry.
            tag = "thread-${update.postId}",
            postId = update.postId,
            title = update.title,
            body = applicationContext.resources.getQuantityString(
                R.plurals.notify_body_tracked_thread,
                update.newReplies,
                update.newReplies,
            ),
        )

    /** One notification about one thread; tapping it opens that thread through the deep-link route. */
    @SuppressLint("MissingPermission")
    private fun postThreadNotification(
        channel: String,
        tag: String,
        postId: Long,
        title: String,
        body: String,
    ) {
        if (!canPostNotifications()) return
        val context = applicationContext
        val openThread =
            PendingIntent.getActivity(
                context,
                tag.hashCode(),
                Intent(Intent.ACTION_VIEW, (NodeSeekSite.BASE_URL + NodeSeekSite.postPath(postId)).toUri())
                    .setClass(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, channel)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(openThread)
                .setAutoCancel(true)
                .build()
        try {
            NotificationManagerCompat.from(context).notify(tag, THREAD_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the explicit check and this call.
        }
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

    private companion object {
        /** The id under each thread's tag; tags keep them apart, and away from the tabs' ids 0 and 1. */
        const val THREAD_NOTIFICATION_ID = 100

        /** A burst of matches is a sign the keyword is too broad, not a reason to fill the shade. */
        const val MAX_KEYWORD_NOTIFICATIONS = 5
    }

    @PluralsRes
    private fun bodyRes(tab: NotificationTab): Int =
        when (tab) {
            NotificationTab.INTERACTIONS -> R.plurals.notify_body_interactions
            NotificationTab.MESSAGES -> R.plurals.notify_body_messages
        }
}
