package io.github.nodyssey.ui.bookmarks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import io.github.nodyssey.ui.common.describedAsLoading
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
import io.github.plaza.designsys.component.LayerDivider
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.MetaStat
import io.github.plaza.designsys.component.MetaText
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.ThreadRow
import io.github.plaza.designsys.component.ThreadRowTitle
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.component.layerCardSlice
import io.github.plaza.designsys.component.listAvatarSize
import io.github.plaza.designsys.component.materialIcon
import io.github.plaza.designsys.component.textScaledSize
import io.github.plaza.designsys.theme.LocalEinkMode
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.Sizes
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.floatShadow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One collected thread, as a slice of the one white card the list is drawn on (board 8e): the
 * title, one meta line, and whatever this device has of it at the end of the row.
 *
 * No avatar, unlike the feed. The collection payload carries no picture for most rows (see
 * [CollectedPostMetaStore]), and a column of initials the reader never chose to see bought nothing
 * but width the title needed — 8e drops it, and gives that width to the download state instead.
 *
 * [selected] carries all three states the row can be in, because they are three and not two: null is
 * "not multi-selecting", which is the only one of the three where the download column is drawn and a
 * tap opens the thread.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BookmarkRow(
    entry: BookmarkEntry,
    offlineAvailable: Boolean,
    selected: Boolean?,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOfflineAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inSelection = selected != null
    val layers = LocalPlazaLayers.current
    val showsOfflineColumn = offlineAvailable && !inSelection
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .layerCardSlice(layers, first, last)
            .background(if (selected == true) layers.inset else Color.Transparent)
            // Not a raw `pointerInput`: this is what gives the press a ripple and gives TalkBack a
            // long-click action it can announce and perform.
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = stringResource(Res.string.bookmarks_select_action),
            ),
    ) {
        if (!first) LayerDivider(startInset = Spacing.lg, endInset = Spacing.lg)
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = if (inSelection) Spacing.sm else Spacing.lg,
                    end = if (showsOfflineColumn) Spacing.xs else Spacing.lg,
                    top = Spacing.md,
                    bottom = Spacing.md,
                ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected != null) {
                // The row is the touch target — it is what toggles the tick — so the box itself is
                // released from Material's 48dp minimum. Left at its default it would claim 48dp of
                // a 360dp row and take that width out of the title.
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    Checkbox(checked = selected, onCheckedChange = null)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ThreadRowTitle(text = AnnotatedString(entry.title))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    BoardTag(title = entry.categoryTitle, slug = entry.categorySlug)
                    val replies = entry.commentCount?.let { stringResource(Res.string.post_reply_count, it) }
                    val byline =
                        if (inSelection && offlineAvailable) {
                            // The download column is gone in multi-select, so the state it was saying
                            // moves onto the meta line — otherwise ticking a row is also the moment
                            // you stop being able to see which of the six you already have.
                            listOfNotNull(entry.authorName?.takeIf { it.isNotBlank() }, offlineSummary(entry.offline))
                        } else {
                            listOfNotNull(entry.authorName?.takeIf { it.isNotBlank() }, replies)
                        }
                    if (byline.isNotEmpty()) MetaText(byline.joinToString(META_SEPARATOR), singleLine = true)
                    if (showsOfflineColumn) (entry.offline as? OfflineState.Stale)?.let { StalePill(it.behindReplies) }
                }
                if (showsOfflineColumn) (entry.offline as? OfflineState.Failed)?.let { FailureLine(it.reason) }
            }
            if (showsOfflineColumn) OfflineStateAction(state = entry.offline, onClick = onOfflineAction)
        }
    }
}

private const val META_SEPARATOR = " · "

/**
 * 「离线版落后 3 条回复」, as 8e's filled pill on the meta line.
 *
 * The pill rather than a sentence under the row because it is the one piece of news on a row of
 * facts — the same weight the feed gives 「N 条新回复」 — and the words stay the offline copy's own:
 * these are replies the *stored* thread is missing, not ones the reader has not seen.
 */
@Composable
private fun StalePill(behindReplies: Int) {
    Text(
        text = stringResource(Res.string.offline_behind_replies, behindReplies),
        style =
        MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            fontFeatureSettings = TABULAR_FIGURES,
        ),
        color = MaterialTheme.colorScheme.onPrimary,
        maxLines = 1,
        modifier =
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
    )
}

