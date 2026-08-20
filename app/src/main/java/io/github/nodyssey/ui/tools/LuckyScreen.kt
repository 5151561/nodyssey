package io.github.nodyssey.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.core.LuckyDraw
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.action_copy_link
import io.github.nodyssey.ui.resources.action_done
import io.github.nodyssey.ui.resources.lucky_dedupe
import io.github.nodyssey.ui.resources.lucky_dedupe_hint
import io.github.nodyssey.ui.resources.lucky_draw_time
import io.github.nodyssey.ui.resources.lucky_generate
import io.github.nodyssey.ui.resources.lucky_intro
import io.github.nodyssey.ui.resources.lucky_link_copied
import io.github.nodyssey.ui.resources.lucky_needs_post_id
import io.github.nodyssey.ui.resources.lucky_open_web
import io.github.nodyssey.ui.resources.lucky_pick_date
import io.github.nodyssey.ui.resources.lucky_pick_time
import io.github.nodyssey.ui.resources.lucky_post_id
import io.github.nodyssey.ui.resources.lucky_principle
import io.github.nodyssey.ui.resources.lucky_prize_count
import io.github.nodyssey.ui.resources.lucky_result
import io.github.nodyssey.ui.resources.lucky_result_hint
import io.github.nodyssey.ui.resources.lucky_start_floor
import io.github.nodyssey.ui.resources.lucky_title
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.digitsOnly
import io.github.plaza.designsys.component.rememberClipboardCopy
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun LuckyRoute(
    viewModel: LuckyViewModel,
    onBack: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LuckyScreen(
        state = state,
        postIdState = viewModel.postId,
        prizeCountState = viewModel.prizeCount,
        startFloorState = viewModel.startFloor,
        onBack = onBack,
        onDrawAtChange = viewModel::setDrawAt,
        onDedupeChange = viewModel::setDedupeFloors,
        onGenerate = viewModel::generate,
        onOpenBrowser = onOpenBrowser,
        modifier = modifier,
    )
}

