package io.github.nodyssey.ui.sticker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.nodyssey.data.composer.PickedImage
import io.github.nodyssey.data.imagehost.HostedImage
import io.github.nodyssey.data.imagehost.ImageHostError
import io.github.nodyssey.data.imagehost.ImageHostException
import io.github.nodyssey.data.sticker.GitHubRepoRef
import io.github.nodyssey.data.sticker.LinkProbe
import io.github.nodyssey.data.sticker.MySticker
import io.github.nodyssey.data.sticker.RepoListing
import io.github.nodyssey.data.sticker.StickerCdnSettings
import io.github.nodyssey.data.sticker.StickerLibrary
import io.github.nodyssey.data.sticker.StickerSourceError
import io.github.nodyssey.data.sticker.StickerSourceException
import io.github.nodyssey.data.sticker.StickerSubscription
import io.github.nodyssey.data.sticker.extractStickerLinks
import io.github.nodyssey.data.sticker.stickerNameFromUrl
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StickerAddTab { LINK, HOST, UPLOAD, GITHUB }

/** One pasted link in 1c's preview row. */
data class LinkPreview(
    val url: String,
    val name: String,
    /** Null until the picture has loaded or failed — decided by drawing it, not by asking. */
    val loads: Boolean? = null,
    /** Whether its server answered the probe; null while that is out. */
    val answered: Boolean? = null,
    val sizeBytes: Long? = null,
) {
    val tooLarge: Boolean get() = (sizeBytes ?: 0L) > LARGE_STICKER_BYTES

    /**
     * Goes in with 添加. Drawing it decides when it has been drawn; a tile the row never scrolled to
     * has not, and stands on its server's answer instead.
     */
    val ready: Boolean get() = loads ?: (answered == true)
}

/** Past this a sticker is slow to appear in a thread; said, not refused. */
const val LARGE_STICKER_BYTES = 2L * 1024 * 1024

sealed interface HostListState {
    data object Loading : HostListState

    data class Loaded(val images: List<HostedImage>) : HostListState

    data class Failed(val error: ImageHostError) : HostListState
}

sealed interface RepoState {
    data object Idle : RepoState

    data object Loading : RepoState

    /** The address is not one this can read. */
    data object BadUrl : RepoState

    data class Loaded(val listing: RepoListing) : RepoState

    data class Failed(val error: StickerSourceError) : RepoState
}

/**
 * 添加表情 — the four ways into 我的 and the GitHub subscription, one page.
 *
 * One per opening of that page, so each starts empty.
 */
