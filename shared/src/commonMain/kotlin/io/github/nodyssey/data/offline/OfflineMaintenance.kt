package io.github.nodyssey.data.offline

import io.github.nodyssey.data.OfflineLibrary
import io.github.nodyssey.data.UserSpaceRepository
import io.github.nodyssey.data.session.SessionRepository
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.flow.first

/**
 * The daily housekeeping, once — 离线内容保留 and 自动补新回复 — with nothing platform-specific in it.
 *
 * It lived in `:app`'s `OfflineMaintenanceWorker` while iOS had no scheduler at all. It is here now
 * because the errand is the same on both platforms and only the trigger differs: WorkManager runs it
 * once a day on Android, `BGTaskScheduler` does on iOS, and both call this. Duplicating it would be
 * two copies of the same 20-page walk, the same signed-out short-circuit and the same
 * network-vs-anything-else retry rule — a place for the two to drift.
 *
 * The sweep runs unconditionally; the sync is skipped unless 自动补新回复 is on and someone is signed
 * in, because collections are the account's own list and asking for them signed out is a request that
 * can only be refused.
 */
suspend fun runOfflineMaintenance(
    library: OfflineLibrary,
    downloads: OfflineDownloads,
    sessionRepository: SessionRepository,
    userSpaceRepository: UserSpaceRepository,
): MaintenanceOutcome {
    downloads.sweepExpired()

    if (!library.settings.first().autoSyncReplies) return MaintenanceOutcome.COMPLETED
    if (!sessionRepository.peek().isSignedIn) return MaintenanceOutcome.COMPLETED

    val counts = mutableMapOf<Long, Int>()
    var page = 1
    while (page <= MAX_COLLECTION_PAGES) {
        val result =
            try {
                userSpaceRepository.collections(page)
            } catch (e: SiteException) {
                // Same rule as the drain and 通知轮询: only a transport failure is worth another run.
                // A challenge page answered with more background requests is what the challenge is for.
                return if (e.error is SiteError.Network) MaintenanceOutcome.NETWORK_FAILED else MaintenanceOutcome.COMPLETED
            }
        result.items.forEach { post -> post.commentCount?.let { counts[post.postId] = it } }
        if (!result.hasNextPage || result.items.isEmpty()) break
        page++
    }
    library.noteReplyCounts(counts)

    val stale = downloads.staleIds()
    if (stale.isNotEmpty()) library.download(stale)
    return MaintenanceOutcome.COMPLETED
}

/** The same bound 收藏 walks the list under, and for the same reason: it exists. */
private const val MAX_COLLECTION_PAGES = 20
