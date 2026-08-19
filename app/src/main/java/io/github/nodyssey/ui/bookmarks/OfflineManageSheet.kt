package io.github.nodyssey.ui.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.nodyssey.R
import io.github.nodyssey.data.OfflineSettings
import io.github.nodyssey.data.OfflineUsage
import io.github.nodyssey.ui.account.formatBytes
import io.github.plaza.designsys.component.ChoiceRow
import io.github.plaza.designsys.component.GroupedColumn
import io.github.plaza.designsys.component.GroupedRow
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES

/**
 * 离线管理 — how much is stored, under what conditions more gets stored, and the way out.
 *
 * A sheet rather than a settings page because every control on it is about the list underneath: the
 * reader gets here from the status bar on that list, changes one thing, and is back. 保留期限 is the
 * one control with more than two values, so it is the one that opens something further.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OfflineManageSheet(
    usage: OfflineUsage,
    settings: OfflineSettings,
    onSettingsChange: (OfflineSettings) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    var pickingRetention by remember { mutableStateOf(false) }
    var confirmingClear by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        OfflineManagePanel(
            usage = usage,
            settings = settings,
            onSettingsChange = onSettingsChange,
            onPickRetention = { pickingRetention = true },
            onClear = { confirmingClear = true },
            onDone = onDismiss,
        )
    }

    if (pickingRetention) {
        RetentionDialog(
            current = settings.retentionDays,
            onSelect = { onSettingsChange(settings.copy(retentionDays = it)) },
            onDismiss = { pickingRetention = false },
        )
    }

    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text(stringResource(R.string.offline_clear_title)) },
            // Spelled out because the word 清空 sits on a screen called 收藏, and the one thing a reader
            // will fear is that it clears the collection. It does not, and that is worth a sentence.
            text = { Text(stringResource(R.string.offline_clear_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingClear = false
                        onClear()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.offline_clear_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * Everything inside the sheet.
 *
 * Split out from [OfflineManageSheet] so it can be previewed: `ModalBottomSheet` draws into its own
 * dialog window, which `@Preview` does not render, and this panel is the part that was designed.
 */
