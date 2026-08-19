package io.github.nodyssey.ui.bookmarks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nodyssey.R
import io.github.nodyssey.data.OfflineFailure
import io.github.nodyssey.data.OfflineState
import io.github.nodyssey.data.OfflineUsage
import io.github.nodyssey.ui.account.formatBytes
import io.github.nodyssey.ui.common.BoardTag
import io.github.plaza.designsys.component.AvatarCapOffset
import io.github.plaza.designsys.component.MetaStat
import io.github.plaza.designsys.component.MetaText
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.ThreadRow
import io.github.plaza.designsys.component.ThreadRowTitle
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.component.listAvatarSize
import io.github.plaza.designsys.component.textScaledSize
import io.github.plaza.designsys.theme.Sizes
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES

/**
 * One collected thread, drawn as the feed draws it plus whatever this device has of it.
 *
 * [selected] carries all three states the row can be in, because they are three and not two: null is
 * "not multi-selecting", which is the only one of the three where the download column is drawn and a
 * tap opens the thread.
 */
@Composable
internal fun BookmarkRow(
    entry: BookmarkEntry,
    offlineAvailable: Boolean,
    selected: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOfflineAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inSelection = selected != null
    val avatarSize = listAvatarSize()
    ThreadRow(
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = stringResource(R.string.bookmarks_select_action),
        containerColor =
        if (selected == true) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surface
        },
        leading = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (selected != null) {
                    // The row is the touch target — it is what toggles the tick — so the box itself
                    // is released from Material's 48dp minimum. Left at its default it would claim
                    // 48dp of a 360dp row and take that width out of the title.
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = null,
                            modifier = Modifier.offset(y = AvatarCapOffset),
                        )
                    }
                }
                UserAvatar(
                    // The collection payload carries neither a uid nor a picture, so this comes from
                    // what the device remembers of the thread — see [CollectedPostMetaStore]. Still
                    // null for a thread nothing here has ever opened or downloaded, and the row then
                    // falls back to the author's initial as it always did.
                    url = entry.avatarUrl,
                    name = entry.authorName?.takeIf { it.isNotBlank() } ?: entry.title,
                    size = avatarSize,
                    modifier = Modifier.offset(y = AvatarCapOffset),
                )
            }
        },
        title = { ThreadRowTitle(text = AnnotatedString(entry.title)) },
        supporting =
        if (offlineAvailable && !inSelection) {
            { OfflineSupportingLine(entry.offline) }
        } else {
            null
        },
        trailing =
        if (offlineAvailable && !inSelection) {
            { OfflineStateAction(state = entry.offline, onClick = onOfflineAction) }
        } else {
            null
        },
    ) {
        BoardTag(title = entry.categoryTitle, slug = entry.categorySlug)
        entry.authorName?.takeIf { it.isNotBlank() }?.let { MetaText(it, singleLine = true) }
        if (inSelection && offlineAvailable) {
            // The download column is gone in multi-select, so the state it was saying moves onto the
            // meta line — otherwise ticking a row is also the moment you stop being able to see
            // which of the six you already have.
            MetaText(offlineSummary(entry.offline), singleLine = true)
        } else {
            entry.commentCount?.let {
                MetaStat(
                    icon = PlazaIcons.ModeComment,
                    value = it.toString(),
                    contentDescription = stringResource(R.string.post_reply_count, it),
                )
            }
            entry.createdAtText?.takeIf { it.isNotBlank() }?.let { MetaText(it, singleLine = true) }
        }
    }
}

/** 「离线版落后 3 条回复」 and 「下载失败 · …」 — the two states that owe the reader a sentence. */
@Composable
private fun OfflineSupportingLine(state: OfflineState) {
    val (text, color) =
        when (state) {
            is OfflineState.Stale ->
                stringResource(R.string.offline_behind_replies, state.behindReplies) to
                    MaterialTheme.colorScheme.primary

            is OfflineState.Failed ->
                stringResource(state.reason.messageRes) to MaterialTheme.colorScheme.error

            else -> return
        }
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector =
            if (state is OfflineState.Failed) PlazaIcons.ErrorCircle else PlazaIcons.ArrowDownward,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(textScaledSize(14.sp)),
        )
        Text(
            text = text,
            style =
            MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
            color = color,
        )
    }
}

/**
 * The download column: one of five states, and a tap that does the obvious thing for each.
 *
 * A single control rather than five, because from the reader's side it is one — "deal with this
 * row's offline copy". What that means differs (fetch it, stop fetching it, catch it up, try again)
 * but there is never a choice to make, so there is never more than one button.
 */