/**
 * 幸运抽奖 — a form, not a wheel.
 *
 * The site's `/lucky` is a notary for T-floor giveaways: you declare the thread, the closing time, how
 * many prizes, which floor counting starts at and whether one user can win twice, and it hands back a
 * public link. An earlier design had a spinning wheel and a stardust cost; neither exists, and the whole
 * value of the real thing is that it costs nothing and commits to the rules *before* the draw.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuckyScreen(
    state: LuckyUiState,
    postIdState: TextFieldState,
    prizeCountState: TextFieldState,
    startFloorState: TextFieldState,
    onBack: () -> Unit,
    onDrawAtChange: (Long) -> Unit,
    onDedupeChange: (Boolean) -> Unit,
    onGenerate: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    val copy = rememberClipboardCopy()
    val copyLabel = stringResource(Res.string.lucky_title)
    val copiedText = stringResource(Res.string.lucky_link_copied)

    val appBarState = rememberOneHandAppBarState()
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(Res.string.lucky_title),
                state = appBarState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    text = stringResource(Res.string.lucky_intro),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = Spacing.md),
                )
            }

            OutlinedTextField(
                state = postIdState,
                label = { Text(stringResource(Res.string.lucky_post_id)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = digitsOnly(LuckyViewModel.MAX_FIELD_LENGTH),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            DrawTimeRow(
                millis = state.drawAtMillis,
                onPickDate = { pickingDate = true },
                onPickTime = { pickingTime = true },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    state = prizeCountState,
                    label = { Text(stringResource(Res.string.lucky_prize_count)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    inputTransformation = digitsOnly(LuckyViewModel.MAX_FIELD_LENGTH),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    state = startFloorState,
                    label = { Text(stringResource(Res.string.lucky_start_floor)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    inputTransformation = digitsOnly(LuckyViewModel.MAX_FIELD_LENGTH),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(Res.string.lucky_dedupe),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(Res.string.lucky_dedupe_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.dedupeFloors, onCheckedChange = onDedupeChange)
                }
            }

            Button(
                onClick = onGenerate,
                enabled = state.canGenerate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                Icon(PlazaIcons.Link, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(Res.string.lucky_generate),
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
            if (!state.canGenerate) {
                Text(
                    text = stringResource(Res.string.lucky_needs_post_id),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            TextButton(
                onClick = { onOpenBrowser(NodeSeekSite.BASE_URL + NodeSeekSite.LUCKY_PATH) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.lucky_principle))
            }

            state.generatedLink?.let { link ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                GeneratedLinkCard(
                    link = link,
                    onCopy = { copy(copyLabel, link, copiedText) },
                    onOpen = { onOpenBrowser(link) },
                )
            }
        }
    }

    if (pickingDate) {
        DrawDatePicker(
            millis = state.drawAtMillis,
            onDismiss = { pickingDate = false },
            onPicked = { picked ->
                pickingDate = false
                onDrawAtChange(picked)
            },
        )
    }
    if (pickingTime) {
        DrawTimePicker(
            millis = state.drawAtMillis,
            onDismiss = { pickingTime = false },
            onPicked = { picked ->
                pickingTime = false
                onDrawAtChange(picked)
            },
        )
    }
}

@Composable
private fun DrawTimeRow(
    millis: Long,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                stringResource(Res.string.lucky_draw_time),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = LuckyDraw.formatDrawTime(millis),
                    style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = TABULAR_FIGURES,
                    ),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onPickDate) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = stringResource(Res.string.lucky_pick_date),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp),
                    )
                }
                TextButton(onClick = onPickTime) {
                    Text(stringResource(Res.string.lucky_pick_time))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawDatePicker(
    millis: Long,
    onDismiss: () -> Unit,
    onPicked: (Long) -> Unit,
) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = millis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = pickerState.selectedDateMillis
                    // Only the date changes; the time already chosen is preserved deliberately, so
                    // picking a day does not silently reset the closing hour to midnight.
                    onPicked(selected?.let { combineDate(it, millis) } ?: millis)
                },
            ) {
                Text(stringResource(Res.string.action_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawTimePicker(
    millis: Long,
    onDismiss: () -> Unit,
    onPicked: (Long) -> Unit,
) {
    val current = remember(millis) { millis.toLocalDateTime() }
    val pickerState =
        rememberTimePickerState(
            initialHour = current.hour,
            initialMinute = current.minute,
            is24Hour = true,
        )
    // The mode toggle is the reason this is `TimePickerDialog` and not an `AlertDialog` holding a
    // `TimePicker`: dragging a dial to 23:59 is slow, and typing the time was simply unreachable.
    var displayMode by remember { mutableStateOf(TimePickerDisplayMode.Picker) }
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.lucky_pick_time),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 20.dp),
            )
        },
        modeToggleButton = {
            TimePickerDialogDefaults.DisplayModeToggle(
                onDisplayModeChange = {
                    displayMode =
                        if (displayMode == TimePickerDisplayMode.Picker) {
                            TimePickerDisplayMode.Input
                        } else {
                            TimePickerDisplayMode.Picker
                        }
                },
                displayMode = displayMode,
            )
        },
        confirmButton = {
            TextButton(onClick = { onPicked(combineTime(millis, pickerState.hour, pickerState.minute)) }) {
                Text(stringResource(Res.string.action_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    ) {
        if (displayMode == TimePickerDisplayMode.Input) {
            TimeInput(state = pickerState)
        } else {
            TimePicker(state = pickerState)
        }
    }
}

@Composable
private fun GeneratedLinkCard(
    link: String,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(Res.string.lucky_result),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = link,
                        style =
                        MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onCopy) {
                        Icon(
                            PlazaIcons.ContentCopy,
                            contentDescription = stringResource(Res.string.action_copy_link),
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                FilledTonalButton(
                    onClick = onOpen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                ) {
                    Icon(
                        PlazaIcons.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                    )
                    Text(
                        stringResource(Res.string.lucky_open_web),
                        modifier = Modifier.padding(start = 7.dp),
                    )
                }
                Text(
                    text = stringResource(Res.string.lucky_result_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun Long.toLocalDateTime(): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())

/**
 * The picker reports a UTC midnight; the closing time has to stay the one already chosen.
 *
 * Reading the date back in UTC rather than in the device zone is what keeps "27 日" from becoming the
 * 26th for anyone west of Greenwich — Material's date picker is documented to return midnight UTC.
 */
private fun combineDate(pickedUtcMillis: Long, currentMillis: Long): Long {
    val date: LocalDate = Instant.ofEpochMilli(pickedUtcMillis).atZone(ZoneId.of("UTC")).toLocalDate()
    val time: LocalTime = currentMillis.toLocalDateTime().toLocalTime()
    return date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun combineTime(currentMillis: Long, hour: Int, minute: Int): Long =
    currentMillis
        .toLocalDateTime()
        .toLocalDate()
        .atTime(hour, minute)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

// -------------------------------------------------------------------------------------------------

@Preview(showBackground = true, widthDp = 360, heightDp = 950, name = "9c 幸运抽奖")
@Composable
private fun LuckyPreview() {
    PlazaTheme {
        LuckyScreen(
            state =
            LuckyUiState(
                // 2026-07-27 20:00 UTC+8, the sample from the design.
                drawAtMillis = 1_785_153_600_000L,
                dedupeFloors = true,
                canGenerate = true,
                generatedLink =
                "https://www.nodeseek.com/lucky?post=286417&time=2026-07-27%2020:00&n=3&start=1&unique=1",
            ),
            postIdState = rememberTextFieldState("286417"),
            prizeCountState = rememberTextFieldState("3"),
            startFloorState = rememberTextFieldState("1"),
            onBack = {},
            onDrawAtChange = {},
            onDedupeChange = {},
            onGenerate = {},
            onOpenBrowser = {},
        )
    }
}
