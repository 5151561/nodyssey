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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nodyssey.data.OfflineFailure
import io.github.nodyssey.data.OfflineState
import io.github.nodyssey.data.OfflineUsage
import io.github.nodyssey.ui.account.formatBytes
import io.github.nodyssey.ui.common.BoardTag
import io.github.nodyssey.ui.common.shortMessage
import io.github.nodyssey.ui.common.siteErrorRecovery
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.bookmarks_download
import io.github.nodyssey.ui.resources.bookmarks_filter_all
import io.github.nodyssey.ui.resources.bookmarks_filter_downloaded
import io.github.nodyssey.ui.resources.bookmarks_filter_new_replies
import io.github.nodyssey.ui.resources.bookmarks_remove
import io.github.nodyssey.ui.resources.bookmarks_select_action
import io.github.nodyssey.ui.resources.bookmarks_selection_all_new
import io.github.nodyssey.ui.resources.bookmarks_selection_partial
import io.github.nodyssey.ui.resources.bookmarks_selection_size
import io.github.nodyssey.ui.resources.bookmarks_stale
import io.github.nodyssey.ui.resources.offline_behind_replies
import io.github.nodyssey.ui.resources.offline_failed_challenge
import io.github.nodyssey.ui.resources.offline_failed_network
import io.github.nodyssey.ui.resources.offline_failed_rate_limited
import io.github.nodyssey.ui.resources.offline_failed_space
import io.github.nodyssey.ui.resources.offline_failed_unavailable
import io.github.nodyssey.ui.resources.offline_state_downloaded
import io.github.nodyssey.ui.resources.offline_state_downloading
import io.github.nodyssey.ui.resources.offline_state_not_downloaded
import io.github.nodyssey.ui.resources.offline_state_percent
import io.github.nodyssey.ui.resources.offline_state_queued
import io.github.nodyssey.ui.resources.offline_state_retry
import io.github.nodyssey.ui.resources.offline_state_sync
import io.github.nodyssey.ui.resources.offline_stop_download
import io.github.nodyssey.ui.resources.offline_stop_download_progress
import io.github.nodyssey.ui.resources.post_reply_count
import io.github.plaza.core.net.SiteError
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

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
        onLongClickLabel = stringResource(Res.string.bookmarks_select_action),
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
                    contentDescription = stringResource(Res.string.post_reply_count, it),
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
                stringResource(Res.string.offline_behind_replies, state.behindReplies) to
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
            label = stringResource(Res.string.offline_state_downloaded)
            labelColor = scheme.onSurfaceVariant
        }

        is OfflineState.Downloading -> {
            label =
                state.progress
                    ?.let { stringResource(Res.string.offline_state_percent, (it * 100).toInt()) }
                    ?: stringResource(Res.string.offline_state_queued)
            labelColor = scheme.onSurfaceVariant
        }

        is OfflineState.NotDownloaded -> {
            label = stringResource(Res.string.offline_state_not_downloaded)
            labelColor = scheme.onSurfaceVariant
        }

        is OfflineState.Stale -> {
            label = stringResource(Res.string.offline_state_sync)
            labelColor = scheme.onSurfaceVariant
        }

        is OfflineState.Failed -> {
            label = stringResource(Res.string.offline_state_retry)
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
                label = stringResource(Res.string.bookmarks_filter_all, state.entries.size),
                selected = state.filter == BookmarkFilter.ALL,
                onClick = { onFilter(BookmarkFilter.ALL) },
            )
            // The other two count downloads, so without a library there is nothing for them to be
            // about — and 「已下载 0」 next to 「全部 12」 reads as a broken feature rather than an absent one.
            if (state.offlineAvailable) {
                BookmarkChip(
                    label = stringResource(Res.string.bookmarks_filter_downloaded, state.downloadedCount),
                    selected = state.filter == BookmarkFilter.DOWNLOADED,
                    onClick = { onFilter(BookmarkFilter.DOWNLOADED) },
                )
                BookmarkChip(
                    label = stringResource(Res.string.bookmarks_filter_new_replies, state.newReplyCount),
                    selected = state.filter == BookmarkFilter.NEW_REPLIES,
                    onClick = { onFilter(BookmarkFilter.NEW_REPLIES) },
                )
            }
        }
        sortMenu()
    }
}