class StickerAddViewModel(
    private val library: StickerLibrary,
) : ViewModel() {
    private val _tab = MutableStateFlow(StickerAddTab.LINK)
    val tab: StateFlow<StickerAddTab> = _tab.asStateFlow()

    val mineUrls: StateFlow<Set<String>> =
        library.mine.map { list -> list.mapTo(HashSet()) { it.url } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun selectTab(tab: StickerAddTab) {
        _tab.value = tab
        if (tab == StickerAddTab.HOST && _host.value !is HostListState.Loaded) loadHost()
    }

    // ---- 链接 -------------------------------------------------------------------------------------

    private val _linkText = MutableStateFlow("")
    val linkText: StateFlow<String> = _linkText.asStateFlow()

    private val _previews = MutableStateFlow<List<LinkPreview>>(emptyList())
    val previews: StateFlow<List<LinkPreview>> = _previews.asStateFlow()

    private var probeJob: Job? = null

    fun setLinkText(text: String) {
        _linkText.value = text
        val links = extractStickerLinks(text).take(MAX_LINKS)
        val previous = _previews.value.associateBy { it.url }
        _previews.value = links.map { url -> previous[url] ?: LinkPreview(url, stickerNameFromUrl(url)) }
        // Asked once the text stops changing: typed out by hand, every prefix of a link is a link,
        // and each would be a request to somebody's server. A probe still out for a link edited
        // away is dropped with it; one for a link still there is asked again.
        probeJob?.cancel()
        probeJob = viewModelScope.launch {
            delay(PROBE_DEBOUNCE_MS)
            _previews.value.filter { it.answered == null }.forEach { preview ->
                launch {
                    val probe = library.probe(preview.url)
                    updatePreview(preview.url) {
                        it.copy(answered = probe is LinkProbe.Ok, sizeBytes = (probe as? LinkProbe.Ok)?.sizeBytes)
                    }
                }
            }
        }
    }

    /** Appends a pasted block on a line of its own. */
    fun appendLinks(text: String) {
        val current = _linkText.value.trimEnd()
        setLinkText(if (current.isEmpty()) text.trim() else "$current\n${text.trim()}")
    }

    fun onPreviewLoaded(
        url: String,
        loads: Boolean,
    ) = updatePreview(url) { it.copy(loads = loads) }

    fun renamePreview(
        url: String,
        name: String,
    ) = updatePreview(url) { it.copy(name = name.trim().ifEmpty { it.name }) }

    private fun updatePreview(
        url: String,
        change: (LinkPreview) -> LinkPreview,
    ) = _previews.update { list -> list.map { if (it.url == url) change(it) else it } }

    /** The [LinkPreview.ready] previews; the ones still loading wait, the broken ones are left out. */
    suspend fun addLinks(): List<String> {
        val ready = _previews.value.filter { it.ready }
        return library.add(ready.map { MySticker(it.url, it.name) })
    }

    // ---- 我的图床 ---------------------------------------------------------------------------------

    val hostConfig = library.imageHostConfig.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _host = MutableStateFlow<HostListState>(HostListState.Loading)
    val host: StateFlow<HostListState> = _host.asStateFlow()

    private val _hostSelection = MutableStateFlow<Set<String>>(emptySet())
    val hostSelection: StateFlow<Set<String>> = _hostSelection.asStateFlow()

    private val _gifOnly = MutableStateFlow(false)
    val gifOnly: StateFlow<Boolean> = _gifOnly.asStateFlow()

    fun loadHost() {
        _host.value = HostListState.Loading
        viewModelScope.launch {
            _host.value = runCatchingExceptCancellation { library.hostedImages() }.fold(
                onSuccess = { HostListState.Loaded(it) },
                onFailure = { HostListState.Failed((it as? ImageHostException)?.error ?: ImageHostError.Network) },
            )
        }
    }

    fun setGifOnly(value: Boolean) {
        _gifOnly.value = value
    }

    fun toggleHost(url: String) = _hostSelection.update { if (url in it) it - url else it + url }

    /** 按住拖动可连续多选: a drag selects every cell it passes over, never deselects. */
    fun selectHost(url: String) = _hostSelection.update { it + url }

    fun clearHostSelection() {
        _hostSelection.value = emptySet()
    }

    suspend fun addHostSelection(): List<String> {
        val loaded = (_host.value as? HostListState.Loaded)?.images.orEmpty()
        val chosen = loaded.filter { it.url in _hostSelection.value }
        _hostSelection.value = emptySet()
        return library.add(chosen.map { MySticker(it.url, stickerNameFromUrl(it.fileName)) })
    }

    // ---- 上传 -------------------------------------------------------------------------------------

    private val _keepOriginal = MutableStateFlow(true)
    val keepOriginal: StateFlow<Boolean> = _keepOriginal.asStateFlow()
    val uploads = library.uploads

    fun setKeepOriginal(value: Boolean) {
        _keepOriginal.value = value
    }

    fun upload(images: List<PickedImage>) = library.upload(images, _keepOriginal.value)

    fun retryUpload(id: String) = library.retryUpload(id)

    // ---- GitHub ----------------------------------------------------------------------------------

    val subscriptions: StateFlow<List<StickerSubscription>> =
        library.subscriptions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val cdn: StateFlow<StickerCdnSettings> =
        library.cdn.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StickerCdnSettings())

    /** Null for 新的源; otherwise the subscribed repository being re-picked (换文件夹). */
    private val _source = MutableStateFlow<String?>(null)
    val source: StateFlow<String?> = _source.asStateFlow()

    private val _repoUrl = MutableStateFlow("")
    val repoUrl: StateFlow<String> = _repoUrl.asStateFlow()

    private val _repo = MutableStateFlow<RepoState>(RepoState.Idle)
    val repo: StateFlow<RepoState> = _repo.asStateFlow()

    private val _folderSelection = MutableStateFlow<Set<String>>(emptySet())
    val folderSelection: StateFlow<Set<String>> = _folderSelection.asStateFlow()

    private var repoJob: Job? = null

    fun setRepoUrl(text: String) {
        _repoUrl.value = text
    }

    fun newSource() {
        repoJob?.cancel()
        _source.value = null
        _repoUrl.value = ""
        _repo.value = RepoState.Idle
        _folderSelection.value = emptySet()
    }

    /** Opens the GitHub tab on [slug] with its current folders ticked, on its own branch — 换文件夹. */
    fun editSource(slug: String) {
        _tab.value = StickerAddTab.GITHUB
        _source.value = slug
        _repoUrl.value = "github.com/$slug"
        val base = GitHubRepoRef.parse(slug) ?: return
        load {
            val subscription = subscribed { it == slug }
            base.copy(ref = subscription?.ref) to subscription
        }
    }

    fun loadRepo() {
        val ref = GitHubRepoRef.parse(_repoUrl.value)
        if (ref == null) {
            _repo.value = RepoState.BadUrl
            return
        }
        load {
            val existing = subscribed { it.equals(ref.slug, ignoreCase = true) }
            _source.value = existing?.slug
            ref to existing
        }
    }

    /**
     * Read from the store rather than [subscriptions].value, which holds anything only while
     * something collects it — and 换文件夹 calls [editSource] before the page that does is composed.
     */
    private suspend fun subscribed(slugMatches: (String) -> Boolean): StickerSubscription? =
        library.subscriptions.first().firstOrNull { slugMatches(it.slug) }

    /**
     * Lists the repository [resolve] names, ticking whichever of its folders the subscription it
     * also returns already holds.
     */
    private fun load(resolve: suspend () -> Pair<GitHubRepoRef, StickerSubscription?>) {
        repoJob?.cancel()
        _repo.value = RepoState.Loading
        _folderSelection.value = emptySet()
        repoJob = viewModelScope.launch {
            _repo.value = try {
                val (ref, subscription) = resolve()
                val preselect = subscription?.folders?.mapTo(HashSet()) { it.path }
                val listing = library.inspect(ref)
                val available = listing.folders.mapTo(HashSet()) { it.path }
                _folderSelection.value = preselect?.intersect(available).orEmpty()
                RepoState.Loaded(listing)
            } catch (e: StickerSourceException) {
                RepoState.Failed(e.error)
            }
        }
    }

    fun toggleFolder(path: String) = _folderSelection.update { if (path in it) it - path else it + path }

    fun selectAllFolders() {
        val listing = (_repo.value as? RepoState.Loaded)?.listing ?: return
        val all = listing.folders.mapTo(HashSet()) { it.path }
        _folderSelection.value = if (_folderSelection.value == all) emptySet() else all
    }

    /** @return false when there was nothing to subscribe to. */
    suspend fun subscribe(): Boolean {
        val listing = (_repo.value as? RepoState.Loaded)?.listing ?: return false
        val paths = _folderSelection.value
        if (paths.isEmpty()) return false
        library.subscribe(listing, paths)
        return true
    }

    private companion object {
        /** A paste is a handful of stickers; a page of links is a mistake, and each one is a request. */
        const val MAX_LINKS = 30

        /** Long enough to outlast a keystroke, short enough that a paste reads as instant. */
        const val PROBE_DEBOUNCE_MS = 400L
    }
}
