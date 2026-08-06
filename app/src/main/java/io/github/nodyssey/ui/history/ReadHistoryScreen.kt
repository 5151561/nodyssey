package io.github.nodyssey.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.R
import io.github.nodyssey.core.TimeFormat
import io.github.nodyssey.data.ReadHistoryEntry
import io.github.nodyssey.ui.common.LoadingState
import io.github.nodyssey.ui.common.NodysseyIcons
import io.github.nodyssey.ui.common.StatusView
import io.github.nodyssey.ui.theme.NodysseyTheme
import io.github.nodyssey.ui.theme.StatusShapes

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
        onClearAll = viewModel::clear,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadHistoryScreen(
    state: ReadHistoryUiState,
    onBack: () -> Unit,
    onPostClick: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    /** Injectable so the relative stamps can be asserted without the test racing the wall clock. */
    nowMillis: Long = System.currentTimeMillis(),
) {
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (state.entries.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }) {
                            Text(stringResource(R.string.history_clear_all))
                        }
                    }
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

                else ->
                    LazyColumn {
                        items(state.entries, key = ReadHistoryEntry::postId) { entry ->
                            HistoryRow(
                                entry = entry,
                                nowMillis = nowMillis,
                                onClick = { onPostClick(entry.postId) },
                                onRemove = { onRemove(entry.postId) },
                            )
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
}

@Composable
private fun HistoryRow(
    entry: ReadHistoryEntry,
    nowMillis: Long,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    // A thread read before the snapshot columns existed, or one whose page never carried a title.
    // The id is not much, but it is true, and it still opens the thread.
    val title = entry.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.history_untitled, entry.postId)
    ListItem(
        headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(entry.subtitle(nowMillis)) },
        leadingContent = {
            Icon(
                NodysseyIcons.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.history_remove, title),
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** `日常 · someone · 3 天前`, skipping whatever the snapshot did not capture. */
private fun ReadHistoryEntry.subtitle(nowMillis: Long): String =
    listOfNotNull(
        categoryTitle?.takeIf { it.isNotBlank() },
        authorName?.takeIf { it.isNotBlank() },
        TimeFormat.relative(lastReadAtMillis, nowMillis),
    ).joinToString(SUBTITLE_SEPARATOR)

private const val SUBTITLE_SEPARATOR = " · "

@Preview
@Composable
private fun ReadHistoryScreenPreview() {
    NodysseyTheme {
        ReadHistoryScreen(
            state =
            ReadHistoryUiState(
                isLoading = false,
                entries =
                listOf(
                    ReadHistoryEntry(1, "绿云抢鸡竞赛又要开始了", "ipv4", 34378, "日常", 42, 1_000_000L),
                    ReadHistoryEntry(2, null, null, null, null, null, 900_000L),
                ),
            ),
            onBack = {},
            onPostClick = {},
            onRemove = {},
            onClearAll = {},
            // Two days after the newest row, so the preview shows the relative stamps doing work.
            nowMillis = 1_000_000L + 2 * 24 * 60 * 60 * 1000L,
        )
    }
}