/**
 * The line that admits the list on screen came off disk.
 *
 * A standing condition rather than an event, which is why it is a strip in the layout and not the
 * Snackbar the feed uses for the same failure. On the feed a refusal to refresh means "no newer
 * posts" and the content below it is untouched; here it means the reader is looking at a snapshot —
 * a thread un-collected on the web is still on it, a thread collected on the web is not, and the
 * rows without a stored copy will not open. That is worth saying for as long as it is true.
 *
 * The reason comes from the error rather than being assumed to be the network: 需要登录后查看 and
 * 需要确认一下你不是机器人 are the two failures a retry alone cannot fix, and a strip that called
 * either of them 「离线」 would send the reader to look for their signal.
 *
 * Which is also why the button is [siteErrorRecovery]'s. Naming those two failures correctly and
 * then offering 重试 anyway only moved the dead end: the strip said the wall was the reason and the
 * one control on it was the press that cannot clear a wall.
 */
@Composable
internal fun BookmarkStaleBanner(
    error: SiteError,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    onVerify: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recovery = siteErrorRecovery(error, onVerify = onVerify, onSignIn = onSignIn, onRetry = onRetry)
    Surface(
        modifier = modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = 10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 40.dp).padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = PlazaIcons.LinkOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = stringResource(Res.string.bookmarks_stale, error.shortMessage()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            recovery?.let {
                TextButton(onClick = it.onClick) {
                    Text(it.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
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
                            Res.string.bookmarks_selection_size,
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
                            stringResource(Res.string.bookmarks_selection_partial, alreadyOfflineCount)
                        } else {
                            stringResource(Res.string.bookmarks_selection_all_new)
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
                        text = stringResource(Res.string.bookmarks_download),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
            IconButton(onClick = onRemove, enabled = selectedCount > 0) {
                Icon(
                    imageVector = PlazaIcons.BookmarkRemove,
                    contentDescription = stringResource(Res.string.bookmarks_remove),
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
            stringResource(Res.string.offline_state_downloaded) + " " + formatBytes(state.bytes)

        is OfflineState.Downloading ->
            state.progress
                ?.let {
                    stringResource(Res.string.offline_state_downloading) + " " +
                        stringResource(Res.string.offline_state_percent, (it * 100).toInt())
                }
                ?: stringResource(Res.string.offline_state_queued)

        is OfflineState.Stale -> stringResource(Res.string.offline_behind_replies, state.behindReplies)

        is OfflineState.Failed -> stringResource(state.reason.messageRes)

        is OfflineState.NotDownloaded -> stringResource(Res.string.offline_state_not_downloaded)
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
        is OfflineState.Downloaded -> stringResource(Res.string.offline_state_downloaded)

        is OfflineState.Downloading ->
            state.progress
                ?.let { stringResource(Res.string.offline_stop_download_progress, (it * 100).toInt()) }
                ?: stringResource(Res.string.offline_stop_download)

        is OfflineState.NotDownloaded -> stringResource(Res.string.offline_state_not_downloaded)

        is OfflineState.Stale -> stringResource(Res.string.offline_state_sync)

        is OfflineState.Failed -> stringResource(Res.string.offline_state_retry)
    }

internal val OfflineFailure.messageRes: StringResource
    get() =
        when (this) {
            OfflineFailure.OutOfSpace -> Res.string.offline_failed_space
            OfflineFailure.Network -> Res.string.offline_failed_network
            OfflineFailure.Unavailable -> Res.string.offline_failed_unavailable
            OfflineFailure.Challenge -> Res.string.offline_failed_challenge
            OfflineFailure.RateLimited -> Res.string.offline_failed_rate_limited
        }
