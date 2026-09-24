package io.github.nodyssey.ui.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.CreditEntry
import io.github.nodyssey.ui.common.GrowthProgressBar
import io.github.nodyssey.ui.common.NoLedgerEntriesState
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.nodyssey.ui.common.webViewUrl
import io.github.nodyssey.ui.postlist.toSiteError
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.assets_quota_value
import io.github.nodyssey.ui.resources.credit_balance
import io.github.nodyssey.ui.resources.credit_column_change
import io.github.nodyssey.ui.resources.credit_column_total
import io.github.nodyssey.ui.resources.credit_entry_total
import io.github.nodyssey.ui.resources.credit_progress_remaining
import io.github.nodyssey.ui.resources.credit_title
import io.github.nodyssey.ui.resources.credit_unit_level
import io.github.plaza.core.TimeFormat
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.SectionLabel
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
    onOpenBrowser: (String) -> Unit,
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
 * 鸡腿流水 (8b).
 *
 * The site's four-column table becomes a three-part row on one white card: the change in a fixed
 * column at the start, the site's own reason with its time under it, and the running total at the end.
 * Four columns do not survive a 360dp width, and of the four the reason is the only one that needs the
 * room — so it gets the middle and the numbers sit either side of it, where they line up down the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditScreen(
    state: CreditUiState,
    entries: Flow<PagingData<CreditEntry>>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = entries.collectAsLazyPagingItems()
    // Collapsed to start with: when there are no rows yet, the balance card and a full-height status
    // card share a column that does not scroll, and under an open title the status card's 登录 / 重试
    // starts below the fold.
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
        CreditLedger(
            state = state,
            rows = rows,
            onRetry = onRetry,
            onOpenBrowser = onOpenBrowser,
            onSignIn = onSignIn,
            modifier =
            Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth(),
        )
    }
}

/**
 * The balance, with the level progress it doubles as.
 *
 * NodeSeek's levelling *is* the chicken count — 344 chickens is both the balance and the progress bar
 * — so showing the two as separate figures would invent a distinction the site does not make. The bar
 * is therefore drawn under the balance itself, over the current level's span (Lv2 is 400 → 900), in
 * the tertiary tone the 鸡腿 tile wears on 我的 and 账户与成长.
 */
@Composable
private fun ChickenBalanceHeader(state: CreditUiState) {
    LayerCard(
        modifier = Modifier.padding(horizontal = LayerPageGutter),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = state.chickenCount?.toString() ?: "—",
                style =
                MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text =
                state.level?.let { stringResource(Res.string.credit_unit_level, it) }
                    ?: stringResource(Res.string.credit_balance),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
        state.levelProgress?.let { progress ->
            GrowthProgressBar(progress = progress, color = MaterialTheme.colorScheme.tertiary)
        }
        levelProgressText(state)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun levelProgressText(state: CreditUiState): String? {
    val chicken = state.chickenCount ?: return null
    val next = state.nextLevelChicken ?: return null
    val level = state.level ?: return null
    val remaining = next - chicken
    return if (remaining > 0) {
        stringResource(Res.string.credit_progress_remaining, chicken, next, remaining, level + 1)
    } else {
        stringResource(Res.string.assets_quota_value, chicken, next)
    }
}

@Composable
private fun CreditLedger(
    state: CreditUiState,
    rows: LazyPagingItems<CreditEntry>,
    onRetry: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val refresh = rows.loadState.refresh
    if (rows.itemCount == 0) {
        // Not a list yet — loading, failed, or genuinely empty. The header stays above whichever it is,
        // since the balance is its own load and is usually already there.
        Column(modifier) {
            ChickenBalanceHeader(state)
            val stateModifier = Modifier.fillMaxSize()
            when (refresh) {
                is LoadState.Loading -> LoadingState(stateModifier)

                is LoadState.Error ->
                    SiteErrorState(
                        error = refresh.error.toSiteError(),
                        onRetry = {
                            onRetry()
                            rows.retry()
                        },
                        // Named rather than left to [SiteErrorState]'s fallback. This screen asks for a
                        // web view in one place only, so the two are the same closure — and a challenge
                        // reaching it by fallback is exactly how other screens ended up handing one to a
                        // plain reading view without anything in the code saying so.
                        onOpenBrowser = {
                            onOpenBrowser(
                                refresh.error.toSiteError()
                                    .webViewUrl(NodeSeekSite.BASE_URL + NodeSeekSite.CREDIT_PATH),
                            )
                        },
                        onVerify = onOpenBrowser,
                        onSignIn = onSignIn,
                        modifier = stateModifier,
                    )

                is LoadState.NotLoading -> NoLedgerEntriesState(stateModifier)
            }
        }
        return
    }

    LazyColumn(modifier) {
        item(key = "balance") { ChickenBalanceHeader(state) }
        item(key = "columns") {
            Row(Modifier.padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 8.dp)) {
                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                SectionLabel(
                    stringResource(Res.string.credit_column_change),
                    Modifier.weight(1f),
                    color = muted,
                    contentPadding = PaddingValues(),
                )
                SectionLabel(stringResource(Res.string.credit_column_total), color = muted, contentPadding = PaddingValues())
            }
        }
        // The row id would be the natural key and this endpoint does not publish one: its
        // rows are positional arrays with no id column. The running total is the next best
        // thing — it is unique per row in a ledger that only ever appends — with the index
        // behind it for the one case that breaks it, an adjustment of exactly zero.
        items(
            count = rows.itemCount,
            key = { index -> rows.peek(index)?.let { "${it.balanceAfter}-${it.createdAtMillis}" } ?: "index-$index" },
        ) { index ->
            rows[index]?.let { entry ->
                LedgerSlice(first = index == 0, last = index == rows.itemCount - 1) {
                    CreditRow(entry)
                }
            }
        }
        ledgerFooter(rows, endNote = null)
    }
}

@Composable
private fun CreditRow(entry: CreditEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = Spacing.lg, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = signedAmount(entry.change),
            style =
            MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
            color = ledgerAmountColor(entry.change),
            // A floor rather than a width, and one line: the column lines up for the everyday one-
            // and two-digit changes, and a 「−1000」 at a large text size widens its own row instead
            // of breaking into 「−100」 over 「0」.
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.widthIn(min = 56.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = entry.reason,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            entry.createdAtMillis?.let {
                Text(
                    text = TimeFormat.absolute(it),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        entry.balanceAfter?.let { total ->
            // Read aloud as 总计 N rather than a bare number trailing the reason; the column label
            // that says so on screen is one item up and out of the row's reach.
            val description = stringResource(Res.string.credit_entry_total, total)
            Text(
                text = total.toString(),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_FIGURES),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { contentDescription = description },
            )
        }
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
            state = CreditUiState(level = 1, chickenCount = 344, levelFloorChicken = 100, nextLevelChicken = 400),
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
