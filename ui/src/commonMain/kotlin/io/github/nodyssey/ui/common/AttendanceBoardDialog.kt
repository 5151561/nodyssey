package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.AttendanceBoardEntry
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_retry
import io.github.nodyssey.ui.resources.assets_board
import io.github.nodyssey.ui.resources.assets_board_failed
import io.github.nodyssey.ui.resources.assets_board_gain
import io.github.nodyssey.ui.resources.assets_board_self
import io.github.plaza.core.net.SiteError
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.component.groupedListItemColors
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import org.jetbrains.compose.resources.stringResource

/**
 * 今日签到榜 (9b), shared by 我的 and 账户与成长 and carrying no navigation of its own.
 *
 * A bottom sheet rather than the dialog it used to be: the board is a list, and a list wants the
 * height a sheet can grow to — the dialog held it in a fixed 360dp window with its own scroll. The
 * rows sit on one white card on the sheet's page colour, the same layering as every list screen.
 *
 * [selfUid] marks the reader's own row, tinted and suffixed 「（我）」, so finding yourself on a long
 * board is a glance rather than a read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceBoardDialog(
    isLoading: Boolean,
    entries: List<AttendanceBoardEntry>,
    error: SiteError?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    /** The signed-in account, whose row the board marks; required so that no entry point forgets it. */
    selfUid: Long?,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = LocalPlazaLayers.current.page,
    ) {
        Column(Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl)) {
            Text(
                text = stringResource(Res.string.assets_board),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = Spacing.sm).padding(bottom = Spacing.md).semantics { heading() },
            )
            when {
                isLoading ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) { PlazaSpinner(Modifier.describedAsLoading(), size = 24.dp) }

                error != null ->
                    LayerCard(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(
                            stringResource(Res.string.assets_board_failed),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
                    }

                else ->
                    LazyColumn(Modifier.heightIn(max = BOARD_LIST_MAX_HEIGHT)) {
                        // No `key`: the board is a one-shot snapshot that never reorders or grows,
                        // and names are not guaranteed unique — a duplicate key would crash for the
                        // sake of an identity Lazy already gets from the index.
                        itemsIndexed(entries) { index, entry ->
                            BoardRow(
                                rank = index + 1,
                                entry = entry,
                                isSelf = selfUid != null && entry.uid == selfUid,
                                first = index == 0,
                                last = index == entries.lastIndex,
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun BoardRow(
    rank: Int,
    entry: AttendanceBoardEntry,
    isSelf: Boolean,
    first: Boolean,
    last: Boolean,
) {
    GroupedListItem(
        first = first,
        last = last,
        colors =
        if (isSelf) {
            groupedListItemColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            groupedListItemColors()
        },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = rank.toString(),
                    style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = TABULAR_FIGURES,
                    ),
                    // The podium in the tertiary tone; everyone else in the row's own ink.
                    color = if (rank <= PODIUM && !isSelf) MaterialTheme.colorScheme.tertiary else Color.Unspecified,
                    modifier = Modifier.width(28.dp),
                )
                UserAvatar(url = entry.uid?.let(NodeSeekSite::avatarUrl), name = entry.name, size = 32.dp)
            }
        },
        headlineContent = {
            Text(
                text = if (isSelf) stringResource(Res.string.assets_board_self, entry.name) else entry.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent =
        entry.gain?.let {
            {
                Text(
                    text = stringResource(Res.string.assets_board_gain, it),
                    style =
                    MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = TABULAR_FIGURES,
                    ),
                )
            }
        },
        dividerInset = if (isSelf) 0.dp else Spacing.lg,
    )
}

private const val PODIUM = 3

/** Past this the list scrolls inside the sheet, so the sheet never covers the whole screen. */
private val BOARD_LIST_MAX_HEIGHT = 520.dp
