package io.github.nodyssey.data

import androidx.compose.runtime.Immutable
import io.github.nodyssey.model.PostDetail
import kotlinx.coroutines.flow.Flow

/**
 * What this device has stored for reading with the network off.
 *
 * 收藏 (board i1) is drawn around offline reading — five per-row states, a queue, a size budget, an
 * incremental catch-up on new replies — and every one of those is a claim about bytes on disk. The
 * engine that makes them true is `data/offline/RoomOfflineLibrary`.
 *
 * It is deliberately *not* built on `post_details`. That cache is a window — the pages a reader
 * happened to scroll through, replaced wholesale by the next refresh — and it stores no images at
 * all, so calling a row in it 「已离线」 would be a promise the cache cannot keep the moment the
 * reader goes offline and pages past the window. Downloads live in tables of their own, and the two
 * meet only in [OfflineThreadReader], where a stored page is handed to the post cache as a read the
 * network could not serve.
 *
 * [isAvailable] is how the screen finds out whether this build has an engine behind it at all. While
 * it is false the status bar, the per-row download column, the 「全部下载」 pill and the 离线管理
 * entry are not drawn — 收藏 is a list, filters and multi-select, and nothing on it says anything
 * about offline that is not true.
 */
interface OfflineLibrary {
    val isAvailable: Boolean

    /** Per-thread state, keyed by post id. A thread with no entry is [OfflineState.NotDownloaded]. */
    val states: Flow<Map<Long, OfflineState>>

    val usage: Flow<OfflineUsage>

    val settings: Flow<OfflineSettings>

    /** Queues these threads. Already-offline ones are refreshed rather than re-fetched whole. */
    suspend fun download(postIds: Collection<Long>)

    /**
     * Tells the library what the site currently says these threads' reply counts are.
     *
     * This is where 「离线版落后 3 条回复」 comes from, and it has to be *told*: a stored copy knows
     * exactly how many replies it contains and nothing whatever about how many have arrived since.
     * The collection list is the one screen that already holds both numbers for every downloaded
     * thread, so it hands them over on every load rather than the library issuing a second sweep of
     * requests to learn what the app had just been given.
     *
     * Counts for threads this device has not downloaded are ignored.
     */
    suspend fun noteReplyCounts(counts: Map<Long, Int>)

    /**
     * Roughly what downloading these would cost, for the 多选 toolbar's 「约 4.6 MB」.
     *
     * Null when there is no basis for a number — which is the honest answer before anything has been
     * downloaded, since the estimate can only come from what past downloads of comparable threads
     * actually weighed. The toolbar drops the size from its line rather than printing a guess.
     */
    suspend fun estimateBytes(postIds: Collection<Long>): Long?

    /** Stops one in-flight download and keeps whatever it had already stored. */
    suspend fun cancel(postId: Long)

    /** Deletes every stored body, reply and image. Collections themselves are untouched. */
    suspend fun clearAll()

    suspend fun updateSettings(settings: OfflineSettings)
}

/**
 * The read side of the download store, for the post cache to fall back on.
 *
 * Separate from [OfflineLibrary] because it has a different caller and a different question: the
 * library is what 收藏 manages downloads through, this is what the *reader* silently benefits from
 * when the site cannot be reached. Kept to one method so nothing about how threads are downloaded
 * leaks into the repository that consumes them.
 */
interface OfflineThreadReader {
    /** One stored page as though it had just been fetched, or null when this device has no copy. */
    suspend fun storedPage(
        postId: Long,
        page: Int,
    ): StoredThreadPage?
}

/**
 * A page out of the download store, with the moment its copy was made.
 *
 * [downloadedAtMillis] travels with it because the post cache stamps what it stores, and stamping a
 * three-day-old copy with "now" would make the reader's own screen call it fresh — no refresh
 * offered, no indication that the replies below are the ones from Tuesday.
 */
data class StoredThreadPage(
    val detail: PostDetail,
    val downloadedAtMillis: Long,
)

/**
 * One thread's offline state — the five the row can draw, and no sixth.
 *
 * [Stale] is separate from [Downloaded] rather than a flag on it because they are different answers
 * to the reader's actual question: one says "this is here", the other says "this is here but the
 * thread has moved on", and only the second one is worth a tap.
 */
sealed interface OfflineState {
    data object NotDownloaded : OfflineState

    /** [progress] is null while the thread is queued behind others and has no share of its own yet. */
    data class Downloading(val progress: Float? = null) : OfflineState

    data class Downloaded(val bytes: Long) : OfflineState

    /** Stored, but the site has [behindReplies] replies this copy has never seen. */
    data class Stale(val behindReplies: Int, val bytes: Long) : OfflineState

    data class Failed(val reason: OfflineFailure) : OfflineState
}

/**
 * Why a download stopped, in the categories the row has words for.
 *
 * An enum rather than the engine's own message: the row shows the reason in eleven characters next
 * to a 重试 button, and whatever a failing HTTP layer has to say does not fit there and is not what
 * the reader needs in order to decide whether tapping 重试 is worth anything.
 *
 * The ordinal is what the row stores (`offline_threads.failure`), so entries are append-only:
 * reordering these renames every failure already on disk.
 */
enum class OfflineFailure {
    /** No room left. Retrying changes nothing until something is cleared. */
    OutOfSpace,

    /** The network went away mid-download. Retrying is exactly the right move. */
    Network,

    /** The thread is gone or no longer visible to this account. */
    Unavailable,

    /**
     * Cloudflare wants a human. Not [Network], though it used to be filed there: that classification
     * handed the queue to WorkManager's backoff, and a challenge answered with a retry curve of
     * non-browser traffic is how a challenge that would have passed becomes one that never does —
     * the exact behaviour the notification poller and the maintenance sweep already refuse. 重试 is
     * still right, but only after the reader has been through the WebView.
     */
    Challenge,

    /** The site said slow down. More background requests are the opposite of the remedy. */
    RateLimited,
}

/** The size breakdown the 离线管理 sheet draws, in bytes. */
@Immutable
data class OfflineUsage(
    val posts: Int = 0,
    val textBytes: Long = 0,
    val imageBytes: Long = 0,
    /** Free space on the device, for the 「可用 3.2 GB」 half of the line. Null when unreadable. */
    val freeBytes: Long? = null,
) {
    val totalBytes: Long get() = textBytes + imageBytes
}

/** The four controls in the 离线管理 sheet. */
@Immutable
data class OfflineSettings(
    val wifiOnly: Boolean = true,
    val includeImages: Boolean = true,
    val autoSyncReplies: Boolean = false,
    val retentionDays: Int = DEFAULT_RETENTION_DAYS,
) {
    companion object {
        /** 不清理 — kept as a day count of zero so the setting is one Int rather than an Int and a flag. */
        const val KEEP_FOREVER = 0

        const val DEFAULT_RETENTION_DAYS = 30

        val RETENTION_CHOICES = listOf(7, 30, 90, KEEP_FOREVER)
    }
}