@Composable
private fun OfflineStateAction(
    state: OfflineState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val iconSize = textScaledSize(22.sp)
    val label: String
    val labelColor: Color
    val description = offlineActionDescription(state)

    when (state) {
        is OfflineState.Downloaded -> {
            label = stringResource(R.string.offline_state_downloaded)
            labelColor = scheme.onSurfaceVariant
        }

        is OfflineState.Downloading -> {
            label =
                state.progress
                    ?.let { stringResource(R.string.offline_state_percent, (it * 100).toInt()) }
                    ?: stringResource(R.string.offline_state_queued)
            labelColor = scheme.onSurfaceVariant
        }

        is OfflineState.NotDownloaded -> {
            label = stringResource(R.string.offline_state_not_downloaded)
            labelColor = scheme.onSurfaceVariant
        }

        is OfflineState.Stale -> {
            label = stringResource(R.string.offline_state_sync)
            labelColor = scheme.onSurfaceVariant
        }

        is OfflineState.Failed -> {
            label = stringResource(R.string.offline_state_retry)
            labelColor = scheme.error
        }
    }

    Column(
        modifier =
        modifier
            .width(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .heightIn(min = Sizes.minTouchTarget)
            .padding(top = 2.dp)
            // One node, one announcement: a bare glyph and a bare "62%" read as two unrelated things.
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        when (state) {
            is OfflineState.Downloading -> DownloadProgressRing(state.progress, iconSize)

            else ->
                Icon(
                    imageVector =
                    when (state) {
                        is OfflineState.Downloaded -> PlazaIcons.CloudDone
                        is OfflineState.Stale -> PlazaIcons.Sync
                        is OfflineState.Failed -> Icons.Default.Refresh
                        else -> PlazaIcons.Download
                    },
                    contentDescription = null,
                    tint =
                    when (state) {
                        is OfflineState.Downloaded, is OfflineState.Stale -> scheme.primary
                        is OfflineState.Failed -> scheme.error
                        else -> scheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(iconSize),
                )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
            color = labelColor,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The ring, with a stop square inside it.
 *
 * `gapSize` zeroed and a butt cap because Material's determinate indicator draws a gap between the
 * ends of the arc by default, and at 22dp that gap is a third of what a 10%-complete download has to
 * show with. A square rather than a ✕: at this size the cross's arms and the arc's ends are the same
 * few pixels and the whole thing reads as noise.
 */
@Composable
private fun DownloadProgressRing(
    progress: Float?,
    size: Dp,
) {
    Box(contentAlignment = Alignment.Center) {
        if (progress == null) {
            CircularProgressIndicator(
                modifier = Modifier.size(size),
                strokeWidth = RING_STROKE,
                strokeCap = StrokeCap.Butt,
            )
        } else {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(size),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
                strokeWidth = RING_STROKE,
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
            )
        }
        Icon(
            imageVector = PlazaIcons.Stop,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size / 2),
        )
    }
}

private val RING_STROKE = 3.5.dp

/**
 * 已离线 5 篇 · 占用 12.4 MB · 仅 Wi-Fi 下载.
 *
 * The whole bar is the touch target rather than just the 管理 label at its end: the comp draws that
 * label at 34dp of row height, which is not a target anyone can hit, and a 48dp button dropped into
 * a 34dp bar sets the bar's height instead. So the bar grows to 48dp and all of it opens the sheet.
 */
@Composable
internal fun OfflineStatusBar(
    usage: OfflineUsage,
    wifiOnly: Boolean,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, onClick = onManage) {
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.minTouchTarget)
                    .padding(horizontal = Spacing.lg, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = PlazaIcons.CloudDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(textScaledSize(18.sp)),
                )
                val summary =
                    stringResource(R.string.offline_status, usage.posts, formatBytes(usage.totalBytes))
                Text(
                    text = if (wifiOnly) stringResource(R.string.offline_status_wifi_only, summary) else summary,
                    style =
                    MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.offline_manage),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** 全部 12 / 已下载 5 / 有新回复 3, and ⇅ pinned to the end. */
@Composable
internal fun BookmarkFilterRow(
    state: BookmarksUiState,
    onFilter: (BookmarkFilter) -> Unit,
    sortMenu: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = Spacing.lg, end = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            BookmarkChip(
                label = stringResource(R.string.bookmarks_filter_all, state.entries.size),
                selected = state.filter == BookmarkFilter.ALL,
                onClick = { onFilter(BookmarkFilter.ALL) },
            )
            // The other two count downloads, so without a library there is nothing for them to be
            // about — and 「已下载 0」 next to 「全部 12」 reads as a broken feature rather than an absent one.
            if (state.offlineAvailable) {
                BookmarkChip(
                    label = stringResource(R.string.bookmarks_filter_downloaded, state.downloadedCount),
                    selected = state.filter == BookmarkFilter.DOWNLOADED,
                    onClick = { onFilter(BookmarkFilter.DOWNLOADED) },
                )
                BookmarkChip(
                    label = stringResource(R.string.bookmarks_filter_new_replies, state.newReplyCount),
                    selected = state.filter == BookmarkFilter.NEW_REPLIES,
                    onClick = { onFilter(BookmarkFilter.NEW_REPLIES) },
                )
            }
        }
        sortMenu()
    }
}

/**
 * The comp's chip: `primary` when on, `surfaceContainerLow` when off, and no outline either way.
 *
 * Material's own filter chip is `secondaryContainer` when selected and outlined when not, which on
 * this palette makes the selected chip and the unselected ones nearly the same grey.
 */
@Composable
private fun BookmarkChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style =
                MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
            )
        },
        border = null,
        colors =
        FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

