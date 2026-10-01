package io.github.nodyssey.data.offline

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Where offline downloads meet the platform's own scheduler.
 *
 * Two pieces of work and no timers of our own:
 *
 * - **The drain** is one-time work, enqueued whenever something is added to the queue. Its network
 *   constraint is 仅 Wi-Fi 下载, which is why the setting takes effect on already-waiting downloads
 *   only by re-enqueuing them — see [startDrain]'s `restart`.
 * - **The sweep** is periodic work every six hours, always scheduled. 保留期限 has to run on a device nobody
 *   has opened 收藏 on for a fortnight, which is precisely the case a check at screen-open misses.
 */
object OfflineWork {
    const val DRAIN_WORK = "offline-download"
    const val MAINTENANCE_WORK = "offline-maintenance"
    private const val MAINTENANCE_INTERVAL_HOURS = 6L

    fun startDrain(
        context: Context,
        wifiOnly: Boolean,
        restart: Boolean,
    ) {
        val request =
            OneTimeWorkRequestBuilder<OfflineDownloadWorker>()
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .build(),
                ).build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            DRAIN_WORK,
            // Appending rather than keeping, because the queue is in Room and the worker reads it
            // once: a thread added in the instant between the running drain's last look and its exit
            // would sit there until something else woke the worker. An appended run that finds the
            // queue already empty costs one COUNT query.
            //
            // Except when the constraint itself changed. A run already waiting for Wi-Fi keeps
            // waiting for Wi-Fi, so turning the setting off has to replace it rather than queue a
            // second run behind a first that will not start.
            if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    fun ensureMaintenance(context: Context) {
        val request =
            // Every six hours, so a collected thread catches up within the morning rather than by
            // tomorrow. Any connection, not 仅 Wi-Fi: the walk itself is a few list pages, and the
            // downloads it queues already wait for Wi-Fi through the drain's own constraint. An
            // UNMETERED walk would also never run behind a VPN that drops NOT_METERED.
            PeriodicWorkRequestBuilder<OfflineMaintenanceWorker>(MAINTENANCE_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                ).build()
        // UPDATE, so an unchanged schedule keeps its place in the day rather than restarting the
        // countdown on every launch — the same reason 通知轮询 uses it.
        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(MAINTENANCE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

/** [OfflineWorkScheduler] over [OfflineWork]; the engine holds this and never a `Context`. */
class WorkManagerOfflineScheduler(
    context: Context,
) : OfflineWorkScheduler {
    private val appContext = context.applicationContext

    override fun startDrain(
        wifiOnly: Boolean,
        restart: Boolean,
    ) = OfflineWork.startDrain(appContext, wifiOnly, restart)

    override fun ensureMaintenance() = OfflineWork.ensureMaintenance(appContext)
}
