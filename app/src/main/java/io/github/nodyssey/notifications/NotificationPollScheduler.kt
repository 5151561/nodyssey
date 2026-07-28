package io.github.nodyssey.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.nodyssey.data.settings.UserSettings
import java.util.concurrent.TimeUnit

/**
 * Keeps the periodic poll in step with the settings SSOT.
 *
 * [NodysseyApp] collects the settings flow and calls [sync] on every change to a field named in
 * [PollSpec] — nothing else schedules work, so the worker can never disagree with the settings
 * screen about whether polling is on.
 */
object NotificationPollScheduler {
    private const val WORK_NAME = "notification-poll"

    /** The subset of [UserSettings] that changes the schedule; the rest the worker reads per run. */
    data class PollSpec(
        val enabled: Boolean,
        val intervalMinutes: Int,
        val wifiOnly: Boolean,
    )

    fun specOf(settings: UserSettings) =
        PollSpec(
            enabled = settings.notificationsEnabled,
            intervalMinutes = settings.notificationPollMinutes,
            wifiOnly = settings.notificationsWifiOnly,
        )

    fun sync(
        context: Context,
        spec: PollSpec,
    ) {
        val workManager = WorkManager.getInstance(context)
        if (!spec.enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(
                    if (spec.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
                ).build()
        val request =
            PeriodicWorkRequestBuilder<NotificationPollWorker>(
                spec.intervalMinutes.toLong(),
                TimeUnit.MINUTES,
            ).setConstraints(constraints)
                .build()
        // UPDATE rather than REPLACE: an unchanged spec keeps its place in the period instead of
        // restarting the countdown on every app launch.
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
