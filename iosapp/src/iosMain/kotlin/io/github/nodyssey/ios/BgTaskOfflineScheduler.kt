@file:OptIn(ExperimentalForeignApi::class)

package io.github.nodyssey.ios

import io.github.nodyssey.data.offline.DrainOutcome
import io.github.nodyssey.data.offline.MaintenanceOutcome
import io.github.nodyssey.data.offline.OfflineDownloads
import io.github.nodyssey.data.offline.OfflineWorkScheduler
import io.github.nodyssey.data.offline.runOfflineMaintenance
import io.github.nodyssey.di.AppContainer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.BackgroundTasks.BGProcessingTask
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Network.nw_interface_type_wifi
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_uses_interface_type
import platform.darwin.dispatch_queue_create
import kotlin.coroutines.resume

/**
 * [OfflineWorkScheduler] on `BGTaskScheduler` — the iOS counterpart of `WorkManagerOfflineScheduler`.
 *
 * This is step D4. Until it, `NoOfflineWorkScheduler` did nothing, and because the drain is only ever
 * triggered *through* the scheduler (the engine says "there is work", never "run now"), an iOS build
 * queued downloads that nothing ever worked off. So this class does two jobs WorkManager rolls into
 * one, and the difference from Android is worth naming:
 *
 * - **The foreground drain.** WorkManager runs its one-time work almost immediately while the app is
 *   on screen; the closest iOS has is doing it ourselves, so [startDrain] launches the drain on
 *   [scope] there and then. This is what actually moves bytes in the common case — and the only path
 *   that runs at all on the Simulator, where `BGTaskScheduler` is unavailable.
 * - **The background continuation.** [startDrain] and [ensureMaintenance] also submit
 *   `BGProcessingTaskRequest`s, so the system relaunches the app to finish a queue too large for one
 *   foreground session, or to run the daily sweep on a device 收藏 has not been opened on for a
 *   fortnight. [register] must have installed the handlers first — see its note on launch timing.
 *
 * **仅 Wi-Fi 下载 cannot be a request constraint here.** WorkManager has `NetworkType.UNMETERED`;
 * `BGProcessingTaskRequest` has only `requiresNetworkConnectivity`, which is "any network". So the
 * Wi-Fi rule is enforced where the download actually happens instead — [networkAllows] checks the
 * live path before every drain, foreground or background, and a run that finds itself on cellular
 * with the setting on simply does nothing and lets a later one try, which is the same waiting
 * WorkManager does, moved one layer in.
 *
 * @param scope the app's process-lifetime scope, dispatched to the main thread. The drain itself
 *   switches to IO inside `RoomOfflineLibrary`; this scope only ever holds the coroutine's shell and
 *   serialises access to [foregroundDrain].
 * @param container resolves the graph, building it if a background launch got here before any scene.
 *   Suspends because that build waits on WebKit for the user agent — see `NodysseyApp`.
 */
