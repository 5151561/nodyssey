package io.github.nodyssey.data.offline

import io.github.nodyssey.data.CollectedPostMeta
import io.github.nodyssey.data.CollectedPostMetaStore
import io.github.nodyssey.data.OfflineFailure
import io.github.nodyssey.data.OfflineLibrary
import io.github.nodyssey.data.OfflineSettings
import io.github.nodyssey.data.OfflineState
import io.github.nodyssey.data.OfflineThreadReader
import io.github.nodyssey.data.OfflineUsage
import io.github.nodyssey.data.PostRemoteDataSource
import io.github.nodyssey.data.StoredThreadPage
import io.github.nodyssey.data.local.OfflineCommentEntity
import io.github.nodyssey.data.local.OfflineDao
import io.github.nodyssey.data.local.OfflineImageEntity
import io.github.nodyssey.data.local.OfflineStateRow
import io.github.nodyssey.data.local.OfflineStatus
import io.github.nodyssey.data.local.OfflineThreadEntity
import io.github.nodyssey.data.local.RichContentJson
import io.github.nodyssey.model.PostContent
import io.github.nodyssey.model.PostDetail
import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The download engine behind board i1's offline half.
 *
 * Three responsibilities, and they are kept apart on purpose:
 *
 * - **The queue is in Room, not in memory.** A download survives the app being swiped away, the
 *   process being killed for memory, and a Wi-Fi network that does not arrive until tomorrow. What
 *   WorkManager runs is a *drain* of that queue; losing the worker loses nothing but the timing.
 * - **The engine never decides when to run.** [OfflineWorkScheduler] does, because that decision is
 *   about network constraints and battery and belongs to the platform. This class only ever says
 *   "there is something to do".
 * - **Nothing is called 已离线 before it is.** A row reaches [OfflineStatus.DOWNLOADED] after every
 *   page is stored, and cancelling a first download deletes the partial copy rather than keeping a
 *   half-thread under a tick.
 *
 * @param maxImageBytes the largest single picture worth storing. A thread with a 40 MB screenshot
 * in it is not what 「同时下载图片」 is offering to keep, and reading one whole into memory to find
 * that out is not something a background worker should do either.
 */
