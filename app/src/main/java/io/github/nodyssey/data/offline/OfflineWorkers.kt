package io.github.nodyssey.data.offline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.nodyssey.NodysseyApp
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.flow.first

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
 * Both live in one worker because both are the same errand — a look over what is stored, once a
 * day, on a device nobody may have opened 收藏 on — and because the sweep must run whether or not
 * the sync does.
 *
 * The sync is deliberately not a re-download of everything. It asks the collection list what the
 * site says each thread's reply count is now, hands those numbers to the library, and queues only
 * the threads that have actually moved; the download engine then re-fetches from each one's last
 * stored page rather than from the top.
 */
class OfflineMaintenanceWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as NodysseyApp).container
        val library = container.offlineLibrary
        val downloads = library as? OfflineDownloads ?: return Result.success()

        downloads.sweepExpired()

        if (!library.settings.first().autoSyncReplies) return Result.success()
        // Collections are the signed-in account's own list; asking for them signed out is a request
        // that can only be refused, and a periodic one at that.
        if (!container.sessionRepository.peek().isSignedIn) return Result.success()

        val counts = mutableMapOf<Long, Int>()
        var page = 1
        while (page <= MAX_COLLECTION_PAGES) {
            val result =
                try {
                    container.userSpaceRepository.collections(page)
                } catch (e: SiteException) {
                    // Same rule as 通知轮询: only a transport failure is worth retrying. A challenge
                    // page answered with more background requests is what the challenge is for.
                    return if (e.error is SiteError.Network) Result.retry() else Result.success()
                }
            result.items.forEach { post -> post.commentCount?.let { counts[post.postId] = it } }
            if (!result.hasNextPage || result.items.isEmpty()) break
            page++
        }
        library.noteReplyCounts(counts)

        val stale = downloads.staleIds()
        if (stale.isNotEmpty()) library.download(stale)
        return Result.success()
    }

    private companion object {
        /** The same bound 收藏 walks the list under, and for the same reason: it exists. */
        const val MAX_COLLECTION_PAGES = 20
    }
}
