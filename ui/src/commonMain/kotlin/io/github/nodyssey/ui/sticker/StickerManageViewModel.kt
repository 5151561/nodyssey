package io.github.nodyssey.ui.sticker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.nodyssey.data.sticker.LinkProbe
import io.github.nodyssey.data.sticker.MySticker
import io.github.nodyssey.data.sticker.StickerCdn
import io.github.nodyssey.data.sticker.StickerCdnSettings
import io.github.nodyssey.data.sticker.StickerLibrary
import io.github.nodyssey.data.sticker.StickerSourceError
import io.github.nodyssey.data.sticker.StickerSubscription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** A saved link that did not answer, and how. */
data class BrokenSticker(
    val sticker: MySticker,
    val probe: LinkProbe,
)

/** One CDN's speed, as 2c shows it beside the choice. */
sealed interface CdnLatency {
    data object Measuring : CdnLatency

    data class Millis(val value: Long) : CdnLatency

    /** Did not answer in time — or there was nothing subscribed to measure with. */
    data object Unreachable : CdnLatency
}

/** 表情管理 (1f, 2c). */
class StickerManageViewModel(
    private val library: StickerLibrary,
) : ViewModel() {
    // ---- 我的 -------------------------------------------------------------------------------------

    /**
     * The order a drag is building, shown in place of the stored one until the finger lifts — so a
     * drag across twenty cells is one write, not twenty.
     */
    private val dragOrder = MutableStateFlow<List<String>?>(null)

    val mine: StateFlow<List<MySticker>> =
        combine(library.mine, dragOrder) { stored, order ->
            if (order == null) {
                stored
            } else {
                val byUrl = stored.associateBy { it.url }
                order.mapNotNull { byUrl[it] } + stored.filterNot { it.url in order }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private val probes = MutableStateFlow<Map<String, LinkProbe>>(emptyMap())

    /** The saved links that did not answer — 2 张加载不出来 — in 我的's order. */
    val broken: StateFlow<List<BrokenSticker>> =
        combine(library.mine, probes) { list, results ->
            list.mapNotNull { sticker ->
                results[sticker.url]?.takeIf { it !is LinkProbe.Ok }?.let { BrokenSticker(sticker, it) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggle(url: String) = _selection.update { if (url in it) it - url else it + url }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    fun deleteSelected() {
        val urls = _selection.value
        _selection.value = emptySet()
        viewModelScope.launch { library.remove(urls) }
    }

    fun moveSelectedToTop() {
        val urls = _selection.value
        _selection.value = emptySet()
        viewModelScope.launch { library.moveToTop(urls) }
    }

    /** Moves the dragged cell to where [targetUrl] is, in the order being built. */
    fun dragMove(
        draggedUrl: String,
        targetUrl: String,
    ) {
        if (draggedUrl == targetUrl) return
        val current = (dragOrder.value ?: mine.value.map { it.url }).toMutableList()
        val from = current.indexOf(draggedUrl)
        val to = current.indexOf(targetUrl)
        if (from < 0 || to < 0) return
        current.add(to, current.removeAt(from))
        dragOrder.value = current
    }

    fun dragEnd() {
        val order = dragOrder.value ?: return
        viewModelScope.launch {
            library.reorder(order)
            dragOrder.value = null
        }
    }

    /**
     * HEADs every saved link, four at a time. Once per opening: a link that broke an hour ago is the
     * same news an hour later, and each one is a request to somebody's image host.
     */
    fun checkLinks() {
        if (probes.value.isNotEmpty()) return
        viewModelScope.launch {
            val gate = Semaphore(PROBE_PARALLELISM)
            library.mine.first().forEach { sticker ->
                launch {
                    val result = gate.withPermit { library.probe(sticker.url) }
                    probes.update { it + (sticker.url to result) }
                }
            }
        }
    }

    fun removeBroken() {
        val urls = broken.value.map { it.sticker.url }
        viewModelScope.launch { library.remove(urls) }
    }

    /** 导出链接: every link, one per line — what 添加表情 › 链接 takes back in one paste. */
    fun exportText(): String = mine.value.joinToString("\n") { it.url }

    // ---- 订阅 -------------------------------------------------------------------------------------

    val subscriptions: StateFlow<List<StickerSubscription>> =
        library.subscriptions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val cdn: StateFlow<StickerCdnSettings> =
        library.cdn.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StickerCdnSettings())

    private val _latency = MutableStateFlow<Map<StickerCdn, CdnLatency>>(emptyMap())
    val latency: StateFlow<Map<StickerCdn, CdnLatency>> = _latency.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    /** The last check's failure, for one line under the list; null when it went through. */
    private val _checkError = MutableStateFlow<StickerSourceError?>(null)
    val checkError: StateFlow<StickerSourceError?> = _checkError.asStateFlow()

    private val _checkedUpToDate = MutableStateFlow(false)
    val checkedUpToDate: StateFlow<Boolean> = _checkedUpToDate.asStateFlow()

    /** 每次打开这一页会测一下各个源的速度. */
    fun measureCdns() {
        viewModelScope.launch {
            val targets = StickerCdn.entries.filter { it != StickerCdn.CUSTOM || cdn.value.normalizedCustomBase() != null }
            _latency.value = targets.associateWith { CdnLatency.Measuring }
            targets.forEach { target ->
                launch {
                    val millis = library.measure(target)
                    _latency.update { it + (target to (millis?.let(CdnLatency::Millis) ?: CdnLatency.Unreachable)) }
                }
            }
        }
    }

    fun checkUpdates(force: Boolean) {
        if (_checking.value) return
        viewModelScope.launch {
            _checking.value = true
            _checkError.value = library.checkForUpdates(force)
            _checking.value = false
            _checkedUpToDate.value = force && _checkError.value == null && subscriptions.value.none { it.hasUpdate }
        }
    }

    fun applyUpdate(slug: String) {
        viewModelScope.launch { library.applyUpdate(slug) }
    }

    fun unsubscribe(slug: String) {
        viewModelScope.launch { library.unsubscribe(slug) }
    }

    fun setHidden(
        slug: String,
        path: String,
        hidden: Boolean,
    ) {
        viewModelScope.launch { library.setFolderHidden(slug, path, hidden) }
    }

    /** Moves the folder at [from] of [slug] to [to] and stores the new order. */
    fun moveFolder(
        slug: String,
        from: Int,
        to: Int,
    ) {
        val folders = subscriptions.value.firstOrNull { it.slug == slug }?.folders ?: return
        if (from !in folders.indices || to !in folders.indices || from == to) return
        val paths = folders.map { it.path }.toMutableList()
        paths.add(to, paths.removeAt(from))
        viewModelScope.launch { library.reorderFolders(slug, paths) }
    }

    fun selectCdn(choice: StickerCdn) {
        viewModelScope.launch { library.setCdn(choice) }
    }

    fun setCustomCdn(base: String) {
        viewModelScope.launch {
            library.setCustomCdnBase(base)
            library.setCdn(StickerCdn.CUSTOM)
            measureCdns()
        }
    }

    private companion object {
        const val PROBE_PARALLELISM = 4
    }
}