class BgTaskOfflineScheduler(
    private val scope: CoroutineScope,
    private val container: suspend () -> AppContainer?,
) : OfflineWorkScheduler {
    private var registered = false
    private var foregroundDrain: Job? = null

    /**
     * Installs the two launch handlers. **Must run before the app finishes launching** — iOS traps
     * if a handler is registered afterwards — so `AppDelegate` calls this from
     * `didFinishLaunchingWithOptions`, before any container exists. The handlers close over
     * [container] and resolve it only when a task actually fires, which is much later.
     */
    fun register() {
        if (registered) return
        registered = true
        val scheduler = BGTaskScheduler.sharedScheduler
        scheduler.registerForTaskWithIdentifier(DRAIN_TASK_ID, null) { task -> onDrainTask(task as? BGProcessingTask) }
        scheduler.registerForTaskWithIdentifier(MAINTENANCE_TASK_ID, null) { task ->
            onMaintenanceTask(task as? BGProcessingTask)
        }
    }

    /**
     * Called once the app is on screen — the iOS counterpart of what the Android `NodysseyApp` does
     * in `onCreate`: schedule the daily sweep, and pick up a queue left behind by a past run, since
     * iOS keeps no persisted one-time work the way WorkManager does.
     */
    fun onLaunch() {
        ensureMaintenance()
        scope.launch {
            val library = container()?.offlineLibrary ?: return@launch
            val downloads = library as? OfflineDownloads ?: return@launch
            if (downloads.hasQueuedWork()) startDrain(library.settings.first().wifiOnly)
        }
    }

    override fun startDrain(
        wifiOnly: Boolean,
        restart: Boolean,
    ) {
        scope.launch {
            if (restart) {
                foregroundDrain?.cancel()
                foregroundDrain = null
            }
            if (foregroundDrain?.isActive != true) {
                foregroundDrain =
                    scope.launch {
                        if (networkAllows(wifiOnly)) {
                            (container()?.offlineLibrary as? OfflineDownloads)?.drainQueue()
                        }
                    }
            }
        }
        // A background continuation in case the foreground run is cut off by the app leaving the
        // screen. requiresNetworkConnectivity only; the Wi-Fi rule is re-checked in the handler.
        submit(BGProcessingTaskRequest(DRAIN_TASK_ID).apply { requiresNetworkConnectivity = true })
    }

    override fun ensureMaintenance() {
        submit(
            BGProcessingTaskRequest(MAINTENANCE_TASK_ID).apply {
                requiresNetworkConnectivity = true
                earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(MAINTENANCE_INTERVAL_SECONDS)
            },
        )
    }

    // --- background handlers ---------------------------------------------------------------------

    private fun onDrainTask(task: BGProcessingTask?) {
        task ?: return
        // Chain the next run before working: a queue bigger than one window's budget then continues
        // rather than stalling until the app is next opened.
        submit(BGProcessingTaskRequest(DRAIN_TASK_ID).apply { requiresNetworkConnectivity = true })
        runBackgroundTask(task) {
            val library = container()?.offlineLibrary ?: return@runBackgroundTask true
            val downloads = library as? OfflineDownloads ?: return@runBackgroundTask true
            // On cellular with 仅 Wi-Fi 下载 on: nothing to do now, and reporting success keeps the
            // system's opinion of this task healthy. The chained request above tries again later.
            if (!networkAllows(library.settings.first().wifiOnly)) {
                true
            } else {
                downloads.drainQueue() == DrainOutcome.DRAINED
            }
        }
    }

    private fun onMaintenanceTask(task: BGProcessingTask?) {
        task ?: return
        ensureMaintenance() // reschedule tomorrow's sweep
        runBackgroundTask(task) {
            val graph = container() ?: return@runBackgroundTask true
            val library = graph.offlineLibrary
            val downloads = library as? OfflineDownloads ?: return@runBackgroundTask true
            runOfflineMaintenance(
                library,
                downloads,
                graph.sessionRepository,
                graph.userSpaceRepository,
            ) == MaintenanceOutcome.COMPLETED
        }
    }

    /**
     * Runs one background task's work and closes it out exactly once.
     *
     * `invokeOnCompletion` rather than a call at the end of the coroutine because the expiration
     * handler can cancel the job mid-drain: routing both the normal finish and the cancellation
     * through completion means `setTaskCompletedWithSuccess` is called once either way, with `false`
     * when the system pulled the budget out from under it.
     */
    private fun runBackgroundTask(
        task: BGTask,
        work: suspend () -> Boolean,
    ) {
        var success = false
        val job = scope.launch { success = work() }
        job.invokeOnCompletion { cause -> task.setTaskCompletedWithSuccess(cause == null && success) }
        task.expirationHandler = { job.cancel() }
    }

    private fun submit(request: BGProcessingTaskRequest) {
        request.requiresExternalPower = false
        // `error = null` drops the reason: submit fails on the Simulator (BGTaskScheduler is
        // unavailable there) and when the system declines to queue more, and neither is worth
        // surfacing — the foreground drain is what moves bytes, this only asks iOS to keep going off
        // screen. The `try` guards the ObjC exception the call can still raise for a bad identifier.
        try {
            BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error = null)
        } catch (t: Throwable) {
            // See above: a refused background request never blocks the reader.
        }
    }

    // --- the Wi-Fi gate --------------------------------------------------------------------------

    private suspend fun networkAllows(wifiOnly: Boolean): Boolean = !wifiOnly || onWifi()

    /**
     * Whether the current default path runs over Wi-Fi.
     *
     * `NWPathMonitor` fires its update handler once with the current path as soon as it starts, so a
     * one-shot read is a matter of taking that first callback and cancelling. Off Wi-Fi — cellular,
     * or no network at all — this is false, and a Wi-Fi-only drain then waits, which for a device
     * with nothing downloadable to reach is also the right answer.
     */
    private suspend fun onWifi(): Boolean =
        suspendCancellableCoroutine { continuation ->
            val monitor =
                nw_path_monitor_create() ?: run {
                    continuation.resume(false)
                    return@suspendCancellableCoroutine
                }
            nw_path_monitor_set_update_handler(monitor) { path ->
                val wifi = path != null && nw_path_uses_interface_type(path, nw_interface_type_wifi)
                nw_path_monitor_cancel(monitor)
                if (continuation.isActive) continuation.resume(wifi)
            }
            nw_path_monitor_set_queue(monitor, dispatch_queue_create("io.github.nodyssey.offline.path", null))
            continuation.invokeOnCancellation { nw_path_monitor_cancel(monitor) }
            nw_path_monitor_start(monitor)
        }

    private companion object {
        /** Both must appear in `Info.plist`'s `BGTaskSchedulerPermittedIdentifiers`, or submit throws. */
        const val DRAIN_TASK_ID = "io.github.nodyssey.offline.drain"
        const val MAINTENANCE_TASK_ID = "io.github.nodyssey.offline.maintenance"

        /** A day, like the Android periodic sweep. iOS treats it as "no earlier than", not "exactly". */
        const val MAINTENANCE_INTERVAL_SECONDS = 24.0 * 60.0 * 60.0
    }
}
