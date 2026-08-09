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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.nodyssey.R
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.image.allowMeteredImage
import io.github.nodyssey.ui.common.NodysseyIcons
import io.github.nodyssey.ui.theme.Spacing

/**
 * One of the five groups NodeSeek's own editor offers.
 *
 * The first three are NodeSeek's image stickers. Their previews come from the site's own
 * `/static/image/sticker/` — the same URLs post bodies already render — while the editor inserts the
 * site's native shortcode (`:ac01:` etc.). Keeping those two concerns separate means a published
 * post still uses NodeSeek's renderer instead of a guessed Markdown image URL.
 *
 * The previews used to ship in `assets/stickers`, which cost 1.7 MB of APK for images the app was
 * downloading anyway the moment a post used one. Fetching them means one Coil-cached copy serves
 * both the panel and the thread, at the price of a first open that needs the network.
 */
data class EmojiGroup(
    @param:StringRes val titleRes: Int,
    val entries: List<EmojiEntry>,
)

sealed interface EmojiEntry {
    /** Inserted as-is. Fluent and App are plain Unicode, which is why they work today. */
    data class Unicode(val character: String) : EmojiEntry

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

val NodeSeekEmojiGroups = listOf(
    EmojiGroup(R.string.composer_emoji_group_acn, acStickers()),
    EmojiGroup(R.string.composer_emoji_group_onion, yctStickers()),
    EmojiGroup(R.string.composer_emoji_group_chick, xhjStickers()),
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

private fun acStickers(): List<EmojiEntry.Sticker> =
    (
        (1..54).map { it.toString().padStart(2, '0') } +
            (1001..1040).map(Int::toString) +
            (2001..2055).map(Int::toString)
        )
        .map { siteSticker(group = "ac", code = it, extension = "png") }

private fun yctStickers(): List<EmojiEntry.Sticker> =
    (1..22)
        .map { it.toString().padStart(3, '0') }
        .map { siteSticker(group = "yct", code = it, extension = "gif") }

private fun xhjStickers(): List<EmojiEntry.Sticker> =
    (1..32).map { number ->
        val code = number.toString().padStart(3, '0')
        val extension = when (number) {
            1, 2, 3, 5, 6, 7, 11, 22, 24, 25, 31, 32 -> "png"
            else -> "gif"
        }
        siteSticker(group = "xhj", code = code, extension = extension)
    }

private fun siteSticker(
    group: String,
    code: String,
    extension: String,
) = EmojiEntry.Sticker(
    name = group + code,
    shortcode = " :$group$code: ",
    url = NodeSeekSite.stickerUrl(group = group, code = code, extension = extension),
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
                        title = stringResource(candidate.titleRes),
                        selected = index == selectedIndex,
                        onClick = { selectedIndex = index },
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(GRID_HEIGHT)) {
                if (group.entries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.composer_emoji_stickers_pending),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = Spacing.xl),
                    )
                } else {
                    EmojiGrid(
                        entries = group.entries,
                        onSelect = { insert(it.insertion) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RecentRow(
                    recent = recent,
                    entriesByInsertion = entriesByInsertion,
                    onSelect = ::insert,
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
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(entries, key = { it.insertion }) { entry ->
            EmojiCell(entry = entry, onClick = { onSelect(entry) })
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

            is EmojiEntry.Sticker -> StickerImage(
                sticker = entry,
                contentDescription = null,
                modifier = Modifier.size(STICKER_SIZE),
            )
        }
    }
}

/**
 * A sticker preview, fetched from the site.
 *
 * Waived past 仅 Wi-Fi 加载图片 on the same grounds as a tap on a skipped image: opening the panel is
 * the user asking for these, they are a few KB each, and the grid only requests the cells on screen.
 * Respecting the switch here would instead leave a permanently blank panel for anyone whose network
 * never reports NOT_METERED — a VPN tunnel, for instance.
 */
@Composable
private fun StickerImage(
    sticker: EmojiEntry.Sticker,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val request = remember(sticker.url) {
        ImageRequest
            .Builder(context)
            .data(sticker.url)
            .allowMeteredImage(true)
            .build()
    }
    AsyncImage(model = request, contentDescription = contentDescription, modifier = modifier)
}

@Composable
private fun RecentRow(
    recent: List<String>,
    entriesByInsertion: Map<String, EmojiEntry>,
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
                        StickerImage(
                            sticker = entry,
                            contentDescription = entry.name,
                            modifier = Modifier.size(RECENT_STICKER_SIZE),
                        )

                    is EmojiEntry.Unicode -> Text(entry.character, fontSize = 18.sp)

                    null -> Text(insertion, fontSize = 18.sp)
                }
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
private val GRID_HEIGHT = 162.dp
private val STICKER_SIZE = 36.dp
private val RECENT_STICKER_SIZE = 26.dp
