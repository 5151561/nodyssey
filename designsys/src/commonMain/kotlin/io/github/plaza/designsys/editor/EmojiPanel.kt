package io.github.plaza.designsys.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.resources.Res
import io.github.plaza.designsys.resources.composer_emoji_backspace
import io.github.plaza.designsys.resources.composer_emoji_recent
import io.github.plaza.designsys.resources.composer_emoji_recent_empty
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.cardBorder
import io.github.plaza.designsys.theme.cardBorderStroke
import io.github.plaza.designsys.theme.cardShadow
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil

/**
 * One tab of the panel.
 *
 * [title] is a composable lambda rather than a `String` because a forum's packs are fixed and the
 * groups are usually built once, outside composition — where there is nothing to resolve a string
 * with. It used to be a `@StringRes Int` for the same reason, which stopped working the moment this
 * module had a target where an Android resource id means nothing; a lambda keeps the laziness
 * without naming anyone's resource system.
 */
data class EmojiGroup(
    val title: @Composable () -> String,
    val entries: List<EmojiEntry>,
    /**
     * What the open tab is remembered by. Blank falls back to the group's position, which is fine
     * for a fixed list and wrong for one that grows while the panel is open — a group that arrives
     * from storage a frame late would otherwise shift every tab after it under the reader's finger.
     */
    val key: String = "",
    /** Drawn before [title] in the pill — 我的's heart. */
    val icon: ImageVector? = null,
    /** A dot on the pill: something in the group wants looking at, an update for instance. */
    val badge: Boolean = false,
    /** Shown above the grid while the group is open — an update notice, say. */
    val header: (@Composable () -> Unit)? = null,
    /** A cell before the entries, scrolling with them — where 我的 puts its 添加. */
    val leadingCell: (@Composable (Modifier) -> Unit)? = null,
    /**
     * Long-press menu items for one entry, drawn inside a [DropdownMenu] anchored to its cell; call
     * the second argument to close it. Null means a long press does nothing.
     */
    val entryMenu: (@Composable ColumnScope.(entry: EmojiEntry, dismiss: () -> Unit) -> Unit)? = null,
    /** What an empty group says instead of the panel's shared `emptyGroupText`. */
    val emptyText: String? = null,
)

/**
 * A recent that was a Markdown image — a saved sticker since deleted, a pack since unsubscribed —
 * still draws as its picture rather than as a line of Markdown in a cell.
 */
private fun markdownImageSticker(insertion: String): EmojiEntry.Sticker? =
    MARKDOWN_IMAGE.matchEntire(insertion.trim())?.let { match ->
        EmojiEntry.Sticker(name = match.groupValues[1], shortcode = insertion, url = match.groupValues[2])
    }

private val MARKDOWN_IMAGE = Regex("""!\[([^\]]*)]\((https://[^)\s]+)\)""")

private fun EmojiGroup.selectionKey(index: Int): String = key.ifEmpty { "#$index" }

sealed interface EmojiEntry {
    /** Inserted as-is. Plain Unicode, which is why it needs nothing from the site. */
    data class Unicode(val character: String) : EmojiEntry

    /**
     * A picture the site renders from a shortcode.
     *
     * [url] is what the preview grid loads; [shortcode] is what goes into the text. Keeping those
     * apart is the whole reason this is not one field — the preview is ours to choose, but a
     * published post has to carry the site's own shortcode so the site's own renderer draws it.
     */
    data class Sticker(
        val name: String,
        val shortcode: String,
        val url: String,
    ) : EmojiEntry
}

val EmojiEntry.insertion: String
    get() = when (this) {
        is EmojiEntry.Unicode -> character
        is EmojiEntry.Sticker -> shortcode
    }

