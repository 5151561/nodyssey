package io.github.nodyssey.ui.composer

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nodyssey.R
import io.github.nodyssey.ui.common.NodysseyIcons
import io.github.nodyssey.ui.theme.Spacing

/**
 * One of the five groups NodeSeek's own editor offers.
 *
 * The first three are image stickers hosted by the site. Their asset URLs have never been captured
 * — the sandbox cannot reach nodeseek.com — and inserting a guessed URL would publish a broken
 * image into a real thread, so those groups carry no entries and say so. Filling them in is a change
 * to this list alone: give the group its [EmojiEntry.Sticker] items and the panel starts working.
 */
data class EmojiGroup(
    @param:StringRes val titleRes: Int,
    val entries: List<EmojiEntry>,
)

sealed interface EmojiEntry {
    /** Inserted as-is. Fluent and App are plain Unicode, which is why they work today. */
    data class Unicode(val character: String) : EmojiEntry

    /** Inserted as a Markdown image, the way the site stores stickers. */
    data class Sticker(val name: String, val url: String) : EmojiEntry
}

val EmojiEntry.insertion: String
    get() = when (this) {
        is EmojiEntry.Unicode -> character
        is EmojiEntry.Sticker -> "![$name]($url)"
    }

val NodeSeekEmojiGroups = listOf(
    EmojiGroup(R.string.composer_emoji_group_acn, emptyList()),
    EmojiGroup(R.string.composer_emoji_group_onion, emptyList()),
    EmojiGroup(R.string.composer_emoji_group_chick, emptyList()),
    EmojiGroup(
        R.string.composer_emoji_group_fluent,
        listOf(
            "😀", "😄", "😅", "🤣", "🙂", "😉",
            "😍", "😘", "🤔", "😐", "😴", "😭",
            "😡", "👍", "👎", "🎉", "❤️", "🔥",
        ).map(EmojiEntry::Unicode),
    ),
    EmojiGroup(
        R.string.composer_emoji_group_app,
        listOf(
            "🍗", "✨", "🐧", "💻", "🌐", "🚀",
            "📦", "🔧", "🐛", "📈", "💰", "🛒",
            "🎯", "⚡", "🧪", "📌", "🔒", "🧵",
        ).map(EmojiEntry::Unicode),
    ),
)

/**
 * The emoji panel from C6, shared by the post editor and the reply sheet.
 *
 * It replaces the keyboard rather than stacking on top of it — the board is explicit about this
 * ("面板与键盘同高切换，不叠加"), and on a 360×800 screen a panel that stacks leaves two lines of
 * the reply visible. The caller is responsible for dismissing the IME before showing it.
 *
 * [recent] is hoisted rather than remembered here: the panel is conditionally composed, so any
 * state it held itself would be thrown away every time the panel closes — which is exactly when
 * the recents were just used and are worth keeping.
 */
@Composable
fun EmojiPanel(
    onInsert: (String) -> Unit,
    onBackspace: () -> Unit,
    recent: List<String>,
    onRecentChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    groups: List<EmojiGroup> = NodeSeekEmojiGroups,
) {
    // Opens on the first group that has anything in it, so the panel is useful the moment it shows.
    var selectedIndex by rememberSaveable { mutableIntStateOf(groups.indexOfFirst { it.entries.isNotEmpty() }.coerceAtLeast(0)) }
    val group = groups.getOrNull(selectedIndex) ?: groups.first()

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
                        title = stringResource(candidate.titleRes),
                        selected = index == selectedIndex,
                        onClick = { selectedIndex = index },
                    )
                }
            }
            Box(Modifier.fillMaxWidth().heightIn(min = GRID_MIN_HEIGHT)) {
                if (group.entries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.composer_emoji_stickers_pending),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = Spacing.xl),
                    )
                } else {
                    EmojiGrid(entries = group.entries, onSelect = { insert(it.insertion) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RecentRow(recent = recent, onSelect = ::insert, modifier = Modifier.weight(1f))
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
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        entries.chunked(COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                row.forEach { entry ->
                    EmojiCell(entry = entry, onClick = { onSelect(entry) }, modifier = Modifier.weight(1f))
                }
                // Keeps the last, partly filled row aligned with the ones above it.
                repeat(COLUMNS - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun EmojiCell(
    entry: EmojiEntry,
    onClick: () -> Unit,
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

            is EmojiEntry.Sticker -> Text(
                text = entry.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentRow(
    recent: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = NodysseyIcons.History,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.composer_emoji_recent),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xs, end = Spacing.sm),
        )
        recent.forEach { entry ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(entry) },
                contentAlignment = Alignment.Center,
            ) {
                Text(entry, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun BackspaceKey(onClick: () -> Unit) {
    val description = stringResource(R.string.composer_emoji_backspace)
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
            imageVector = NodysseyIcons.Backspace,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val COLUMNS = 6
private const val RECENT_LIMIT = 6
private val GRID_MIN_HEIGHT = 162.dp
