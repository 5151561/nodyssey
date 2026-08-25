package io.github.nodyssey

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import io.github.nodyssey.data.offline.OfflineDownloadWorker
import io.github.nodyssey.data.offline.OfflineDownloads
import io.github.nodyssey.data.offline.OfflineMaintenanceWorker
import io.github.nodyssey.di.AndroidAppContainer
import io.github.nodyssey.notifications.NotificationPollWorker

/**
 * Hands each worker its dependencies out of the container, which is what makes a worker a plain
 * constructor-injected class a test can build with fakes.
 *
 * Before this factory existed every worker began with `applicationContext as NodysseyApp` — a cast
 * the compiler cannot check and a test cannot satisfy, which is why the workers had no tests. The
 * container is taken as a function rather than a value for the same reason the cast used to be
 * inside `doWork`: this factory is built inside `workManagerConfiguration`, and the container it
 * reads from is assigned in `onCreate` — asking at [createWorker] time keeps the order of those two
 * a fact WorkManager owns rather than one this class has to be lucky about.
 */
class NodysseyWorkerFactory(
    private val container: () -> AndroidAppContainer,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        val container = container()
        return when (workerClassName) {
            OfflineDownloadWorker::class.qualifiedName ->
                OfflineDownloadWorker(
                    appContext,
                    workerParameters,
                    // `as?`, and the null genuinely means something: a library built without offline
                    // reading does not implement the download half, and a worker holding null
                    // finishes with nothing to do — see the note on [OfflineDownloads].
                    container.offlineLibrary as? OfflineDownloads,
                )

            OfflineMaintenanceWorker::class.qualifiedName ->
                OfflineMaintenanceWorker(
                    appContext,
                    workerParameters,
                    container.offlineLibrary,
                    container.offlineLibrary as? OfflineDownloads,
                    container.sessionRepository,
                    container.userSpaceRepository,
                )

            NotificationPollWorker::class.qualifiedName ->
                NotificationPollWorker(
                    appContext,
                    workerParameters,
                    container.settingsRepository,
                    container.sessionRepository,
                    container.notificationRepository,
                    container.clock,
                )

            // Not an error: null sends WorkManager down its reflective default path, which is the
            // right answer for a worker some library enqueued for itself.
            else -> null
        }
    }
}