/**
 * The emoji panel from 2c, shared by the post editor, the reply sheet and the message bar.
 *
 * It replaces the keyboard rather than stacking on top of it — "面板与键盘同高切换，不叠加" — and on
 * a 360×800 screen a panel that stacks leaves two lines of the reply visible. The caller is
 * responsible for dismissing the IME before showing it.
 *
 * 最近使用 is the first group pill rather than a strip under the grid (as it was until 2c): the
 * recents are what a writer reaches for most, and a tab shows as many of them as the grid holds
 * instead of the six a strip had room for. The panel opens on it only once there is something in it;
 * before that it opens on the first pack, so the first thing a new writer sees is stickers.
 *
 * The backspace key floats at the grid's bottom-end corner rather than living in a cell of it: a
 * cell would scroll away with the stickers, and a backspace that moves is not one a thumb can find.
 * The grid is padded by one row at its end so its last stickers scroll clear of it.
 *
 * [stickerImage] is a slot rather than an `AsyncImage` in here because how a preview is *fetched* is
 * the app's business: these are waived past a 仅 Wi-Fi 加载图片 setting this module knows nothing
 * about. [emptyGroupText] is a parameter for the same kind of reason — a group is empty for a reason
 * only the app can state.
 *
 * [recent] is hoisted rather than remembered here: the panel is conditionally composed, so any
 * state it held itself would be thrown away every time the panel closes — which is exactly when
 * the recents were just used and are worth keeping.
 */
@Composable
fun EmojiPanel(
    groups: List<EmojiGroup>,
    onInsert: (String) -> Unit,
    onBackspace: () -> Unit,
    recent: List<String>,
    onRecentChange: (List<String>) -> Unit,
    emptyGroupText: String,
    stickerImage: @Composable (sticker: EmojiEntry.Sticker, contentDescription: String?, modifier: Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The recents when there are any to open on; otherwise the first group that has anything in it,
    // so the panel is useful the moment it shows.
    var selectedKey by rememberSaveable {
        mutableStateOf(
            if (recent.isNotEmpty()) {
                RECENT_KEY
            } else {
                val first = groups.indexOfFirst { it.entries.isNotEmpty() }.coerceAtLeast(0)
                groups.getOrNull(first)?.selectionKey(first) ?: RECENT_KEY
            },
        )
    }
    val selectedIndex = if (selectedKey == RECENT_KEY) {
        RECENT
    } else {
        groups.indices.firstOrNull { groups[it].selectionKey(it) == selectedKey } ?: 0
    }
    val selectedGroup = groups.getOrNull(selectedIndex).takeIf { selectedIndex != RECENT }
    val entriesByInsertion = remember(groups) {
        groups.flatMap(EmojiGroup::entries).associateBy(EmojiEntry::insertion)
    }
    // A recent no group knows any more — a pack the site dropped — is still text that can be
    // inserted, so it stays in the tab as what it inserts.
    val entries = if (selectedIndex == RECENT) {
        recent.map { entriesByInsertion[it] ?: markdownImageSticker(it) ?: EmojiEntry.Unicode(it) }
    } else {
        selectedGroup?.entries.orEmpty()
    }

    fun insert(text: String) {
        onInsert(text)
        onRecentChange((listOf(text) + recent.filterNot { it == text }).take(RECENT_LIMIT))
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.md, end = Spacing.md, top = 10.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GroupPill(
                    title = stringResource(Res.string.composer_emoji_recent),
                    selected = selectedIndex == RECENT,
                    onClick = { selectedKey = RECENT_KEY },
                )
                groups.forEachIndexed { index, candidate ->
                    GroupPill(
                        title = candidate.title(),
                        selected = index == selectedIndex,
                        onClick = { selectedKey = candidate.selectionKey(index) },
                        icon = candidate.icon,
                        badge = candidate.badge,
                    )
                }
            }
            selectedGroup?.header?.invoke()
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // The panel is sized from its cells rather than the other way round: square cells
                // under a fixed height showed a different number of rows on every width and left the
                // backspace key between two of them. More columns, not bigger cells, on a wide sheet.
                val columns = maxOf(COLUMNS, ceil((maxWidth + CELL_GAP) / (MAX_CELL + CELL_GAP)).toInt())
                val cell = (maxWidth - CELL_GAP * (columns - 1)) / columns
                Box(Modifier.fillMaxWidth().height(cell * VISIBLE_ROWS + CELL_GAP * (VISIBLE_ROWS - 1))) {
                    val leadingCell = selectedGroup?.leadingCell
                    if (entries.isNotEmpty() || leadingCell != null) {
                        EmojiGrid(
                            entries = entries,
                            columns = columns,
                            onSelect = { insert(it.insertion) },
                            stickerImage = stickerImage,
                            bottomClearance = cell + CELL_GAP,
                            leadingCell = leadingCell,
                            entryMenu = selectedGroup?.entryMenu,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (entries.isEmpty()) {
                        Text(
                            text = if (selectedIndex == RECENT) {
                                stringResource(Res.string.composer_emoji_recent_empty)
                            } else {
                                selectedGroup?.emptyText ?: emptyGroupText
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center).padding(horizontal = Spacing.xl),
                        )
                    }
                    BackspaceKey(onClick = onBackspace, modifier = Modifier.align(Alignment.BottomEnd).size(cell))
                }
            }
        }
    }
}

