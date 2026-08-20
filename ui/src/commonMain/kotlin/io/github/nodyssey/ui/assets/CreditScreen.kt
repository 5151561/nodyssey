package io.github.nodyssey.ui.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.nodyssey.data.CreditEntry
import io.github.nodyssey.ui.common.NoLedgerEntriesState
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.nodyssey.ui.postlist.toSiteError
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.credit_balance
import io.github.nodyssey.ui.resources.credit_entry_total
import io.github.nodyssey.ui.resources.credit_level
import io.github.nodyssey.ui.resources.credit_level_progress
import io.github.nodyssey.ui.resources.credit_title
import io.github.plaza.core.TimeFormat
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.readableWidth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.compose.resources.stringResource

@Composable
fun CreditRoute(
    viewModel: CreditViewModel,
    onBack: () -> Unit,
    onOpenBrowser: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CreditScreen(
        state = state,
        entries = viewModel.entries,
        onBack = onBack,
        onRetry = {
            viewModel.refreshBalance()
            // The header and the list are separate loads, so a single 重试 has to restart both —
            // retrying only the list would leave a stale "—" over freshly loaded rows.
        },
        onOpenBrowser = onOpenBrowser,
        onSignIn = onSignIn,
        modifier = modifier,
    )
}

/**
 * 鸡腿流水 (board d3).
 *
 * The site's four-column table becomes a two-line row: the change and the site's own reason on top,
 * the running total and the timestamp beneath. Four columns do not survive a 360dp width, and of the
 * four the reason is the only one that needs the room.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditScreen(
    state: CreditUiState,
    entries: Flow<PagingData<CreditEntry>>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenBrowser: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = entries.collectAsLazyPagingItems()
    val appBarState = rememberOneHandAppBarState(initiallyExpanded = false)
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(Res.string.credit_title),
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
            Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth(),
        ) {
            ChickenBalanceHeader(state)
            CreditLedger(
                rows = rows,
                onRetry = onRetry,
                onOpenBrowser = onOpenBrowser,
                onSignIn = onSignIn,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The balance, with the level progress it doubles as.
 *
 * NodeSeek's levelling *is* the chicken count — 344 chickens is both the balance and the progress bar
 * — so showing the two as separate figures would invent a distinction the site does not make. The
 * progress line is therefore a caption on the same number, and it disappears entirely above Lv1
 * because no other threshold has ever been published.
 */
@Composable
private fun ChickenBalanceHeader(state: CreditUiState) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md)
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.credit_balance),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = state.chickenCount?.toString() ?: "—",
                    style =
                    MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = TABULAR_FIGURES,
                    ),
                )
            }
            levelProgressText(state)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
                )
            }
        }
    }
}

@Composable
private fun levelProgressText(state: CreditUiState): String? {
    val level = state.level ?: return null
    val chicken = state.chickenCount
    val next = state.nextLevelChicken
    return if (chicken != null && next != null) {
        stringResource(Res.string.credit_level_progress, level, chicken, next)
    } else {
        stringResource(Res.string.credit_level, level)
    }
}

@Composable
private fun CreditLedger(
    rows: LazyPagingItems<CreditEntry>,
    onRetry: () -> Unit,
    onOpenBrowser: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val refresh = rows.loadState.refresh
    when {
        refresh is LoadState.Loading && rows.itemCount == 0 -> LoadingState(modifier)

        refresh is LoadState.Error && rows.itemCount == 0 ->
            SiteErrorState(
                error = refresh.error.toSiteError(),
                onRetry = {
                    onRetry()
                    rows.retry()
                },
                onOpenBrowser = onOpenBrowser,
                onSignIn = onSignIn,
                modifier = modifier,
            )

        refresh is LoadState.NotLoading && rows.itemCount == 0 -> NoLedgerEntriesState(modifier)

        else ->
            LazyColumn(modifier) {
                // The row id would be the natural key and this endpoint does not publish one: its
                // rows are positional arrays with no id column. The running total is the next best
                // thing — it is unique per row in a ledger that only ever appends — with the index
                // behind it for the one case that breaks it, an adjustment of exactly zero.
                items(
                    count = rows.itemCount,
                    key = { index -> rows.peek(index)?.let { "${it.balanceAfter}-${it.createdAtMillis}" } ?: "index-$index" },
                ) { index ->
                    rows[index]?.let { entry ->
                        CreditRow(entry)
                        LedgerRowDivider()
                    }
                }
                ledgerFooter(rows, endNote = null)
            }
    }
}

@Composable
private fun CreditRow(entry: CreditEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = signedAmount(entry.change),
                style =
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
                // Gains are the site's primary colour, spends are muted rather than an error red:
                // feeding someone a chicken is a thing you meant to do, not a fault.
                color =
                if (entry.change < 0) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Text(
                text = entry.reason,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text =
            listOfNotNull(
                entry.balanceAfter?.let { stringResource(Res.string.credit_entry_total, it) },
                entry.createdAtMillis?.let(TimeFormat::absolute),
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// -------------------------------------------------------------------------------------------------

private val previewEntries =
    listOf(
        CreditEntry(1, 384, "回帖奖励", 1_785_573_691_000),
        CreditEntry(2, 383, "签到收益2个鸡腿", 1_785_567_394_000),
        CreditEntry(5, 373, "发帖奖励", 1_785_398_200_000),
        CreditEntry(1, 363, "被StreamingPub投喂鸡腿", 1_785_308_455_000),
        CreditEntry(-1, 350, "投喂鸡腿", 1_785_143_620_000),
    )

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "d3 鸡腿流水")
@Composable
private fun CreditPreview() {
    PlazaTheme {
        CreditScreen(
            state = CreditUiState(level = 1, chickenCount = 384, nextLevelChicken = 400),
            entries = flowOf(PagingData.from(previewEntries)),
            onBack = {},
            onRetry = {},
            onOpenBrowser = {},
            onSignIn = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "d3 鸡腿流水 · dark")
@Composable
private fun CreditDarkPreview() {
    PlazaTheme(darkTheme = true) {
        CreditScreen(
            state = CreditUiState(level = 2, chickenCount = 1_240),
            entries = flowOf(PagingData.from(previewEntries)),
            onBack = {},
            onRetry = {},
            onOpenBrowser = {},
            onSignIn = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "d3 鸡腿流水 · 未登录")
@Composable
private fun CreditSignInPreview() {
    PlazaTheme {
        CreditScreen(
            state = CreditUiState(),
            entries =
            flowOf(
                PagingData.empty(
                    sourceLoadStates =
                    androidx.paging.LoadStates(
                        refresh = LoadState.Error(SiteException(SiteError.LoginRequired)),
                        prepend = LoadState.NotLoading(true),
                        append = LoadState.NotLoading(true),
                    ),
                ),
            ),
            onBack = {},
            onRetry = {},
            onOpenBrowser = {},
            onSignIn = {},
        )
    }
}
