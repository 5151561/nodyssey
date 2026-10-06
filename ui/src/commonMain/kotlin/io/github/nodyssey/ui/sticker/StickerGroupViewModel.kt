package io.github.nodyssey.ui.sticker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.nodyssey.data.sticker.LinkProbe
import io.github.nodyssey.data.sticker.MySticker
import io.github.nodyssey.data.sticker.StickerCdnSettings
import io.github.nodyssey.data.sticker.StickerLibrary
import io.github.plaza.designsys.editor.EmojiEntry
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

/** One cell of 1h. [id] is the link for a picture, the character for a Unicode emoji. */
data class GroupCell(
    val id: String,
    val name: String,
    /** Null for a Unicode emoji, which is drawn as text and cannot be saved to 我的. */
    val url: String?,
    /** The link did not answer the last check (我的 only). */
    val broken: Boolean = false,
)

/** What 1h shows: the group, its cells in display order, and how many of them are broken. */
internal data class GroupPage(
    val item: StickerGroupItem,
    val cells: List<GroupCell>,
    val brokenCount: Int,
)

/** 组详情 (1h): one group's pictures, any of the three kinds. */
class StickerGroupViewModel(
    private val library: StickerLibrary,
    val key: String,
    private val siteGroups: List<EmojiGroup>,
) : ViewModel() {
    /**
     * 我的's order while a drag builds it, shown in place of the stored one until the finger lifts —
     * so a drag across twenty cells is one write, not twenty.
     */
    private val dragOrder = MutableStateFlow<List<String>?>(null)

    private val storedMine: SharedFlow<List<MySticker>> =
        library.mine.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    internal val page: StateFlow<GroupPage?> =
        combine(
            combine(storedMine, dragOrder, ::applyDrag),
            library.subscriptions,
            library.groupLayout,
            library.cdn,
            library.linkProbes,
        ) { mine, subscriptions, layout, cdn, probes ->
            val item = arrangeGroups(mine, subscriptions, layout, siteGroups).firstOrNull { it.key == key }
            item?.let { pageOf(it, cdn, probes) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun applyDrag(
        stored: List<MySticker>,
        order: List<String>?,
    ): List<MySticker> {
        if (order == null) return stored
        val byUrl = stored.associateBy { it.url }
        return order.mapNotNull { byUrl[it] } + stored.filterNot { it.url in order }
    }

    private fun pageOf(
        item: StickerGroupItem,
        cdn: StickerCdnSettings,
        probes: Map<String, LinkProbe>,
    ): GroupPage =
        when (item) {
            is StickerGroupItem.Mine -> {
                val cells = item.stickers.map { sticker ->
                    GroupCell(sticker.url, sticker.name, sticker.url, broken = probes[sticker.url]?.let { it !is LinkProbe.Ok } == true)
                }
                // 「已排到最后」: on screen only — the stored order is the reader's, and a link that
                // is down today may be back tomorrow in the place it was.
                val (ok, broken) = cells.partition { !it.broken }
                GroupPage(item, ok + broken, broken.size)
            }

            is StickerGroupItem.Folder -> GroupPage(item, folderEntries(item.folder, cdn).map { GroupCell(it.url, it.name, it.url) }, 0)

            is StickerGroupItem.Site -> GroupPage(
                item,
                item.group.entries.map { entry ->
                    when (entry) {
                        is EmojiEntry.Sticker -> GroupCell(entry.url, entry.name, entry.url)
                        is EmojiEntry.Unicode -> GroupCell(entry.character, entry.character, url = null)
                    }
                },
                0,
            )
        }

    // ---- 多选 -------------------------------------------------------------------------------------

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private val _selecting = MutableStateFlow(false)

    /** In 多选 — entered from the bar's checklist button, and left with nothing chosen still in it. */
    val selecting: StateFlow<Boolean> = _selecting.asStateFlow()

    fun startSelecting() {
        _selecting.value = true
    }

    fun toggle(id: String) {
        _selecting.value = true
        _selection.update { if (id in it) it - id else it + id }
    }

    fun clearSelection() {
        _selection.value = emptySet()
        _selecting.value = false
    }

    fun deleteSelected() {
        val urls = _selection.value
        clearSelection()
        viewModelScope.launch { library.remove(urls) }
    }

    fun moveSelectedToTop() {
        val urls = _selection.value
        clearSelection()
        viewModelScope.launch { library.moveToTop(urls) }
    }

    /**
     * 收藏到我的 for everything chosen in a subscribed or site group. @return through [onAdded] the
     * links that were new — what an undo takes back out.
     */
    fun saveSelectedToMine(onAdded: (List<String>) -> Unit) {
        val chosen = _selection.value
        val cells = page.value?.cells.orEmpty().filter { it.id in chosen && it.url != null }
        clearSelection()
        viewModelScope.launch { onAdded(library.add(cells.map { MySticker(it.url!!, it.name) })) }
    }

    // ---- 一张 -------------------------------------------------------------------------------------

    fun saveToMine(
        cell: GroupCell,
        onAdded: (List<String>) -> Unit,
    ) {
        val url = cell.url ?: return
        viewModelScope.launch { onAdded(library.add(listOf(MySticker(url, cell.name)))) }
    }

    fun undoAdd(urls: List<String>) {
        viewModelScope.launch { library.remove(urls) }
    }

    fun rename(
        url: String,
        name: String,
    ) {
        viewModelScope.launch { library.rename(url, name) }
    }

    fun moveToTop(url: String) {
        viewModelScope.launch { library.moveToTop(listOf(url)) }
    }

    fun delete(url: String) {
        viewModelScope.launch { library.remove(listOf(url)) }
    }

    fun removeBroken() {
        val urls = page.value?.cells.orEmpty().filter { it.broken }.map { it.id }
        viewModelScope.launch { library.remove(urls) }
    }

    fun setHidden(hidden: Boolean) {
        viewModelScope.launch { library.setGroupHidden(key, hidden) }
    }

    /** 导出链接: every link of 我的, one per line — what 添加表情 › 链接 takes back in one paste. */
    fun exportLinks(): String = page.value?.cells.orEmpty().mapNotNull { it.url }.joinToString("\n")

    // ---- 拖动排序 (我的) ----------------------------------------------------------------------------

    /** Moves the dragged cell to where [targetUrl] is, in the order being built. */
    fun dragMove(
        draggedUrl: String,
        targetUrl: String,
    ) {
        val current = dragOrder.value ?: (page.value?.item as? StickerGroupItem.Mine)?.stickers?.map { it.url } ?: return
        val next = current.moved(draggedUrl, targetUrl)
        if (next !== current) dragOrder.value = next
    }

    /**
     * Stores the dragged order, and keeps showing it until Room hands it back: dropping it as soon
     * as the write returns would draw the stored order from before the drag for the frame or two
     * before the query re-runs.
     */
    fun dragEnd() {
        val order = dragOrder.value ?: return
        viewModelScope.launch {
            library.reorder(order)
            storedMine.first { list -> list.map { it.url }.hasOrder(order) }
            // A drag started meanwhile is building its own order; that one is not ours to drop.
            dragOrder.compareAndSet(order, null)
        }
    }
}
