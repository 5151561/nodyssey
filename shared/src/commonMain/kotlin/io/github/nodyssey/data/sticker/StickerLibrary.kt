package io.github.nodyssey.data.sticker

import io.github.nodyssey.data.composer.ImagePreparer
import io.github.nodyssey.data.composer.PickedImage
import io.github.nodyssey.data.imagehost.HostedImage
import io.github.nodyssey.data.imagehost.ImageHostConfig
import io.github.nodyssey.data.imagehost.ImageHostError
import io.github.nodyssey.data.imagehost.ImageHostException
import io.github.nodyssey.data.imagehost.ImageHostRepository
import io.github.nodyssey.data.local.MyStickerEntity
import io.github.nodyssey.data.local.StickerDao
import io.github.nodyssey.data.local.StickerFolderEntity
import io.github.nodyssey.data.local.StickerRepoEntity
import io.github.nodyssey.data.settings.StickerSettingsStore
import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpResponse
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/** What a HEAD request said about a link. */
sealed interface LinkProbe {
    /** Reachable. [sizeBytes] when the server said how large it is. */
    data class Ok(val sizeBytes: Long?) : LinkProbe

    /** Answered with an error status — a 404 is the usual one, the picture is gone. */
    data class Broken(val code: Int) : LinkProbe

    /** No answer at all — DNS, a refused connection, a timeout. */
    data object Unreachable : LinkProbe
}

/** One picture on its way to the image host and then into 我的. */
data class StickerUpload(
    val id: String,
    val source: String,
    val name: String,
    val keepOriginal: Boolean,
    val progress: Float = 0f,
    val state: State = State.WAITING,
    val error: ImageHostException? = null,
) {
    enum class State { WAITING, UPLOADING, DONE, FAILED }
}

/**
 * 我的表情 and the GitHub sticker packs: everything the panel, the add sheet and 表情管理 read and
 * write, behind one object the platform shells build once.
 *
 * Uploads run in [scope] rather than a screen's: 「关掉面板也会在后台传完」 is the promise, and a
 * ViewModel's scope ends with the sheet.
 */