class RoomOfflineLibrary(
    private val dao: OfflineDao,
    private val remote: PostRemoteDataSource,
    private val files: OfflineFileStore,
    private val images: OfflineImageSource,
    private val settingsStore: OfflineSettingsStore,
    /**
     * Where a download writes down what the pages told it about the thread.
     *
     * This is the only route that fills in a collection made on the web years ago and never opened
     * here: `list-collection` will not name its board or its author, and nothing else on this device
     * has ever seen the thread. Downloading it fetches the very page that says all of it.
     */
    private val collectedMeta: CollectedPostMetaStore,
    private val scheduler: OfflineWorkScheduler,
    private val clock: AppClock,
    private val dispatchers: AppDispatchers,
    private val maxImageBytes: Long = DEFAULT_MAX_IMAGE_BYTES,
    /** See [delayBetweenRequests]. Zero in tests, where there is no site to be polite to. */
    private val requestSpacingMillis: Long = DEFAULT_REQUEST_SPACING_MILLIS,
) : OfflineLibrary,
    OfflineThreadReader,
    OfflineDownloads {
    override val isAvailable: Boolean = true

    override val states: Flow<Map<Long, OfflineState>> =
        dao.observeStates().map { rows -> rows.associate { it.postId to it.toState() } }

    override val usage: Flow<OfflineUsage> =
        dao.observeUsage().map { row ->
            OfflineUsage(
                posts = row.posts,
                textBytes = row.textBytes,
                imageBytes = row.imageBytes,
                freeBytes = files.freeBytes(),
            )
            // Free space is a `StatFs` syscall, and this flow is collected by a screen: without
            // this it would run on whatever dispatcher the ViewModel collects on, which is the main
            // one.
        }.flowOn(dispatchers.io)

    override val settings: Flow<OfflineSettings> = settingsStore.settings

    // --- the queue -------------------------------------------------------------------------------

    override suspend fun download(postIds: Collection<Long>) {
        if (postIds.isEmpty()) return
        withContext(dispatchers.io) {
            val now = clock.nowMillis()
            postIds.forEach { postId ->
                val existing = dao.find(postId)
                // A re-download of a stored thread keeps its content while it queues: the reader can
                // still open the copy they already have, and 「同步」 is a top-up, not a demolition.
                dao.upsert(
                    existing?.copy(status = OfflineStatus.QUEUED, progress = null, failure = null, queuedAtMillis = now)
                        ?: OfflineThreadEntity(
                            postId = postId,
                            title = "",
                            body = null,
                            totalPages = 1,
                            storedCommentCount = 0,
                            remoteCommentCount = null,
                            status = OfflineStatus.QUEUED,
                            progress = null,
                            failure = null,
                            textBytes = 0,
                            queuedAtMillis = now,
                            downloadedAtMillis = null,
                        ),
                )
            }
            scheduler.startDrain(settingsStore.settings.first().wifiOnly)
        }
    }

    override suspend fun noteReplyCounts(counts: Map<Long, Int>) {
        if (counts.isEmpty()) return
        withContext(dispatchers.io) {
            dao.storedIds().forEach { postId -> counts[postId]?.let { dao.setRemoteCommentCount(postId, it) } }
        }
    }

    override suspend fun estimateBytes(postIds: Collection<Long>): Long? =
        withContext(dispatchers.io) {
            val average = dao.averageStoredBytes() ?: return@withContext null
            // Only what is not here yet: the rest is a catch-up on new replies, whose size this
            // average says nothing about and which the toolbar counts on its own line instead.
            val fresh = postIds.count { dao.find(it)?.status != OfflineStatus.DOWNLOADED }
            if (fresh == 0) null else (average * fresh).toLong()
        }

    override suspend fun cancel(postId: Long) {
        withContext(dispatchers.io) {
            val row = dao.find(postId) ?: return@withContext
            if (row.downloadedAtMillis == null) {
                // Nothing complete was here before this download, so there is nothing to keep. The
                // pages already stored are a fragment, and a fragment under a 已离线 tick is the one
                // thing this screen must never say.
                dao.delete(postId)
                files.sweep(dao.liveFileNames().toSet())
            } else {
                dao.upsert(row.copy(status = OfflineStatus.DOWNLOADED, progress = null, failure = null))
            }
        }
    }

    override suspend fun clearAll() {
        withContext(dispatchers.io) {
            dao.deleteAll()
            files.clear()
        }
    }

    override suspend fun updateSettings(settings: OfflineSettings) {
        settingsStore.update(settings)
        // 仅 Wi-Fi 下载 is a WorkManager constraint, so a queue that is already waiting has to be
        // re-enqueued under the new one — otherwise turning the switch off leaves the downloads
        // still waiting for a network they no longer need.
        withContext(dispatchers.io) {
            if (dao.queuedCount() > 0) scheduler.startDrain(settings.wifiOnly, restart = true)
            scheduler.ensureMaintenance()
        }
    }

    // --- the read side ---------------------------------------------------------------------------

    override suspend fun storedPage(
        postId: Long,
        page: Int,
    ): StoredThreadPage? =
        withContext(dispatchers.io) {
            val row = dao.find(postId) ?: return@withContext null
            val downloadedAt = row.downloadedAtMillis ?: return@withContext null
            if (page < 1 || page > row.totalPages) return@withContext null
            val comments = dao.comments(postId, page)
            // Page 1 of a stored thread always has a body; a later page having no comments is
            // ordinary (they were all deleted), a first page with neither is a broken row.
            if (page == 1 && row.body == null && comments.isEmpty()) return@withContext null
            StoredThreadPage(
                detail =
                PostDetail(
                    postId = postId,
                    title = row.title,
                    body = if (page == 1) row.body else null,
                    comments = comments.map { it.content },
                    page = page,
                    totalPages = row.totalPages,
                    hasNextPage = page < row.totalPages,
                    collected = row.collected,
                    collectionCount = row.collectionCount,
                    isAwarded = row.isAwarded,
                ),
                downloadedAtMillis = downloadedAt,
            )
        }

    // --- the drain -------------------------------------------------------------------------------

    override suspend fun hasQueuedWork(): Boolean = withContext(dispatchers.io) { dao.queuedCount() > 0 }

    override suspend fun staleIds(): List<Long> =
        withContext(dispatchers.io) {
            dao
                .observeStates()
                .first()
                .filter { it.status == OfflineStatus.DOWNLOADED && it.behindReplies > 0 }
                .map { it.postId }
        }

    override suspend fun drainQueue(): DrainOutcome =
        withContext(dispatchers.io) {
            val settings = settingsStore.settings.first()
            while (true) {
                val row = dao.nextQueued() ?: break
                val step =
                    runCatchingExceptCancellation { downloadOne(row, settings) }
                        .getOrElse { thrown ->
                            // A row deleted under a running download — 取消, or 清空离线内容 — takes
                            // the foreign key its comments were about to be written against with it,
                            // so the write fails. [fail] is a no-op on a row that is no longer being
                            // downloaded, and the queue behind this thread is still worth draining.
                            fail(row.postId, thrown.toOfflineFailure())
                            StepOutcome.NEXT
                        }
                when (step) {
                    StepOutcome.NEXT -> Unit
                    StepOutcome.NETWORK -> return@withContext DrainOutcome.NETWORK_FAILED
                    StepOutcome.BLOCKED -> return@withContext DrainOutcome.BLOCKED
                }
            }
            DrainOutcome.DRAINED
        }

    /** What one thread's attempt means for the rest of the queue — see [downloadOne]. */
    private enum class StepOutcome {
        /** This thread is done, stored or failed on its own account; the queue continues. */
        NEXT,

        /** The network gave out. Stop, and let the scheduler's backoff try the queue again. */
        NETWORK,

        /** The site is refusing — a challenge or a rate limit. Stop, and do not let it retry. */
        BLOCKED,
    }

    /**
     * Downloads one thread whole, or records why it could not be.
     *
     * The return value is about the *queue*, not this thread: a transport failure is worth the
     * scheduler's backoff, a challenge or rate limit will meet every following row too and must not
     * be retried at ([StepOutcome.BLOCKED]), and everything else is about this thread alone —
     * retrying the queue behind it would only produce the same answer with more requests.
     */
    private suspend fun downloadOne(
        row: OfflineThreadEntity,
        settings: OfflineSettings,
    ): StepOutcome {
        val postId = row.postId
        // Where a top-up starts. The last stored page is re-fetched rather than skipped: it was
        // partial when it was stored, and the replies that arrived since begin inside it.
        val fromPage = if (row.downloadedAtMillis != null) row.totalPages.coerceAtLeast(1) else 1
        dao.upsert(row.copy(status = OfflineStatus.DOWNLOADING, progress = 0f, failure = null))

        val pictures = LinkedHashSet<String>()
        var body: PostContent? = if (fromPage > 1) row.body else null
        var title = row.title
        var totalPages = row.totalPages.coerceAtLeast(fromPage)
        var collected = row.collected
        var collectionCount = row.collectionCount
        var isAwarded = row.isAwarded

        // Deleted on the first page that actually arrives, not before the walk. A catch-up that
        // fails at its first request would otherwise have thrown away the last page of a copy the
        // reader still has, in exchange for nothing.
        var replaced = false
        var page = fromPage
        while (page <= totalPages) {
            if (page > fromPage) delayBetweenRequests()
            if (!stillWanted(postId)) return StepOutcome.NEXT
            val detail =
                runCatchingExceptCancellation { remote.loadDetail(postId, page) }
                    .getOrElse { thrown ->
                        val failure = thrown.toOfflineFailure()
                        fail(postId, failure)
                        return when (failure) {
                            OfflineFailure.Network -> StepOutcome.NETWORK
                            OfflineFailure.Challenge, OfflineFailure.RateLimited -> StepOutcome.BLOCKED
                            else -> StepOutcome.NEXT
                        }
                    }
            totalPages = detail.totalPages.coerceAtLeast(page)
            if (detail.title.isNotBlank()) title = detail.title
            detail.body?.let { body = it }
            detail.collected?.let { collected = it }
            detail.collectionCount?.let { collectionCount = it }
            detail.isAwarded?.let { isAwarded = it }

            // Re-asked after the request, not only before it: the reader had the length of a page
            // fetch to press 停止, and writing comments for a row that is gone is a foreign key
            // violation rather than a wasted write.
            if (!stillWanted(postId)) return StepOutcome.NEXT
            if (!replaced) {
                dao.deleteCommentsFrom(postId, fromPage)
                replaced = true
            }
            dao.upsertComments(
                detail.comments.mapIndexed { position, content ->
                    OfflineCommentEntity(postId = postId, page = page, position = position, content = content)
                },
            )
            if (settings.includeImages) {
                (listOfNotNull(detail.body) + detail.comments).forEach { pictures += it.imageUrls() }
            }
            // Pages are half the job when pictures are coming too, all of it when they are not.
            val pagesShare = (page - fromPage + 1).toFloat() / (totalPages - fromPage + 1).coerceAtLeast(1)
            dao.setProgress(postId, if (settings.includeImages) pagesShare / 2f else pagesShare)
            page++
        }

        if (settings.includeImages && !storePictures(postId, pictures)) {
            fail(postId, OfflineFailure.OutOfSpace)
            return StepOutcome.NEXT
        }
        if (!stillWanted(postId)) return StepOutcome.NEXT

        val stored = dao.commentCount(postId)
        val textBytes = dao.commentBytes(postId) + (body?.sizeBytes() ?: 0L)
        dao.upsert(
            (dao.find(postId) ?: row).copy(
                title = title,
                body = body,
                totalPages = totalPages,
                storedCommentCount = stored,
                // Everything the site had a moment ago is now here, so nothing is behind. The next
                // collection load re-states the site's own count and the row goes stale from there.
                remoteCommentCount = stored,
                status = OfflineStatus.DOWNLOADED,
                progress = null,
                failure = null,
                textBytes = textBytes,
                collected = collected,
                collectionCount = collectionCount,
                isAwarded = isAwarded,
                downloadedAtMillis = clock.nowMillis(),
            ),
        )
        // The pages just fetched carry everything the 收藏 row wants and `list-collection` withholds.
        // Written after the copy is complete rather than per page, so a download that failed halfway
        // does not leave the list describing a thread out of a page it then threw away.
        collectedMeta.remember(
            CollectedPostMeta(
                postId = postId,
                title = title.takeIf { it.isNotBlank() },
                categoryTitle = body?.categoryTitle,
                authorName = body?.authorName,
                avatarUrl = body?.avatarUrl,
                authorUid = body?.authorUid,
                // What the site had when this copy was made — the same number [remoteCommentCount]
                // is set to, and a statement of the site's rather than a count of stored rows.
                commentCount = stored,
                createdAtText = body?.createdAtText,
            ),
        )
        return StepOutcome.NEXT
    }

    /**
     * Fetches the pictures this thread needs, skipping the ones already on disk.
     *
     * @return false only when the device is out of room — the one image failure that is about the
     * device rather than about one picture. A single 404'd attachment must not cost the reader the
     * thread's text.
     */
    private suspend fun storePictures(
        postId: Long,
        urls: Set<String>,
    ): Boolean {
        if (urls.isEmpty()) return true
        val rows = mutableListOf<OfflineImageEntity>()
        urls.forEachIndexed { index, url ->
            val name = files.nameOf(url)
            val known = dao.imageByFile(name)
            if (known != null && files.hasStored(url)) {
                rows += OfflineImageEntity(postId = postId, url = url, fileName = name, bytes = known.bytes)
            } else {
                delayBetweenRequests()
                val bytes = runCatchingExceptCancellation { images.fetch(url, maxImageBytes) }.getOrNull()
                if (bytes != null) {
                    if (!files.hasRoomFor(bytes.size.toLong())) return false
                    // The write is the second half of the same question. Free space cannot be
                    // reserved between the check above and here, and app-private storage has no
                    // other way to refuse a write, so a failure here is the disk being full.
                    val written = runCatchingExceptCancellation { files.write(name, bytes) }.getOrNull() ?: return false
                    rows += OfflineImageEntity(postId, url, name, written)
                }
            }
            dao.setProgress(postId, 0.5f + 0.5f * (index + 1).toFloat() / urls.size)
        }
        dao.upsertImages(rows)
        return true
    }

    /** False when the reader cancelled, or 清空离线内容 removed the row, while this was running. */
    private suspend fun stillWanted(postId: Long): Boolean = dao.find(postId)?.status == OfflineStatus.DOWNLOADING

    /**
     * Records why one thread stopped — but only while it is still the thread being downloaded.
     *
     * A row that has since been cancelled back to 已离线, or deleted outright, is not this download's
     * to mark: turning a kept copy into 下载失败 would take away the very thing 停止 promised to keep.
     */
    private suspend fun fail(
        postId: Long,
        reason: OfflineFailure,
    ) {
        val row = dao.find(postId) ?: return
        if (row.status != OfflineStatus.DOWNLOADING) return
        val hadCopy = row.downloadedAtMillis != null
        dao.upsert(
            row.copy(
                status = OfflineStatus.FAILED,
                progress = null,
                failure = reason.ordinal,
                textBytes = if (hadCopy) row.textBytes else 0,
            ),
        )
        if (hadCopy) return
        // A first attempt that failed leaves a fragment nothing can read and the retention sweep
        // will never reach — it only counts from a completed copy's timestamp, and this row has
        // none. The row itself stays, because it is what draws 下载失败 and its 重试.
        dao.deleteCommentsFrom(postId, 1)
        dao.deleteImagesOf(postId)
        files.sweep(dao.liveFileNames().toSet())
    }

    /**
     * The pause between two page requests.
     *
     * A drain is the only thing in this app that asks the site for pages as fast as it can answer —
     * a dozen collected threads is a hundred requests — and a hundred requests in as many hundred
     * milliseconds is what a bot filter is for. Nobody is watching this run, so the spacing costs
     * the reader nothing.
     */
    private suspend fun delayBetweenRequests() {
        if (requestSpacingMillis > 0) delay(requestSpacingMillis)
    }

    // --- maintenance -----------------------------------------------------------------------------

    override suspend fun sweepExpired() {
        withContext(dispatchers.io) {
            val days = settingsStore.settings.first().retentionDays
            if (days == OfflineSettings.KEEP_FOREVER) return@withContext
            val cutoff = clock.nowMillis() - days * MILLIS_PER_DAY
            dao.expiredIds(cutoff).forEach { dao.delete(it) }
            files.sweep(dao.liveFileNames().toSet())
        }
    }

    companion object {
        /** See [maxImageBytes]. Generous enough for a full-page screenshot, short of a video frame dump. */
        const val DEFAULT_MAX_IMAGE_BYTES = 12L * 1024 * 1024

        /** See [delayBetweenRequests]. */
        const val DEFAULT_REQUEST_SPACING_MILLIS = 400L

        private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}

/** How many replies the site has that this copy does not, or 0 when nothing has said. */
private val OfflineStateRow.behindReplies: Int
    get() = ((remoteCommentCount ?: storedCommentCount) - storedCommentCount).coerceAtLeast(0)

private fun OfflineStateRow.toState(): OfflineState =
    when (status) {
        OfflineStatus.QUEUED -> OfflineState.Downloading(progress)

        OfflineStatus.DOWNLOADING -> OfflineState.Downloading(progress)

        OfflineStatus.FAILED ->
            OfflineState.Failed(
                failure?.let { OfflineFailure.entries.getOrNull(it) } ?: OfflineFailure.Network,
            )

        else ->
            behindReplies
                .takeIf { it > 0 }
                ?.let { OfflineState.Stale(behindReplies = it, bytes = bytes) }
                ?: OfflineState.Downloaded(bytes)
    }

/** What one floor costs to keep, measured on the same JSON the row is stored as. */
private fun PostContent.sizeBytes(): Long =
    RichContentJson.format
        .encodeToString(this)
        // `encodeToByteArray`, not `toByteArray()`: the latter is a JVM extension whose charset is a
        // default rather than a definition, and this number is compared against a stored one.
        .encodeToByteArray()
        .size
        .toLong()

/**
 * Which of the row's words this failure gets.
 *
 * The split is by what would fix it: a transport failure is worth another go and gets the
 * scheduler's backoff; a challenge or a rate limit is the site refusing *this client right now* —
 * more requests are the problem, so those stop the queue instead of retrying it (the rule the
 * notification poller and the maintenance sweep already follow); everything else is the site
 * declining to show this thread to this account, which another request cannot change.
 */
private fun Throwable.toOfflineFailure(): OfflineFailure {
    val error = (this as? SiteException)?.error ?: return OfflineFailure.Network
    return when (error) {
        SiteError.Network, SiteError.Unknown -> OfflineFailure.Network
        SiteError.Cloudflare -> OfflineFailure.Challenge
        SiteError.RateLimited -> OfflineFailure.RateLimited
        is SiteError.Http -> if (error.statusCode >= 500) OfflineFailure.Network else OfflineFailure.Unavailable
        else -> OfflineFailure.Unavailable
    }
}