@Composable
private fun GroupPill(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    badge: Boolean = false,
) {
    val layers = LocalPlazaLayers.current
    // The selectable overload, so a screen reader hears which group is open.
    Surface(
        selected = selected,
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.inverseSurface else layers.raised,
        contentColor = if (selected) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface,
        border = if (selected) null else layers.cardBorderStroke,
        // 28dp drawn; Material still hit-tests the pill at 48.
        modifier = Modifier.height(28.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
            if (badge) Badge()
        }
    }
}

@Composable
private fun EmojiGrid(
    entries: List<EmojiEntry>,
    columns: Int,
    onSelect: (EmojiEntry) -> Unit,
    stickerImage: @Composable (EmojiEntry.Sticker, String?, Modifier) -> Unit,
    bottomClearance: Dp,
    leadingCell: (@Composable (Modifier) -> Unit)?,
    entryMenu: (@Composable ColumnScope.(EmojiEntry, () -> Unit) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier,
        contentPadding = PaddingValues(bottom = bottomClearance),
        horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
        verticalArrangement = Arrangement.spacedBy(CELL_GAP),
    ) {
        if (leadingCell != null) {
            item(key = LEADING_KEY) { leadingCell(Modifier.aspectRatio(1f)) }
        }
        items(entries, key = { it.insertion }) { entry ->
            EmojiCell(entry = entry, onClick = { onSelect(entry) }, stickerImage = stickerImage, menu = entryMenu)
        }
    }
}

@Composable
private fun EmojiCell(
    entry: EmojiEntry,
    onClick: () -> Unit,
    stickerImage: @Composable (EmojiEntry.Sticker, String?, Modifier) -> Unit,
    menu: (@Composable ColumnScope.(EmojiEntry, () -> Unit) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val description = when (entry) {
        is EmojiEntry.Unicode -> entry.character
        is EmojiEntry.Sticker -> entry.name
    }
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (menu != null) {
                    { menuOpen = true }
                } else {
                    null
                },
            ).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        when (entry) {
            is EmojiEntry.Unicode -> Text(entry.character, fontSize = 24.sp)
            is EmojiEntry.Sticker -> stickerImage(entry, null, Modifier.size(STICKER_SIZE))
        }
        if (menu != null) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                menu(entry) { menuOpen = false }
            }
        }
    }
}

/** Raised where the cells are recessed, so it reads as a key over the grid rather than a cell of it. */
@Composable
private fun BackspaceKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layers = LocalPlazaLayers.current
    val description = stringResource(Res.string.composer_emoji_backspace)
    val shape = MaterialTheme.shapes.small
    Box(
        modifier = modifier
            .cardShadow(shape, layers.shadows)
            .clip(shape)
            .background(layers.raised)
            .cardBorder(layers, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = PlazaIcons.Backspace,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private const val COLUMNS = 6
private const val RECENT = -1
private const val RECENT_KEY = "recent"
private const val LEADING_KEY = "\u0000leading"

/** Every sticker ever inserted is not a useful tab; three rows of the grid is. */
private const val RECENT_LIMIT = 18
private val CELL_GAP = 6.dp
private const val VISIBLE_ROWS = 3

/** Past this a wide sheet gets another column instead of bigger cells. */
private val MAX_CELL = 64.dp
private val STICKER_SIZE = 36.dp
