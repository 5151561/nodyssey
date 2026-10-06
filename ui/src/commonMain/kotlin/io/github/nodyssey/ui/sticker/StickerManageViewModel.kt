package io.github.nodyssey.ui.sticker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.nodyssey.data.imagehost.ImageHostConfig
import io.github.nodyssey.data.sticker.LinkProbe
import io.github.nodyssey.data.sticker.StickerCdn
import io.github.nodyssey.data.sticker.StickerCdnSettings
import io.github.nodyssey.data.sticker.StickerImportResult
import io.github.nodyssey.data.sticker.StickerLibrary
import io.github.nodyssey.data.sticker.StickerSourceError
import io.github.nodyssey.data.sticker.StickerSubscription
import io.github.plaza.designsys.editor.EmojiGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One CDN's speed, as 加载源 shows it beside the choice. */
sealed interface CdnLatency {
    data object Measuring : CdnLatency

    data class Millis(val value: Long) : CdnLatency

    /** Did not answer in time — or there was nothing subscribed to measure with. */
    data object Unreachable : CdnLatency
}

/** 导入's outcome, for one line under 备份. */
sealed interface ImportOutcome {
    data object Running : ImportOutcome

    data object NotABackup : ImportOutcome

    data class Done(val result: StickerImportResult) : ImportOutcome
}

/** 表情管理's front page (1g) and GitHub 订阅 (1i). */
class StickerManageViewModel(
    private val library: StickerLibrary,
    private val siteGroups: List<EmojiGroup>,
) : ViewModel() {
    // ---- 面板里的组 --------------------------------------------------------------------------------

    /**
     * The order a drag is building, shown in place of the stored one until the finger lifts — so a
     * drag across eight rows is one write, not eight.
     */
    private val dragOrder = MutableStateFlow<List<String>?>(null)

    /** What is stored, shared so [dragEnd] waits on the same emissions [groups] is drawn from. */
    private val stored: SharedFlow<List<StickerGroupItem>> =
        combine(library.mine, library.subscriptions, library.groupLayout) { mine, subscriptions, layout ->
            arrangeGroups(mine, subscriptions, layout, siteGroups)
        }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    internal val groups: StateFlow<List<StickerGroupItem>> =
        combine(stored, dragOrder) { stored, order ->
            if (order == null) {
                stored
            } else {
                val byKey = stored.associateBy { it.key }
                order.mapNotNull { byKey[it] } + stored.filterNot { it.key in order }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Moves the dragged row to where [targetKey] is, in the order being built. */
    fun dragMove(
        draggedKey: String,
        targetKey: String,
    ) {
        val current = dragOrder.value ?: groups.value.map { it.key }
        val next = current.moved(draggedKey, targetKey)
        if (next !== current) dragOrder.value = next
    }

    /**
     * Stores the dragged order, and keeps showing it until the store hands it back: dropping it as
     * soon as the write returns would draw the order from before the drag for a frame.
     */
    fun dragEnd() {
        val order = dragOrder.value ?: return
        viewModelScope.launch {
            library.reorderGroups(order)
            stored.first { list -> list.map { it.key }.hasOrder(order) }
            // A drag started meanwhile is building its own order; that one is not ours to drop.
            dragOrder.compareAndSet(order, null)
        }
    }

    fun setHidden(
        key: String,
        hidden: Boolean,
    ) {
        viewModelScope.launch { library.setGroupHidden(key, hidden) }
    }

    /** How many of 我的's links did not answer the last check — 「2 张失效」. */
    val brokenMine: StateFlow<Int> =
        combine(library.mine, library.linkProbes) { mine, probes ->
            mine.count { sticker -> probes[sticker.url]?.let { it !is LinkProbe.Ok } == true }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private var linksChecked = false

    /** Asks every saved link once per opening of this page; 我的's own page reads the answers. */
    fun checkLinks() {
        if (linksChecked) return
        linksChecked = true
        viewModelScope.launch { library.probeMine() }
    }

    // ---- 来源 -------------------------------------------------------------------------------------

    val subscriptions: StateFlow<List<StickerSubscription>> =
        library.subscriptions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Each repository the last check could not read, and why — 仓库找不到了 on 1i. */
    val sourceErrors: StateFlow<Map<String, StickerSourceError>> = library.sourceErrors

    val hostConfig: StateFlow<ImageHostConfig?> =
        library.imageHostConfig.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
            // Read rather than [cdn].value: the page measures as it opens, before anything collects that.
            val settings = library.cdn.first()
            val targets = StickerCdn.entries.filter { it != StickerCdn.CUSTOM || settings.normalizedCustomBase() != null }
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
            val error = library.checkForUpdates(force)
            // Queried afresh: [subscriptions] has not been handed the check's writes yet, and has no
            // collector at all on the check the page runs as it opens.
            val upToDate = force && error == null && library.subscriptions.first().none { it.hasUpdate }
            _checkError.value = error
            _checking.value = false
            _checkedUpToDate.value = upToDate
        }
    }

    fun applyUpdate(slug: String) {
        viewModelScope.launch { library.applyUpdate(slug) }
    }

    fun unsubscribe(slug: String) {
        viewModelScope.launch { library.unsubscribe(slug) }
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

    // ---- 备份 -------------------------------------------------------------------------------------

    suspend fun exportBackup(): String = library.exportBackup()

    private val _import = MutableStateFlow<ImportOutcome?>(null)
    val import: StateFlow<ImportOutcome?> = _import.asStateFlow()

    /** Forgets the last import, so the dialog opens on an empty field rather than on its result. */
    fun resetImport() {
        if (_import.value != ImportOutcome.Running) _import.value = null
    }

    fun importBackup(text: String) {
        if (_import.value == ImportOutcome.Running) return
        _import.value = ImportOutcome.Running
        viewModelScope.launch {
            _import.value = library.importBackup(text)?.let(ImportOutcome::Done) ?: ImportOutcome.NotABackup
        }
    }
}

/** Whether these keys stand in [order] — ignoring any added or removed since it was taken. */
internal fun List<String>.hasOrder(order: List<String>): Boolean {
    val wanted = order.toSet()
    val kept = filter { it in wanted }
    val present = kept.toSet()
    return kept == order.filter { it in present }
}