@Composable
private fun OfflineManagePanel(
    usage: OfflineUsage,
    settings: OfflineSettings,
    onSettingsChange: (OfflineSettings) -> Unit,
    onPickRetention: () -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
) {
    // Scrollable, because a bottom sheet opens at half height: at a large reading size the panel is
    // taller than that, and everything under 自动补新回复 — including 清空 and 完成 — was reachable only
    // by knowing to drag the sheet up first. `ModalBottomSheet` picks the gesture up through nested
    // scroll, so dragging the content still expands the sheet before the content itself moves.
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 18.dp),
    ) {
        Column(
            modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = stringResource(R.string.offline_sheet_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.offline_sheet_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        UsageBreakdown(usage, Modifier.padding(horizontal = Spacing.xl))

        GroupedColumn(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.lg)) {
            GroupedRow(
                title = stringResource(R.string.offline_wifi_only),
                subtitle = stringResource(R.string.offline_wifi_only_body),
                icon = PlazaIcons.Wifi,
                first = true,
                showChevron = false,
                onClick = { onSettingsChange(settings.copy(wifiOnly = !settings.wifiOnly)) },
                trailing = {
                    // No `onCheckedChange`: the row is the target, and a switch that also took taps
                    // would give one row two hit areas with the same effect.
                    Switch(checked = settings.wifiOnly, onCheckedChange = null)
                },
            )
            GroupedRow(
                title = stringResource(R.string.offline_images),
                subtitle = stringResource(R.string.offline_images_body),
                icon = PlazaIcons.Image,
                showChevron = false,
                onClick = { onSettingsChange(settings.copy(includeImages = !settings.includeImages)) },
                trailing = { Switch(checked = settings.includeImages, onCheckedChange = null) },
            )
            GroupedRow(
                title = stringResource(R.string.offline_auto_sync),
                subtitle = stringResource(R.string.offline_auto_sync_body),
                icon = PlazaIcons.Sync,
                showChevron = false,
                onClick = { onSettingsChange(settings.copy(autoSyncReplies = !settings.autoSyncReplies)) },
                trailing = { Switch(checked = settings.autoSyncReplies, onCheckedChange = null) },
            )
            GroupedRow(
                title = stringResource(R.string.offline_retention),
                subtitle = stringResource(R.string.offline_retention_body),
                icon = PlazaIcons.Schedule,
                last = true,
                value = retentionLabel(settings.retentionDays),
                onClick = onPickRetention,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xl),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClear, enabled = usage.totalBytes > 0) {
                Text(
                    text = stringResource(R.string.offline_clear),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(onClick = onDone) {
                Text(
                    text = stringResource(R.string.offline_done),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

/**
 * 12.4 MB, split into the two things it is made of.
 *
 * Two segments rather than a progress bar because the number this is a fraction *of* — free space —
 * is three orders of magnitude larger, so drawn to scale the whole bar would be empty. What the
 * reader is deciding is whether to turn images off, and the split is the only thing that answers it.
 */
@Composable
private fun UsageBreakdown(
    usage: OfflineUsage,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.padding(bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = formatBytes(usage.totalBytes),
                style =
                MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
                color = scheme.onSurface,
            )
            Text(
                text =
                usage.freeBytes
                    ?.let { stringResource(R.string.offline_usage_summary, usage.posts, formatBytes(it)) }
                    ?: stringResource(R.string.offline_usage_summary_unknown, usage.posts),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                color = scheme.onSurfaceVariant,
            )
        }
        // Weighted rather than measured in dp so the two segments keep their ratio at any width, and
        // a zero-byte half simply takes no room instead of drawing a sliver that rounds to one pixel.
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(scheme.surfaceContainer),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val total = usage.totalBytes.coerceAtLeast(1L)
            if (usage.textBytes > 0) {
                Spacer(
                    Modifier
                        .weight(usage.textBytes.toFloat() / total)
                        .fillMaxHeight()
                        .background(scheme.primary),
                )
            }
            if (usage.imageBytes > 0) {
                Spacer(
                    Modifier
                        .weight(usage.imageBytes.toFloat() / total)
                        .fillMaxHeight()
                        .background(scheme.primaryContainer),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            UsageLegend(scheme.primary, stringResource(R.string.offline_usage_text, formatBytes(usage.textBytes)))
            UsageLegend(
                scheme.primaryContainer,
                stringResource(R.string.offline_usage_images, formatBytes(usage.imageBytes)),
            )
        }
    }
}

@Composable
private fun UsageLegend(
    swatch: Color,
    label: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(swatch))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RetentionDialog(
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.offline_retention)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.offline_retention_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
                OfflineSettings.RETENTION_CHOICES.forEach { choice ->
                    ChoiceRow(
                        label = retentionLabel(choice),
                        selected = choice == current,
                        onSelect = {
                            onSelect(choice)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun retentionLabel(days: Int): String =
    if (days == OfflineSettings.KEEP_FOREVER) {
        stringResource(R.string.offline_retention_forever)
    } else {
        stringResource(R.string.offline_retention_days, days)
    }

/**
 * The sheet on its own.
 *
 * `ModalBottomSheet` renders into a dialog window, which a `@Preview` cannot show — so the preview
 * draws the sheet's contents in the shape the sheet gives them instead. It is the panel that is
 * being reviewed here; the scrim and the drag handle are Material's and unchanged.
 */
@Composable
private fun OfflineManagePanelPreview(darkTheme: Boolean) {
    PlazaTheme(darkTheme = darkTheme) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(Modifier.padding(top = 22.dp)) {
                OfflineManagePanel(
                    usage =
                    OfflineUsage(
                        posts = 5,
                        textBytes = 2_202_009,
                        imageBytes = 10_800_332,
                        freeBytes = 3_435_973_836,
                    ),
                    settings = OfflineSettings(autoSyncReplies = false),
                    onSettingsChange = {},
                    onPickRetention = {},
                    onClear = {},
                    onDone = {},
                )
            }
        }
    }
}

@Preview(name = "i1 离线管理", widthDp = 360, heightDp = 620)
@Composable
private fun OfflineManageSheetPreview() = OfflineManagePanelPreview(darkTheme = false)

@Preview(name = "i1 离线管理 · Dark", widthDp = 360, heightDp = 620)
@Composable
private fun OfflineManageSheetDarkPreview() = OfflineManagePanelPreview(darkTheme = true)
