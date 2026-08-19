package io.github.plaza.designsys.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.resources.Res
import io.github.plaza.designsys.resources.composer_emoji_backspace
import io.github.plaza.designsys.resources.composer_emoji_recent
import io.github.plaza.designsys.theme.Spacing
import org.jetbrains.compose.resources.stringResource

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
)

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
 * The emoji panel from C6, shared by a post editor and a reply sheet.
 *
 * It replaces the keyboard rather than stacking on top of it — the board is explicit about this
 * ("面板与键盘同高切换，不叠加"), and on a 360×800 screen a panel that stacks leaves two lines of
 * the reply visible. The caller is responsible for dismissing the IME before showing it.
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
    // Opens on the first group that has anything in it, so the panel is useful the moment it shows.
    var selectedIndex by rememberSaveable { mutableIntStateOf(groups.indexOfFirst { it.entries.isNotEmpty() }.coerceAtLeast(0)) }
    val group = groups.getOrNull(selectedIndex) ?: groups.first()
    val entriesByInsertion = remember(groups) {
        groups.flatMap(EmojiGroup::entries).associateBy(EmojiEntry::insertion)
    }

    fun insert(text: String) {
        onInsert(text)
        onRecentChange((listOf(text) + recent.filterNot { it == text }).take(RECENT_LIMIT))
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp)) {
                groups.forEachIndexed { index, candidate ->
                    GroupPill(
                        title = candidate.title(),
                        selected = index == selectedIndex,
                        onClick = { selectedIndex = index },
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(GRID_HEIGHT)) {
                if (group.entries.isEmpty()) {
                    Text(
                        text = emptyGroupText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = Spacing.xl),
                    )
                } else {
                    EmojiGrid(
                        entries = group.entries,
                        onSelect = { insert(it.insertion) },
                        stickerImage = stickerImage,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RecentRow(
                    recent = recent,
                    entriesByInsertion = entriesByInsertion,
                    onSelect = ::insert,
                    stickerImage = stickerImage,
                    modifier = Modifier.weight(1f),
                )
                BackspaceKey(onClick = onBackspace)
            }
        }
    }
}

@Composable
private fun GroupPill(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // The selectable overload, so a screen reader hears which group is open.
    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }
}

@Composable
private fun EmojiGrid(
    entries: List<EmojiEntry>,
    onSelect: (EmojiEntry) -> Unit,
    stickerImage: @Composable (EmojiEntry.Sticker, String?, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(entries, key = { it.insertion }) { entry ->
            EmojiCell(entry = entry, onClick = { onSelect(entry) }, stickerImage = stickerImage)
        }
    }
}

@Composable
private fun EmojiCell(
    entry: EmojiEntry,
    onClick: () -> Unit,
    stickerImage: @Composable (EmojiEntry.Sticker, String?, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = when (entry) {
        is EmojiEntry.Unicode -> entry.character
        is EmojiEntry.Sticker -> entry.name
    }
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        when (entry) {
            is EmojiEntry.Unicode -> Text(entry.character, fontSize = 24.sp)
            is EmojiEntry.Sticker -> stickerImage(entry, null, Modifier.size(STICKER_SIZE))
        }
    }
}

@Composable
private fun RecentRow(
    recent: List<String>,
    entriesByInsertion: Map<String, EmojiEntry>,
    onSelect: (String) -> Unit,
    stickerImage: @Composable (EmojiEntry.Sticker, String?, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = PlazaIcons.History,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(Res.string.composer_emoji_recent),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xs, end = Spacing.sm),
        )
        recent.forEach { insertion ->
            val entry = entriesByInsertion[insertion]
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(insertion) },
                contentAlignment = Alignment.Center,
            ) {
                when (entry) {
                    is EmojiEntry.Sticker ->
                        stickerImage(entry, entry.name, Modifier.size(RECENT_STICKER_SIZE))

                    is EmojiEntry.Unicode -> Text(entry.character, fontSize = 18.sp)

                    null -> Text(insertion, fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun BackspaceKey(onClick: () -> Unit) {
    val description = stringResource(Res.string.composer_emoji_backspace)
    Box(
        modifier = Modifier
            .width(56.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = PlazaIcons.Backspace,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val COLUMNS = 6
private const val RECENT_LIMIT = 6
private val GRID_HEIGHT = 162.dp
private val STICKER_SIZE = 36.dp
private val RECENT_STICKER_SIZE = 26.dp
