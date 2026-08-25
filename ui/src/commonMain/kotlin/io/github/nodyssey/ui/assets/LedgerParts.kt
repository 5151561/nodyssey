package io.github.nodyssey.ui.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import io.github.nodyssey.ui.common.describedAsLoading
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_retry
import io.github.nodyssey.ui.resources.ledger_amount_gain
import io.github.nodyssey.ui.resources.ledger_amount_spend
import io.github.nodyssey.ui.resources.ledger_end
import io.github.nodyssey.ui.resources.ledger_load_more_failed
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import org.jetbrains.compose.resources.stringResource

/**
 * The bottom of a ledger, whichever ledger it is.
 *
 * Both flows page forwards only and both end in the same three possibilities — more coming, more
 * failed, nothing more — so the footer is written once. It replaces the boards' 「加载更多」 button
 * with Paging's own prefetch, which is the same decision the boards' own note argues for
 * ("App 折叠为按时间连续加载"): a button that has to be pressed once per twenty rows is the numeric
 * pager again with worse ergonomics. The failure row keeps an explicit retry, because that is the one
 * case where the list genuinely cannot continue on its own.
 */
internal fun <T : Any> LazyListScope.ledgerFooter(
    items: LazyPagingItems<T>,
    endNote: String?,
) {
    when (val append = items.loadState.append) {
        is LoadState.Loading ->
            item("appending") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp).describedAsLoading())
                }
            }

        is LoadState.Error ->
            item("append-failed") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(Res.string.ledger_load_more_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = items::retry) { Text(stringResource(Res.string.action_retry)) }
                }
            }

        is LoadState.NotLoading ->
            // Only once the append is genuinely exhausted: `endOfPaginationReached` is what tells
            // "no more pages" apart from "idle between pages", and announcing the end early would be
            // a lie the user cannot check.
            if (append.endOfPaginationReached && items.itemCount > 0) {
                item("end") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp, bottom = 96.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.ledger_end),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        endNote?.let {
                            Text(
                                text = it,
                                style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontFeatureSettings = TABULAR_FIGURES,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
    }
}

/** The hairline between ledger rows, so the two screens cannot drift apart on inset or colour. */
@Composable
internal fun LedgerRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = Spacing.lg),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * A signed amount, with the sign carried by the glyph rather than by colour alone.
 *
 * U+2212 MINUS SIGN, not a hyphen: at the tabular-figure sizes these rows use, a hyphen is short
 * enough to read as a dash between two numbers. Colour distinguishes gain from spend on top of that,
 * never instead of it.
 */
@Composable
internal fun signedAmount(change: Int): String =
    if (change < 0) {
        stringResource(Res.string.ledger_amount_spend, -change)
    } else {
        stringResource(Res.string.ledger_amount_gain, change)
    }
