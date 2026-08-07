package io.github.nodyssey.ui.history

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.R
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.TimeFormat
import io.github.nodyssey.data.ReadHistoryEntry
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.ui.common.AvatarCapOffset
import io.github.nodyssey.ui.common.BoardTag
import io.github.nodyssey.ui.common.ChoiceRow
import io.github.nodyssey.ui.common.LoadingState
import io.github.nodyssey.ui.common.MetaText
import io.github.nodyssey.ui.common.NodysseyIcons
import io.github.nodyssey.ui.common.SectionLabel
import io.github.nodyssey.ui.common.StatusView
import io.github.nodyssey.ui.common.ThreadRow
import io.github.nodyssey.ui.common.ThreadRowTitle
import io.github.nodyssey.ui.common.UserAvatar
import io.github.nodyssey.ui.theme.NodysseyTheme
import io.github.nodyssey.ui.theme.Sizes
import io.github.nodyssey.ui.theme.Spacing
import io.github.nodyssey.ui.theme.StatusShapes
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Composable
fun ReadHistoryRoute(
    viewModel: ReadHistoryViewModel,
    onBack: () -> Unit,
    onPostClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReadHistoryScreen(
        state = state,
        onBack = onBack,
        onPostClick = onPostClick,
        onRemove = viewModel::remove,
        onRestore = viewModel::restore,
        onClearAll = viewModel::clear,
        onLimitChange = viewModel::setLimit,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadHistoryScreen(
    state: ReadHistoryUiState,
    onBack: () -> Unit,
    onPostClick: (Long) -> Unit,
    onRemove: (ReadHistoryEntry) -> Unit,
    onRestore: (ReadHistoryEntry) -> Unit,
    onClearAll: () -> Unit,
    onLimitChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Injectable so the relative stamps and the day grouping can be asserted without racing the clock. */
    nowMillis: Long = System.currentTimeMillis(),
) {
    var confirmClear by remember { mutableStateOf(false) }
    var pickingLimit by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val removedMessage = stringResource(R.string.history_removed)
    val undoLabel = stringResource(R.string.history_undo)

    // Removing one row also drops that thread's unread baseline, so a swipe the reader did not mean
    // costs them the 「N 条新回复」 on it. Cheap to offer the way back; the row carries everything
    // needed to write it again.
    val removeWithUndo: (ReadHistoryEntry) -> Unit = { entry ->
        onRemove(entry)
        scope.launch {
            val outcome =
                snackbarHostState.showSnackbar(
                    message = removedMessage,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Short,
                )
            if (outcome == SnackbarResult.ActionPerformed) onRestore(entry)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.history_title))
                        // The one place the retention setting is visible without opening a menu:
                        // "how much does this thing keep" is the question the screen itself raises.
                        if (state.entries.isNotEmpty()) {
                            Text(
                                text =
                                stringResource(
                                    R.string.history_subtitle,
                                    state.entries.size,
                                    limitLabel(state.limit),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    HistoryMenu(
                        limit = state.limit,
                        canClear = state.entries.isNotEmpty(),
                        onPickLimit = { pickingLimit = true },
                        onClearAll = { confirmClear = true },
                    )
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> LoadingState()

                state.entries.isEmpty() ->
                    StatusView(
                        icon = NodysseyIcons.History,
                        shape = StatusShapes.Empty,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        title = stringResource(R.string.history_empty_title),
                        description = stringResource(R.string.history_empty_body),
                    )

                else -> {
                    val sections = remember(state.entries, nowMillis) { historySections(state.entries, nowMillis) }
                    LazyColumn(Modifier.fillMaxSize()) {
                        sections.forEach { section ->
                            stickyHeader(key = section.bucket) {
                                HistorySectionHeader(stringResource(section.bucket.labelRes))
                            }
                            items(section.entries, key = ReadHistoryEntry::postId) { entry ->
                                HistoryRow(
                                    entry = entry,
                                    stamp = historyStamp(entry, section.bucket, nowMillis),
                                    onClick = { onPostClick(entry.postId) },
                                    onRemove = { removeWithUndo(entry) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            // Spelled out rather than a bare "确定吗": these rows are also the unread baselines, so
            // clearing them un-reads every thread in the feed. That is not something to discover
            // afterwards by noticing the list has changed colour.
            text = { Text(stringResource(R.string.history_clear_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClearAll()
                    },
                ) { Text(stringResource(R.string.history_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (pickingLimit) {
        HistoryLimitDialog(
            current = state.limit,
            onSelect = onLimitChange,
            onDismiss = { pickingLimit = false },
        )
    }
}

/**
 * 保留条数 and 全部清除, behind one overflow.
 *
 * 全部清除 used to be a text button in the bar. It is the most destructive thing on the screen and it
 * sat one mis-tap from 返回, which is not where a button that un-reads the whole feed belongs.
 */
@Composable
private fun HistoryMenu(
    limit: Int,
    canClear: Boolean,
    onPickLimit: () -> Unit,
    onClearAll: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history_limit_title)) },
                leadingIcon = { Icon(NodysseyIcons.History, contentDescription = null) },
                trailingIcon = {
                    Text(
                        text = limitLabel(limit),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = {
                    expanded = false
                    onPickLimit()
                },
            )
            if (canClear) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.history_clear_all),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        expanded = false
                        onClearAll()
                    },
                )
            }
        }
    }
}

/**
 * One read thread, laid out like the feed row it came from: avatar, title, one 12sp meta line.
 *
 * Deliberately not a `ListItem`. The history is the same objects the feed lists, and rendering them
 * in a different vocabulary — a clock icon on every row, the board as plain text — made the screen
 * read as a different app's list. The clock is gone for the same reason a "history" badge on every
 * row of the history carries no information.
 */
@Composable
private fun HistoryRow(
    entry: ReadHistoryEntry,
    stamp: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A thread read before the snapshot columns existed, or one whose page never carried a title.
    // The id is not much, but it is true, and it still opens the thread.
    val title = entry.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.history_untitled, entry.postId)
    val removeLabel = stringResource(R.string.history_remove, title)
    // Deliberately `remember` and not `rememberSwipeToDismissBoxState`, which is a `rememberSaveable`.
    // A LazyColumn saves each item's state under its key and hands it back when an item with that key
    // returns — so a row put back by 撤销 came back still holding "dismissed", and SwipeToDismissBox
    // fired onDismiss again the moment it composed. The row was deleted, restored, and deleted again
    // within a frame, which read as 撤销 doing nothing at all. A swipe is a gesture in progress: it has
    // no business surviving the row it was performed on.
    val positionalThreshold = SwipeToDismissBoxDefaults.positionalThreshold
    val dismissState =
        remember(positionalThreshold) {
            SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold)
        }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = { onRemove() },
        backgroundContent = { RemoveBackdrop() },
    ) {
        ThreadRow(
            onClick = onClick,
            // Swipe is a mouse-and-eyes gesture; TalkBack gets the same action by name. On the row
            // rather than on the dismiss box so it lands on the node TalkBack actually focuses.
            modifier =
            Modifier.semantics {
                customActions = listOf(
                    CustomAccessibilityAction(removeLabel) {
                        onRemove()
                        true
                    },
                )
            },
            leading = {
                UserAvatar(
                    // The uid is all the snapshot kept, which is all the site needs: avatars are
                    // served at /avatar/<uid>.png. A row with no uid falls back to the initial.
                    url = entry.authorUid?.let(NodeSeekSite::avatarUrl),
                    name = entry.authorName?.takeIf { it.isNotBlank() } ?: title,
                    size = Sizes.avatarList,
                    modifier = Modifier.offset(y = AvatarCapOffset),
                )
            },
            title = {
                ThreadRowTitle(
                    text = AnnotatedString(title),
                    // Every row here is read by definition, so the feed's read/unread weight split
                    // would say nothing. Full contrast: the title is what the reader is scanning for.
                    fontWeight = FontWeight.Medium,
                )
            },
        ) {
            // No slug in the snapshot; the tag falls back to matching on the board's name.
            BoardTag(title = entry.categoryTitle, slug = null)
            entry.authorName?.takeIf { it.isNotBlank() }?.let { MetaText(it, singleLine = true) }
            entry.commentCount?.let {
                MetaText(stringResource(R.string.post_reply_count, it), singleLine = true)
            }
            MetaText(stamp, singleLine = true)
        }
    }
}

/** What sits under a row being swiped away. */
@Composable
private fun RemoveBackdrop() {
    Row(
        modifier =
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = Spacing.xl),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Delete,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/**
 * 今天 / 昨天 / 最近七天 / 更早.
 *
 * Sticky, so a long scroll always says which day it is looking at — the timestamps alone answer
 * "how long ago" but not "where am I", and the answer is the reason anyone opens this screen.
 * Opaque for the same reason: the rows scroll underneath it.
 */
@Composable
private fun HistorySectionHeader(label: String) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        // 10dp + SectionLabel's own 4dp start inset lines the label up with the row titles' gutter.
        SectionLabel(label, Modifier.padding(start = 10.dp, top = Spacing.md))
    }
}

@Composable
private fun HistoryLimitDialog(
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_limit_title)) },
        text = {
            Column {
                // The cost of a small number is not obvious from the number, so it is stated here
                // rather than left for the reader to work out from their feed going un-greyed.
                Text(
                    text = stringResource(R.string.history_limit_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
                SettingsRepository.READ_HISTORY_LIMIT_CHOICES.forEach { choice ->
                    ChoiceRow(
                        label = limitChoiceLabel(choice),
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

/** `300 条` / `无上限` — the short form, for the bar and the menu. */
@Composable
private fun limitLabel(limit: Int): String =
    if (limit == SettingsRepository.READ_HISTORY_UNLIMITED) {
        stringResource(R.string.history_limit_unlimited)
    } else {
        stringResource(R.string.history_limit_count, limit)
    }

/** The same, plus which one is the default — worth knowing while choosing, and noise everywhere else. */
@Composable
private fun limitChoiceLabel(limit: Int): String =
    if (limit == SettingsRepository.DEFAULT_READ_HISTORY_LIMIT) {
        stringResource(R.string.history_limit_default, limit)
    } else {
        limitLabel(limit)
    }

/**
 * What the row says about when it was read, given the heading it already sits under.
 *
 * A row under 昨天 that also said "昨天" was the first version, and it wasted the most useful slot on
 * the line to repeat the header. The day is the heading's job; inside today and yesterday the row
 * adds the clock, and further back — where the heading is only a range — it adds the distance.
 */
private fun historyStamp(
    entry: ReadHistoryEntry,
    bucket: HistoryBucket,
    nowMillis: Long,
): String =
    when (bucket) {
        HistoryBucket.Today, HistoryBucket.Yesterday -> TimeFormat.clock(entry.lastReadAtMillis)
        HistoryBucket.Week, HistoryBucket.Earlier -> TimeFormat.relative(entry.lastReadAtMillis, nowMillis)
    }

/** The day headings, newest first. */
internal enum class HistoryBucket(
    @StringRes val labelRes: Int,
) {
    Today(R.string.history_section_today),
    Yesterday(R.string.history_section_yesterday),
    Week(R.string.history_section_week),
    Earlier(R.string.history_section_earlier),
}

internal data class HistorySection(
    val bucket: HistoryBucket,
    val entries: List<ReadHistoryEntry>,
)

/**
 * Buckets the history by calendar day rather than by elapsed hours.
 *
 * Calendar days because that is how a reader remembers reading something: a thread opened at 00:30
 * belongs to 今天 even though "22 小时前" would put it a day back. The bucket order is the enum's, so
 * an out-of-order list — which the database never returns, but a preview might — still reads top to
 * bottom. A timestamp in the future (a clock that moved) lands in 今天; there is no heading for it
 * and pretending it is old would be worse.
 */
internal fun historySections(
    entries: List<ReadHistoryEntry>,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): List<HistorySection> {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return entries
        .groupBy { entry ->
            val day = Instant.ofEpochMilli(entry.lastReadAtMillis).atZone(zone).toLocalDate()
            when (ChronoUnit.DAYS.between(day, today)) {
                1L -> HistoryBucket.Yesterday
                in 2L..6L -> HistoryBucket.Week
                in 7L..Long.MAX_VALUE -> HistoryBucket.Earlier
                else -> HistoryBucket.Today
            }
        }.map { (bucket, rows) -> HistorySection(bucket, rows) }
        .sortedBy { it.bucket.ordinal }
}

@Preview
@Composable
private fun ReadHistoryScreenPreview() {
    val now = 1_800_000_000_000L
    NodysseyTheme {
        ReadHistoryScreen(
            state =
            ReadHistoryUiState(
                isLoading = false,
                entries =
                listOf(
                    ReadHistoryEntry(1, "绿云抢鸡竞赛又要开始了", "ipv4", 34378, "日常", 42, now - 3 * 60 * 60 * 1000L),
                    ReadHistoryEntry(2, "求一个便宜的小鸡跑 NAT", "someone", 1, "交易", 7, now - 26 * 60 * 60 * 1000L),
                    ReadHistoryEntry(3, null, null, null, null, null, now - 5 * 24 * 60 * 60 * 1000L),
                ),
            ),
            onBack = {},
            onPostClick = {},
            onRemove = {},
            onRestore = {},
            onClearAll = {},
            onLimitChange = {},
            nowMillis = now,
        )
    }
}
