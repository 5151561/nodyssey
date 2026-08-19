package io.github.nodyssey.data.offline

/**
 * The half of the offline library its background workers drive.
 *
 * Apart from `OfflineLibrary` because the two have different callers: that one is what 收藏 manages
 * downloads through, this is what WorkManager calls and nothing on any screen ever does. A worker
 * looks for it with a cast — the library that ships when offline reading is unavailable does not
 * implement it, and a worker that finds nothing to drive simply finishes.
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
 */
enum class DrainOutcome {
    DRAINED,
    NETWORK_FAILED,
}

/**
 * When downloads run. Implemented over WorkManager; an interface so the engine stays testable.
 *
 * The engine says *that* there is work, never *when* to do it: 仅 Wi-Fi 下载 is a constraint the
 * platform enforces, and a queue that waited on its own timer would be a second, worse scheduler
 * running beside the one the system already has.
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
