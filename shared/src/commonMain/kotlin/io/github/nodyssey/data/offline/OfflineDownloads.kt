package io.github.nodyssey.data.offline

/**
 * The half of the offline library its background workers drive.
 *
 * Apart from `OfflineLibrary` because the two have different callers: that one is what 收藏 manages
 * downloads through, this is what the background scheduler calls — WorkManager on Android,
 * `BGTaskScheduler` on iOS — and nothing on any screen ever does. A worker looks for it with a cast:
 * the library that ships when offline reading is unavailable does not implement it, and a worker that
 * finds nothing to drive simply finishes.
 */
interface OfflineDownloads {
    /** True when something is queued — what tells a woken worker whether it has a job at all. */
    suspend fun hasQueuedWork(): Boolean

    /** Stored threads the site has moved past, for 自动补新回复. */
    suspend fun staleIds(): List<Long>

    /** Works the queue until it is empty or the network gives out. */
    suspend fun drainQueue(): DrainOutcome

    /** Deletes stored copies older than 离线内容保留, and the pictures nothing refers to any more. */
    suspend fun sweepExpired()
}

/**
 * Why a drain stopped.
 *
 * The distinction is what a worker reports back: an emptied queue is a success, a queue abandoned
 * because the site could not be reached is a retry, and turning the second into the first would
 * leave the reader's downloads sitting still until they opened 收藏 and asked again.
 *
 * [BLOCKED] is the third answer, and it maps to *neither* of those: the site is answering with a
 * challenge or a rate limit, so the queue must stop — every further request is aimed at the same
 * wall — but a scheduler retry would be that same burst on a timer. The rows keep their queued
 * state; the next drain the reader causes (a new download, 重试 after the WebView) picks them up.
 */
enum class DrainOutcome {
    DRAINED,
    NETWORK_FAILED,
    BLOCKED,
}

/**
 * Whether the daily sweep should be tried again — [runOfflineMaintenance]'s answer.
 *
 * The same distinction [DrainOutcome] draws, for the same reason: a run the site could not be reached
 * for is a retry, everything else is done. Each platform maps this to its own scheduler's vocabulary
 * — `Result.retry()` on Android, `setTaskCompleted(success: false)` on iOS.
 */
enum class MaintenanceOutcome {
    COMPLETED,
    NETWORK_FAILED,
}

/**
 * When downloads run. Implemented over WorkManager on Android and `BGTaskScheduler` on iOS; an
 * interface so the engine stays testable and platform-blind.
 *
 * The engine says *that* there is work, never *when* to do it: 仅 Wi-Fi 下载 is a constraint the
 * platform enforces, and a queue that waited on its own timer would be a second, worse scheduler
 * running beside the one the system already has. (Where the platform has no unmetered constraint —
 * iOS — the implementation enforces the Wi-Fi rule itself; that stays the scheduler's problem, not
 * the engine's.)
 */
interface OfflineWorkScheduler {
    /**
     * @param restart replaces a run that is already waiting instead of queuing behind it. Only for a
     * change of 仅 Wi-Fi 下载: a waiting run keeps the constraint it was enqueued with, so switching
     * the setting off would otherwise leave the queue still waiting for a network it no longer needs.
     */
    fun startDrain(
        wifiOnly: Boolean,
        restart: Boolean = false,
    )

    /** Makes sure the daily sweep — 保留期限, and 自动补新回复 when it is on — is scheduled. */
    fun ensureMaintenance()
}