/**
 * The floating bar multi-select works from.
 *
 * Not a `BottomAppBar`: that one is edge-to-edge and part of the frame, and this is a thing that
 * appears over the list for as long as a selection exists — which is what the inset and the corner
 * radius are saying.
 */
@Composable
internal fun SelectionToolbar(
    selectedCount: Int,
    alreadyOfflineCount: Int,
    estimateBytes: Long?,
    offlineAvailable: Boolean,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(Spacing.lg),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier =
            Modifier
                .heightIn(min = 64.dp)
                .padding(start = Spacing.lg, end = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                // The count alone is not repeated here: the bar at the top of the screen already says
                // 已选 N 项, and two of them a screen apart saying the same number is the sort of thing
                // that makes a reader look for the difference. What this line adds is the size, so
                // without a size it does not appear.
                if (estimateBytes != null) {
                    Text(
                        text =
                        stringResource(
                            R.string.bookmarks_selection_size,
                            selectedCount,
                            formatBytes(estimateBytes),
                        ),
                        style =
                        MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = TABULAR_FIGURES,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (offlineAvailable) {
                    Text(
                        text =
                        if (alreadyOfflineCount > 0) {
                            stringResource(R.string.bookmarks_selection_partial, alreadyOfflineCount)
                        } else {
                            stringResource(R.string.bookmarks_selection_all_new)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (offlineAvailable) {
                Button(
                    onClick = onDownload,
                    enabled = selectedCount > 0,
                    contentPadding = PaddingValues(horizontal = Spacing.lg),
                ) {
                    Icon(
                        imageVector = PlazaIcons.Download,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.bookmarks_download),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
            IconButton(onClick = onRemove, enabled = selectedCount > 0) {
                Icon(
                    imageVector = PlazaIcons.BookmarkRemove,
                    contentDescription = stringResource(R.string.bookmarks_remove),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** What the meta line says about a row's offline copy while the download column is hidden. */
@Composable
private fun offlineSummary(state: OfflineState): String =
    when (state) {
        is OfflineState.Downloaded ->
            stringResource(R.string.offline_state_downloaded) + " " + formatBytes(state.bytes)

        is OfflineState.Downloading ->
            state.progress
                ?.let {
                    stringResource(R.string.offline_state_downloading) + " " +
                        stringResource(R.string.offline_state_percent, (it * 100).toInt())
                }
                ?: stringResource(R.string.offline_state_queued)

        is OfflineState.Stale -> stringResource(R.string.offline_behind_replies, state.behindReplies)

        is OfflineState.Failed -> stringResource(state.reason.messageRes)

        is OfflineState.NotDownloaded -> stringResource(R.string.offline_state_not_downloaded)
    }

/**
 * What TalkBack says instead of a glyph and a loose number.
 *
 * The in-flight one carries the percentage, because the column clears its children's semantics and
 * the number is otherwise the one thing on this control a screen reader could not reach.
 */
@Composable
private fun offlineActionDescription(state: OfflineState): String =
    when (state) {
        is OfflineState.Downloaded -> stringResource(R.string.offline_state_downloaded)

        is OfflineState.Downloading ->
            state.progress
                ?.let { stringResource(R.string.offline_stop_download_progress, (it * 100).toInt()) }
                ?: stringResource(R.string.offline_stop_download)

        is OfflineState.NotDownloaded -> stringResource(R.string.offline_state_not_downloaded)

        is OfflineState.Stale -> stringResource(R.string.offline_state_sync)

        is OfflineState.Failed -> stringResource(R.string.offline_state_retry)
    }

internal val OfflineFailure.messageRes: Int
    get() =
        when (this) {
            OfflineFailure.OutOfSpace -> R.string.offline_failed_space
            OfflineFailure.Network -> R.string.offline_failed_network
            OfflineFailure.Unavailable -> R.string.offline_failed_unavailable
        }