class StickerLibrary(
    private val dao: StickerDao,
    private val github: GitHubStickerSource,
    private val settingsStore: StickerSettingsStore,
    private val imageHost: ImageHostRepository,
    private val preparer: ImagePreparer,
    /** Cookie-free and referrer-free — the image host's transport. Probes go out through it. */
    private val http: HttpTransport,
    /** [HttpTransport.execute] blocks on Android, and every caller here is a screen on the main thread. */
    private val dispatchers: AppDispatchers,
    private val clock: AppClock,
    private val scope: CoroutineScope,
) {
    // ---- 我的 -------------------------------------------------------------------------------------

    val mine: Flow<List<MySticker>> = dao.observeMine().map { rows -> rows.map { MySticker(it.url, it.name) } }

    /**
     * Puts [stickers] at the front of 我的, first one first. A link already there keeps its place
     * and name. @return the links that were new — what an undo should take back out.
     */
    suspend fun add(stickers: List<MySticker>): List<String> {
        val fresh = stickers.distinctBy { it.url }.filter { isStickerLink(it.url) }
        if (fresh.isEmpty()) return emptyList()
        val now = clock.nowMillis()
        val rows = fresh.map { sticker ->
            MyStickerEntity(
                url = sticker.url,
                name = sticker.name.trim().take(MAX_NAME_LENGTH).ifEmpty { stickerNameFromUrl(sticker.url) },
                position = 0L,
                addedAtMillis = now,
            )
        }
        return dao.insertAtFront(rows).zip(rows).filter { (rowId, _) -> rowId != -1L }.map { (_, row) -> row.url }
    }

    suspend fun remove(urls: Collection<String>) {
        if (urls.isNotEmpty()) dao.deleteMine(urls.toList())
    }

    suspend fun rename(
        url: String,
        name: String,
    ) {
        val trimmed = name.trim().take(MAX_NAME_LENGTH)
        if (trimmed.isNotEmpty()) dao.rename(url, trimmed)
    }

    /** 移到最前, keeping the moved ones in the order they already had among themselves. */
    suspend fun moveToTop(urls: Collection<String>) {
        val chosen = urls.toSet()
        val current = dao.listMine().map { it.url }
        dao.reorderMine(current.filter { it in chosen } + current.filterNot { it in chosen })
    }

    /** 按住拖动排序 — the whole new order. */
    suspend fun reorder(urls: List<String>) = dao.reorderMine(urls)

    /**
     * Asks [url]'s server about it without downloading it.
     *
     * HEAD rather than GET because the transport reads bodies as text. A server that refuses HEAD
     * (405, 501) is taken as reachable with no size: the picture itself was never asked for.
     */
    suspend fun probe(url: String): LinkProbe {
        // Only a network failure is Unreachable. Anything else thrown here is a bug, and turning it
        // into Unreachable is how a crash once became 「全部移除」 offering to delete every sticker.
        val response = try {
            head(url)
        } catch (_: SiteException) {
            null
        } ?: return LinkProbe.Unreachable
        return when {
            response.isSuccessful -> LinkProbe.Ok(response.header("content-length")?.toLongOrNull())
            response.code == 405 || response.code == 501 -> LinkProbe.Ok(null)
            else -> LinkProbe.Broken(response.code)
        }
    }

    private val _linkProbes = MutableStateFlow<Map<String, LinkProbe>>(emptyMap())

    /** What the last [probeMine] found, by link. A link not in it has not been asked about. */
    val linkProbes: StateFlow<Map<String, LinkProbe>> = _linkProbes.asStateFlow()

    /**
     * HEADs every saved link, four at a time, and keeps the answers in [linkProbes] — where both
     * 表情管理's front page (「2 张失效」) and 我的's own page read them, so walking from one to the
     * other asks each image host once. Run when the front page opens: a link that broke an hour ago
     * is the same news an hour later, and each one is a request to somebody's image host.
     */
    suspend fun probeMine() {
        val urls = mine.first().map { it.url }
        _linkProbes.value = emptyMap()
        val gate = Semaphore(PROBE_PARALLELISM)
        coroutineScope {
            urls.forEach { url ->
                launch {
                    val result = gate.withPermit { probe(url) }
                    _linkProbes.update { it + (url to result) }
                }
            }
        }
    }

    // ---- 面板里的组 --------------------------------------------------------------------------------

    val groupLayout: Flow<StickerGroupLayout> = settingsStore.groupLayout

    /** 按住拖动 on 表情管理's front page: every group's key, in the new order. */
    suspend fun reorderGroups(keys: List<String>) = settingsStore.setGroupOrder(keys)

    /**
     * 左滑隐藏. A subscribed folder keeps its flag on its own row, where unsubscribing takes it away
     * with the folder; every other group's is in the settings store.
     */
    suspend fun setGroupHidden(
        key: String,
        hidden: Boolean,
    ) {
        val folder = parseFolderGroupKey(key)
        if (folder != null) {
            dao.setHidden(folder.first, folder.second, hidden)
        } else {
            settingsStore.setGroupHidden(key, hidden)
        }
    }

    // ---- 我的图床 ----------------------------------------------------------------------------------

    val imageHostConfig: Flow<ImageHostConfig> get() = imageHost.current

    /** @throws ImageHostException as [ImageHostRepository.images] does. */
    suspend fun hostedImages(): List<HostedImage> = imageHost.images()

    // ---- 上传 -------------------------------------------------------------------------------------

    private val _uploads = MutableStateFlow<List<StickerUpload>>(emptyList())
    val uploads: StateFlow<List<StickerUpload>> = _uploads.asStateFlow()

    /**
     * One worker for the life of [scope], woken through [uploadWake] — rather than a job started
     * per pick and checked with `isActive`, which a pick could find still active in the instant
     * after the job had looked for work and found none, leaving that pick waiting forever. A
     * conflated channel cannot lose the wake-up: one sent while the worker drains is still there
     * when it looks again.
     */
    private val uploadWake = Channel<Unit>(Channel.CONFLATED)
    private val uploadWorker = scope.launch(start = CoroutineStart.LAZY) {
        while (true) {
            uploadWake.receive()
            while (true) {
                val next = _uploads.value.firstOrNull { it.state == StickerUpload.State.WAITING } ?: break
                uploadOne(next)
            }
        }
    }

    fun upload(
        images: List<PickedImage>,
        keepOriginal: Boolean,
    ) {
        if (images.isEmpty()) return
        _uploads.update { current ->
            current.filterNot { it.state == StickerUpload.State.DONE } +
                images.map { StickerUpload(Uuid.random().toString(), it.source, it.name, keepOriginal) }
        }
        pumpUploads()
    }

    fun retryUpload(id: String) {
        _uploads.update { list ->
            list.map { if (it.id == id && it.state == StickerUpload.State.FAILED) it.copy(state = StickerUpload.State.WAITING, error = null, progress = 0f) else it }
        }
        pumpUploads()
    }

    /** Clears the finished rows, so the next pick starts on an empty list. */
    fun clearFinishedUploads() {
        _uploads.update { list -> list.filterNot { it.state == StickerUpload.State.DONE || it.state == StickerUpload.State.FAILED } }
    }

    private fun pumpUploads() {
        uploadWake.trySend(Unit)
        uploadWorker.start()
    }

    /**
     * Everything one picture can throw ends as that row's FAILED, saving it into 我的 included: an
     * exception escaping here would end the worker, and with it every upload after this one.
     */
    private suspend fun uploadOne(next: StickerUpload) {
        setUpload(next.id) { it.copy(state = StickerUpload.State.UPLOADING) }
        val result = runCatchingExceptCancellation {
            val prepared = if (next.keepOriginal) {
                preparer.original(next.source, next.name)
            } else {
                preparer.prepare(next.source, next.name)
            }
            val hosted = imageHost.upload(prepared) { fraction -> setUpload(next.id) { it.copy(progress = fraction) } }
            // Uploaded, but to a link 我的 does not keep (see [isStickerLink]) — said, rather than a
            // DONE row with nothing new in the panel.
            if (!isStickerLink(hosted.url)) throw ImageHostException(ImageHostError.InsecureLink, detail = hosted.url)
            add(listOf(MySticker(hosted.url, stickerNameFromUrl(next.name))))
        }
        result
            .onSuccess {
                setUpload(next.id) { it.copy(state = StickerUpload.State.DONE, progress = 1f) }
            }.onFailure { error ->
                val failure = error as? ImageHostException
                    ?: ImageHostException(ImageHostError.Network, cause = error)
                setUpload(next.id) { it.copy(state = StickerUpload.State.FAILED, error = failure) }
            }
    }

    private fun setUpload(
        id: String,
        change: (StickerUpload) -> StickerUpload,
    ) = _uploads.update { list -> list.map { if (it.id == id) change(it) else it } }

    // ---- 备份 -------------------------------------------------------------------------------------

    /** 导出: see [StickerBackup]. */
    suspend fun exportBackup(): String {
        val layout = settingsStore.groupLayout.first()
        val subscriptions = subscriptions.first()
        return StickerBackup.encode(
            StickerBackup(
                version = StickerBackup.VERSION,
                mine = mine.first().map { StickerBackup.Mine(it.url, it.name) },
                subscriptions = subscriptions.map { subscription ->
                    StickerBackup.Subscription(
                        repo = subscription.slug,
                        ref = subscription.ref,
                        sha = subscription.pinnedSha,
                        folders = subscription.folders.map { it.path },
                        hiddenFolders = subscription.folders.filter { it.hidden }.map { it.path },
                    )
                },
                order = layout.order,
                hidden = layout.hidden.toList(),
            ),
        )
    }

    /**
     * 导入 what [exportBackup] wrote. Adds to what is here rather than replacing it: 我的 keeps every
     * link it has (the backup's go after them, in the backup's order), a repository already subscribed
     * is left as it is, and one that is not is read again at the commit the backup pinned — so a pack
     * comes back exactly as it was, whatever its branch has done since. The group order is taken from
     * the backup only when this device has none of its own yet.
     *
     * @return null when [text] is not a backup at all.
     */
    suspend fun importBackup(text: String): StickerImportResult? {
        val backup = StickerBackup.decode(text) ?: return null
        val existing = mine.first().map { it.url }.toSet()
        val fresh = backup.mine.filter { isStickerLink(it.url) && it.url !in existing }
        if (fresh.isNotEmpty()) {
            add(fresh.map { MySticker(it.url, it.name) })
            dao.reorderMine(existing.toList() + fresh.map { it.url }.distinct())
        }

        val subscribed = dao.listRepos().map { it.slug.lowercase() }.toSet()
        var folders = 0
        val failed = mutableListOf<String>()
        for (subscription in backup.subscriptions) {
            if (subscription.repo.lowercase() in subscribed) continue
            val parts = subscription.repo.split('/')
            if (parts.size != 2 || subscription.folders.isEmpty()) {
                failed += subscription.repo
                continue
            }
            try {
                val listing = github.listAt(parts[0], parts[1], subscription.ref, subscription.sha)
                val paths = subscription.folders.toSet() intersect listing.folders.map { it.path }.toSet()
                if (paths.isEmpty()) {
                    failed += subscription.repo
                    continue
                }
                subscribe(listing, paths)
                subscription.hiddenFolders.filter { it in paths }.forEach { dao.setHidden(listing.slug, it, true) }
                folders += paths.size
            } catch (e: StickerSourceException) {
                failed += subscription.repo
                if (e.error is StickerSourceError.RateLimited) break
            }
        }

        val layout = settingsStore.groupLayout.first()
        if (layout.order.isEmpty() && backup.order.isNotEmpty()) settingsStore.setGroupOrder(backup.order)
        backup.hidden.filter { parseFolderGroupKey(it) == null }.forEach { settingsStore.setGroupHidden(it, true) }
        return StickerImportResult(stickers = fresh.size, folders = folders, failedRepos = failed)
    }

    // ---- GitHub 订阅 ------------------------------------------------------------------------------

    val cdn: Flow<StickerCdnSettings> = settingsStore.settings

    private val _sourceErrors = MutableStateFlow<Map<String, StickerSourceError>>(emptyMap())

    /**
     * What the last check said about each repository that failed, by slug — how 1i knows a source is
     * gone (仓库找不到了) rather than only that some check failed. Cleared for a repository as soon as
     * one goes through.
     */
    val sourceErrors: StateFlow<Map<String, StickerSourceError>> = _sourceErrors.asStateFlow()

    suspend fun setCdn(cdn: StickerCdn) = settingsStore.setCdn(cdn)

    suspend fun setCustomCdnBase(base: String) = settingsStore.setCustomBase(base)

    val subscriptions: Flow<List<StickerSubscription>> =
        combine(dao.observeRepos(), dao.observeFolders()) { repos, folders ->
            val bySlug = folders.groupBy { it.repoSlug }
            repos.map { repo -> repo.toSubscription(bySlug[repo.slug].orEmpty()) }
        }

    /**
     * Lists [ref] for the add sheet. A link that names no branch, to a repository already subscribed,
     * is read on the branch the subscription follows rather than the default one: re-subscribing
     * should not quietly move a pack to another branch because the link pasted was a short one.
     *
     * @throws StickerSourceException
     */
    suspend fun inspect(ref: GitHubRepoRef): RepoListing {
        val subscribedRef = if (ref.ref == null) {
            dao.listRepos().firstOrNull { it.slug.equals(ref.slug, ignoreCase = true) }?.ref
        } else {
            null
        }
        return github.list(if (subscribedRef != null) ref.copy(ref = subscribedRef) else ref)
    }

    /**
     * Subscribes to [paths] of [listing] — or, for a repository already subscribed, replaces its
     * folder set with them (换文件夹). Folders kept keep their order and their 隐藏; new ones go to
     * the end. Pinned at [RepoListing.sha] either way.
     *
     * A listing narrowed to one folder ([RepoListing.subPath]) replaces only the folders under it:
     * the reader picked among those and saw no others. The subscribed folders elsewhere stay, with
     * their lists read again at the new commit from [RepoListing.otherFolders] — one pin per
     * repository — and, as on 更新, one that no longer exists there goes.
     */
    suspend fun subscribe(
        listing: RepoListing,
        paths: Set<String>,
    ) {
        require(paths.isNotEmpty())
        val existing = dao.repo(listing.slug)
        val previous = dao.folders(listing.slug).associateBy { it.path }
        val outside = listing.otherFolders.associateBy { it.path }
        val carried = previous.values
            .filterNot { GitHubStickerSource.isUnder(it.path, listing.subPath) }
            .mapNotNull { folder ->
                outside[folder.path]?.let { folder.copy(files = it.files.joinToString("\n"), pendingFiles = null) }
            }
        val chosen = listing.folders.filter { it.path in paths }
        var nextPosition = (previous.values.maxOfOrNull { it.position } ?: -1L) + 1
        val folders = carried + chosen.map { folder ->
            val kept = previous[folder.path]
            StickerFolderEntity(
                repoSlug = listing.slug,
                path = folder.path,
                files = folder.files.joinToString("\n"),
                pendingFiles = null,
                hidden = kept?.hidden ?: false,
                position = kept?.position ?: nextPosition++,
            )
        }
        val repo = StickerRepoEntity(
            slug = listing.slug,
            ref = listing.ref,
            pinnedSha = listing.sha,
            latestSha = null,
            checkedAtMillis = clock.nowMillis(),
            position = existing?.position ?: ((dao.maxRepoPosition() ?: -1L) + 1),
            // Not from a tree GitHub cut short: a count of what it happened to send would be low.
            folderCount = (listing.folders.size + listing.otherFolders.size).takeUnless { listing.truncated },
        )
        dao.replaceSubscription(repo, folders)
    }

    suspend fun unsubscribe(slug: String) {
        dao.unsubscribe(slug)
        _sourceErrors.update { it - slug }
    }

    suspend fun setFolderHidden(
        slug: String,
        path: String,
        hidden: Boolean,
    ) = dao.setHidden(slug, path, hidden)

    suspend fun reorderFolders(
        slug: String,
        paths: List<String>,
    ) = dao.reorderFolders(slug, paths)

    /**
     * Asks GitHub whether any subscribed branch has moved, and if so stores what the subscribed
     * folders hold at the new commit — as pending, never in place of what the panel shows.
     *
     * Throttled to once per [UPDATE_INTERVAL_MS] per repository unless [force]: every call here is
     * two of GitHub's sixty anonymous calls an hour, and a reader behind a shared address shares them.
     * A repository that fails is skipped; @return the first failure, if any, for the screen to show.
     */
    suspend fun checkForUpdates(force: Boolean): StickerSourceError? {
        var firstError: StickerSourceError? = null
        val now = clock.nowMillis()
        for (repo in dao.listRepos()) {
            if (!force && now - repo.checkedAtMillis < UPDATE_INTERVAL_MS) continue
            val (owner, name) = repo.slug.split('/', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            try {
                val head = github.headOf(owner, name, repo.ref)
                _sourceErrors.update { it - repo.slug }
                // A subscription from before the count was kept: one listing of the pinned commit
                // fills it in, once — the next check finds it set.
                if (head == repo.pinnedSha && repo.folderCount == null) {
                    countFolders(owner, name, repo.ref, head)?.let { dao.setFolderCount(repo.slug, it) }
                }
                if (head == repo.pinnedSha) {
                    dao.recordCheck(repo.slug, repo.pinnedSha, latestSha = null, now, filesAtLatest = null, truncated = false)
                    continue
                }
                val listing = if (head != repo.latestSha) {
                    try {
                        github.listAt(owner, name, repo.ref, head)
                    } catch (e: StickerSourceException) {
                        if (e.error != StickerSourceError.NoImages) throw e
                        RepoListing(owner, name, repo.ref, head, emptyList(), truncated = false)
                    }
                } else {
                    null
                }
                listing?.takeUnless { it.truncated }?.let { dao.setFolderCount(repo.slug, it.folders.size) }
                // Written against the rows as they are now, not as they were read above: see
                // [StickerDao.recordCheck].
                dao.recordCheck(
                    slug = repo.slug,
                    pinnedSha = repo.pinnedSha,
                    latestSha = head,
                    checkedAtMillis = now,
                    filesAtLatest = listing?.folders?.associate { it.path to it.files.joinToString("\n") },
                    truncated = listing?.truncated == true,
                )
            } catch (e: StickerSourceException) {
                if (firstError == null) firstError = e.error
                _sourceErrors.update { it + (repo.slug to e.error) }
                if (e.error is StickerSourceError.RateLimited) break
            }
        }
        return firstError
    }

    /** How many image folders [sha] has; null when the tree came back cut short and the count would be low. */
    private suspend fun countFolders(
        owner: String,
        repo: String,
        ref: String,
        sha: String,
    ): Int? =
        try {
            github.listAt(owner, repo, ref, sha).takeUnless { it.truncated }?.folders?.size
        } catch (e: StickerSourceException) {
            if (e.error != StickerSourceError.NoImages) throw e
            0
        }

    /**
     * 更新: moves [slug] to the commit the last check found. One pin per repository, so a folder that
     * vanished upstream goes with it.
     *
     * If every folder vanished nothing moves, rather than leaving a subscription with no tabs — the
     * pinned commit still serves every picture. The update is dismissed instead: the pending lists
     * are cleared and `latestSha` kept, so the next check, finding the branch still there, does not
     * raise it again. Leaving it in place made 更新 a button that did nothing, forever.
     */
    suspend fun applyUpdate(slug: String) {
        val repo = dao.repo(slug) ?: return
        val latest = repo.latestSha ?: return
        val moved = dao.folders(slug).mapNotNull { folder ->
            val pending = folder.pendingFiles ?: return@mapNotNull folder
            if (pending.isEmpty()) null else folder.copy(files = pending, pendingFiles = null)
        }
        if (moved.isEmpty()) {
            dao.clearPending(slug)
            return
        }
        dao.replaceSubscription(repo.copy(pinnedSha = latest, latestSha = null), moved)
    }

    /**
     * How long one sample picture takes to answer through [cdn], in milliseconds; null when it did
     * not within [PROBE_TIMEOUT_MS] or there is nothing subscribed to sample.
     */
    suspend fun measure(cdn: StickerCdn): Long? {
        val folder = subscriptions.first().flatMap { it.folders }.firstOrNull { it.files.isNotEmpty() } ?: return null
        val url = settingsStore.settings.first().urlFor(folder.owner, folder.repo, folder.pinnedSha, folder.files.first(), cdn)
        val start = TimeSource.Monotonic.markNow()
        val response = try {
            head(url)
        } catch (_: SiteException) {
            null
        } ?: return null
        return if (response.isSuccessful) start.elapsedNow().inWholeMilliseconds else null
    }

    /** A HEAD of [url] off the main thread; null when it did not answer within [PROBE_TIMEOUT_MS]. */
    private suspend fun head(url: String): HttpResponse? =
        withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            withContext(dispatchers.io) { http.execute(HttpRequest(url = url, method = "HEAD")) }
        }

    private fun StickerRepoEntity.toSubscription(folders: List<StickerFolderEntity>): StickerSubscription {
        val (owner, repo) = slug.split('/', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return StickerSubscription(
            owner = owner,
            repo = repo,
            ref = ref,
            pinnedSha = pinnedSha,
            checkedAtMillis = checkedAtMillis,
            folderCount = folderCount,
            folders = folders.sortedBy { it.position }.map { folder ->
                SubscribedFolder(
                    owner = owner,
                    repo = repo,
                    pinnedSha = pinnedSha,
                    path = folder.path,
                    files = folder.files.splitLines(),
                    pendingFiles = folder.pendingFiles?.splitLines(),
                    hidden = folder.hidden,
                )
            },
        )
    }

    private fun String.splitLines(): List<String> = if (isEmpty()) emptyList() else split('\n')

    companion object {
        const val PROBE_TIMEOUT_MS = 8_000L
        private const val PROBE_PARALLELISM = 4

        /** Six hours: a pack gains pictures over weeks, and the check spends shared rate limit. */
        const val UPDATE_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}
