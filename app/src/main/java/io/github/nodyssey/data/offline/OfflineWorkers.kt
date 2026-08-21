package io.github.nodyssey.data.offline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.nodyssey.NodysseyApp

/**
 * Works the download queue until it is empty.
 *
 * Holds no state of its own — the queue is rows in Room — so being killed mid-thread costs the
 * pages of one thread and nothing else, and the next run picks the same row up again.
 */
class OfflineDownloadWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val downloads = (applicationContext as NodysseyApp).container.offlineLibrary as? OfflineDownloads
            ?: return Result.success()
        return when (downloads.drainQueue()) {
            DrainOutcome.DRAINED -> Result.success()

            // The one failure worth WorkManager's backoff. Everything else that can go wrong with a
            // download is about that thread, is already recorded on its row, and would answer the
            // same way however many times it were asked.
            DrainOutcome.NETWORK_FAILED -> Result.retry()
        }
    }
}

/**
 * The daily housekeeping: 离线内容保留, and 自动补新回复 when it is switched on.
 *
 * The errand itself is [runOfflineMaintenance] in `commonMain`, because it is the same on iOS, where
 * `BGTaskScheduler` runs it. All this worker adds is WorkManager's vocabulary: an emptied sweep is a
 * success, a sync the site could not be reached for is a retry.
 */
class OfflineMaintenanceWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as NodysseyApp).container
        val library = container.offlineLibrary
        val downloads = library as? OfflineDownloads ?: return Result.success()

        return when (
            runOfflineMaintenance(library, downloads, container.sessionRepository, container.userSpaceRepository)
        ) {
            MaintenanceOutcome.COMPLETED -> Result.success()
            MaintenanceOutcome.NETWORK_FAILED -> Result.retry()
        }
    }
}