/** 「下载失败 · …」 — the one state that owes the reader a sentence rather than a glyph. */
@Composable
private fun FailureLine(reason: OfflineFailure) {
    val color = MaterialTheme.colorScheme.error
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = PlazaIcons.ErrorCircle,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(textScaledSize(14.sp)),
        )
        Text(
            text = stringResource(reason.messageRes),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
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
 *
 * A glyph alone, as board 8e draws it: the word that used to sit under each one cost the row a
 * second line at the end and said what the glyph already did. The words are still there for
 * TalkBack — the whole column is one node carrying [offlineActionDescription].
 */
@Composable
private fun OfflineStateAction(
    state: OfflineState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val description = offlineActionDescription(state)
    Box(
        modifier =
        modifier
            .size(Sizes.minTouchTarget)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            // One node, one announcement: a bare glyph and a bare "62" read as two unrelated things.
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // A local: `progress` is a `val` in another module, so the check would not narrow it.
        val progress = (state as? OfflineState.Downloading)?.progress
        if (progress != null) {
            DownloadProgressRing(progress)
        } else {
            Icon(
                imageVector =
                when (state) {
                    is OfflineState.Downloaded -> OfflinePinIcon

                    // Queued: 8e's clock — waiting its turn, not yet moving.
                    is OfflineState.Downloading -> PlazaIcons.Schedule

                    is OfflineState.Stale -> PlazaIcons.Sync

                    is OfflineState.Failed -> Icons.Default.Refresh

                    is OfflineState.NotDownloaded -> PlazaIcons.Download
                },
                contentDescription = null,
                tint =
                when (state) {
                    is OfflineState.Downloaded, is OfflineState.Stale -> scheme.primary
                    is OfflineState.Failed -> scheme.error
                    else -> scheme.onSurfaceVariant
                },
                modifier = Modifier.size(textScaledSize(22.sp)),
            )
        }
    }
}

/**
 * The ring, with the percentage inside it.
 *
 * `gapSize` zeroed and a butt cap because Material's determinate indicator draws a gap between the
 * ends of the arc by default, and at this size that gap is a third of what a 10%-complete download
 * has to show with. The number rather than 8e's glyph-less ring alone: a ring at 40% and one at 60%
 * are hard to tell apart at 26dp, and the reader deciding whether to stop it wants to know which.
 */
@Composable
private fun DownloadProgressRing(progress: Float) {
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(RING_SIZE).describedAsLoading(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            strokeWidth = RING_STROKE,
            strokeCap = StrokeCap.Butt,
            gapSize = 0.dp,
        )
        Text(
            text = (progress * 100).toInt().toString(),
            style =
            MaterialTheme.typography.labelSmall.copy(
                fontSize = 8.sp,
                lineHeight = 8.sp,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val RING_SIZE = 26.dp
private val RING_STROKE = 4.dp

/**
 * 全部 12 / 已下载 5 / 有新回复 3 — Material's own filter chips, as 8e draws them: the selected one
 * tonal with a tick, the rest outlined on the page.
 */
@Composable
internal fun BookmarkFilterRow(
    state: BookmarksUiState,
    onFilter: (BookmarkFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm),
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
}

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
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_FIGURES),
            )
        },
        leadingIcon =
        if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
    )
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
    onVerify: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val recovery = siteErrorRecovery(error, onVerify = onVerify, onSignIn = onSignIn, onRetry = onRetry)
    val layers = LocalPlazaLayers.current
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = LayerPageGutter).padding(bottom = Spacing.sm),
        // A card on the page like the list under it, rather than a grey strip: on a grey page a
        // grey strip is only an outline away from invisible.
        color = layers.card,
        shape = RoundedCornerShape(16.dp),
        border = layers.cardBorder?.let { BorderStroke(1.dp, it) },
    ) {
        Row(
            modifier = Modifier.heightIn(min = 44.dp).padding(start = 14.dp, end = Spacing.xs),
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
 * The floating bar multi-select works from.
 *
 * Not a `BottomAppBar`: that one is edge-to-edge and part of the frame, and this is a thing that
 * appears over the list for as long as a selection exists — which is what the inset, the pill and
 * the float shadow are saying.
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
    val layers = LocalPlazaLayers.current
    val shape = CircleShape
    Surface(
        modifier = modifier.fillMaxWidth().padding(Spacing.lg).floatShadow(shape, layers.shadows),
        shape = shape,
        color = layers.raised,
        border = layers.cardBorder?.let { BorderStroke(1.dp, it) },
    ) {
        Row(
            modifier =
            Modifier
                .heightIn(min = 64.dp)
                .padding(start = Spacing.xl, end = Spacing.sm),
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
                    shape = CircleShape,
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

/** Material Symbols' `offline_pin` — 8e's 已离线. Not in `material-icons-core`. */
private val OfflinePinIcon: ImageVector by lazy {
    materialIcon(
        name = "OfflinePin",
        pathData =
        "M12,2C6.5,2 2,6.5 2,12s4.5,10 10,10 10,-4.5 10,-10S17.5,2 12,2z" +
            "M17,18L7,18v-2h10v2z" +
            "M10.3,14L7,10.7l1.4,-1.4 1.9,1.9 5.3,-5.3L17,7.3 10.3,14z",
    )
}

/** Material Symbols' `download_for_offline` — 8e's 全部下载. */
internal val DownloadForOfflineIcon: ImageVector by lazy {
    materialIcon(
        name = "DownloadForOffline",
        pathData =
        "M12,2C6.49,2 2,6.49 2,12s4.49,10 10,10s10,-4.49 10,-10S17.51,2 12,2z" +
            "M11,10V6h2v4h3l-4,4l-4,-4H11z" +
            "M17,17H7v-2h10V17z",
    )
}
