package io.github.nodyssey.data

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * What this device has stored for reading with the network off.
 *
 * Declared as an interface with no working implementation yet, on purpose. 收藏 (board i1) is drawn
 * around offline reading — five per-row states, a queue, a size budget, an incremental catch-up on
 * new replies — and every one of those is a claim about bytes on disk. The screen is built and the
 * shape of the answer is fixed here; the engine that fills it in is a separate piece of work.
 *
 * It is deliberately *not* built on `post_details`. That cache is a window — the pages a reader
 * happened to scroll through — and it stores no images at all, so calling a row in it 「已离线」 would
 * be a promise the cache cannot keep the moment the reader goes offline and pages past the window.
 *
 * [isAvailable] is how the screen finds out. While it is false the status bar, the per-row download
 * column, the 「全部下载」 pill and the 离线管理 entry are not drawn — 收藏 is a list, filters and
 * multi-select, and nothing on it says anything about offline that is not true.
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
 * The implementation that ships until the download engine exists.
 *
 * Empty rather than absent so the screen has one code path: the view model always collects these
 * flows, and [isAvailable] is the single thing it branches on.
 */
object UnavailableOfflineLibrary : OfflineLibrary {
    override val isAvailable: Boolean = false
    override val states: Flow<Map<Long, OfflineState>> = flowOf(emptyMap())
    override val usage: Flow<OfflineUsage> = flowOf(OfflineUsage())
    override val settings: Flow<OfflineSettings> = flowOf(OfflineSettings())

    override suspend fun download(postIds: Collection<Long>) = Unit

    override suspend fun estimateBytes(postIds: Collection<Long>): Long? = null

    override suspend fun cancel(postId: Long) = Unit

    override suspend fun clearAll() = Unit

    override suspend fun updateSettings(settings: OfflineSettings) = Unit
}

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
 */
enum class OfflineFailure {
    /** No room left. Retrying changes nothing until something is cleared. */
    OutOfSpace,

    /** The network went away mid-download. Retrying is exactly the right move. */
    Network,

    /** The thread is gone or no longer visible to this account. */
    Unavailable,
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
