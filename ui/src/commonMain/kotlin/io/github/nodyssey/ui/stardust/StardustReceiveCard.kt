package io.github.nodyssey.ui.stardust

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.ui.common.SpendConfirmDialog
import io.github.nodyssey.ui.common.SpendDetail
import io.github.nodyssey.ui.common.describedAsLoading
import io.github.nodyssey.ui.common.shortMessage
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.stardust_receive_confirm_amount
import io.github.nodyssey.ui.resources.stardust_receive_confirm_amount_label
import io.github.nodyssey.ui.resources.stardust_receive_confirm_caution
import io.github.nodyssey.ui.resources.stardust_receive_confirm_caution_onetime
import io.github.nodyssey.ui.resources.stardust_receive_confirm_note_label
import io.github.nodyssey.ui.resources.stardust_receive_confirm_ok
import io.github.nodyssey.ui.resources.stardust_receive_confirm_payee_label
import io.github.nodyssey.ui.resources.stardust_receive_confirm_ref_label
import io.github.nodyssey.ui.resources.stardust_receive_confirm_title
import io.github.nodyssey.ui.resources.stardust_receive_load_failed
import io.github.nodyssey.ui.resources.stardust_receive_loading
import io.github.nodyssey.ui.resources.stardust_receive_paid
import io.github.nodyssey.ui.resources.stardust_receive_pay
import io.github.nodyssey.ui.resources.stardust_receive_payers
import io.github.nodyssey.ui.resources.stardust_receive_retry
import io.github.nodyssey.ui.resources.stardust_receive_sign_in_first
import io.github.nodyssey.ui.resources.stardust_receive_total
import io.github.nodyssey.ui.resources.stardust_receive_unpaid
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.richtext.StardustReceiveCard
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import org.jetbrains.compose.resources.stringResource

@Composable
fun StardustReceiveCard(
    node: RichNode.StardustReceive,
    viewModel: StardustReceiveViewModel,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StardustReceiveCard(
        node = node,
        state = state,
        onPay = viewModel::pay,
        onRetry = viewModel::load,
        onSignIn = onSignIn,
        onFailureShown = viewModel::onFailureShown,
        modifier = modifier,
    )
}

/**
 * A 星辰收款码 with the parts only a signed-in thread can know: who has paid, and a button to.
 *
 * The card itself is [io.github.plaza.designsys.richtext.StardustReceiveCard] — the same one a
 * signature or a direct message draws — and everything added here hangs below its rule. Which is the
 * point: a reader should recognise the code as the same object wherever it turns up, and only find
 * that here it can be acted on.
 *
 * 付款 is guarded by [SpendConfirmDialog], the app's single layer in front of an irreversible spend.
 * The payee's own code offers no button at all: the site would refuse it, and a button whose only
 * outcome is a refusal is worse than no button.
 */
@Composable
fun StardustReceiveCard(
    node: RichNode.StardustReceive,
    state: StardustReceiveUiState,
    onPay: () -> Unit,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    onFailureShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by remember { mutableStateOf(false) }

    StardustReceiveCard(
        node = node,
        modifier = modifier,
        avatarUrl = NodeSeekSite.avatarUrl(node.memberId),
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(vertical = Spacing.sm),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = tallyText(state),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            when {
                // A tally that will not load says so and offers the retry; the 付款 button stays
                // away, because paying without knowing whether you already have is the one thing a
                // one-off code makes expensive.
                state.error != null -> TextButton(onClick = onRetry) { Text(stringResource(Res.string.stardust_receive_retry)) }

                state.isSelf(node.memberId) -> Unit

                else ->
                    Button(
                        onClick = { if (state.isSignedIn) confirming = true else onSignIn() },
                        enabled = !state.isPaying,
                    ) {
                        if (state.isPaying) {
                            CircularProgressIndicator(Modifier.size(18.dp).describedAsLoading(), strokeWidth = 2.dp)
                        } else {
                            Text(
                                stringResource(
                                    if (state.isSignedIn) {
                                        Res.string.stardust_receive_pay
                                    } else {
                                        Res.string.stardust_receive_sign_in_first
                                    },
                                ),
                            )
                        }
                    }
            }
        }

        // The site's own sentence — 余额不足, 已经支付过 — beats anything written here, and it stays on
        // the card rather than in a snackbar because the card is what it is about.
        state.failure?.let { failure ->
            Text(
                text = failure.detail?.takeIf { it.isNotBlank() } ?: failure.error.shortMessage(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }

    if (confirming) {
        SpendConfirmDialog(
            title = stringResource(Res.string.stardust_receive_confirm_title, node.amount),
            details =
            listOfNotNull(
                SpendDetail(
                    label = stringResource(Res.string.stardust_receive_confirm_amount_label),
                    value = stringResource(Res.string.stardust_receive_confirm_amount, node.amount),
                ),
                SpendDetail(
                    label = stringResource(Res.string.stardust_receive_confirm_payee_label),
                    value = "UID ${node.memberId}",
                ),
                SpendDetail(
                    label = stringResource(Res.string.stardust_receive_confirm_ref_label),
                    value = node.refId.toString(),
                ),
                node.description.takeIf { it.isNotBlank() }?.let {
                    SpendDetail(
                        label = stringResource(Res.string.stardust_receive_confirm_note_label),
                        value = it,
                    )
                },
            ),
            caution =
            stringResource(
                if (node.onetime) {
                    Res.string.stardust_receive_confirm_caution_onetime
                } else {
                    Res.string.stardust_receive_confirm_caution
                },
            ),
            confirmLabel = stringResource(Res.string.stardust_receive_confirm_ok),
            isSending = state.isPaying,
            onConfirm = {
                confirming = false
                onFailureShown()
                onPay()
            },
            onDismiss = { confirming = false },
        )
    }
}

/**
 * The one line under the rule, assembled from what is known so far.
 *
 * Nulls are silence, not zero. A tally still in flight must not print "你未付款，共 0 人付款" and then
 * change its mind — for a one-off code that first reading is the one somebody acts on.
 */
@Composable
private fun tallyText(state: StardustReceiveUiState): String {
    if (state.error != null) return stringResource(Res.string.stardust_receive_load_failed)
    val parts =
        listOfNotNull(
            state.paidByMe?.let {
                stringResource(if (it) Res.string.stardust_receive_paid else Res.string.stardust_receive_unpaid)
            },
            state.payerCount?.let { stringResource(Res.string.stardust_receive_payers, it) },
            state.received?.let { stringResource(Res.string.stardust_receive_total, it) },
        )
    return parts.joinToString(" · ").ifEmpty { stringResource(Res.string.stardust_receive_loading) }
}

@Preview(showBackground = true, widthDp = 360, name = "收款码 · 未付款")
@Composable
private fun StardustReceiveCardPreview() {
    PlazaTheme {
        StardustReceiveCard(
            node =
            RichNode.StardustReceive(
                memberId = 52425,
                refId = 100,
                amount = 2,
                description = "请我喝杯咖啡",
                onetime = true,
            ),
            state =
            StardustReceiveUiState(
                payerCount = 3,
                received = 6,
                paidByMe = false,
                isLoading = false,
                isSignedIn = true,
                selfUid = 9,
            ),
            onPay = {},
            onRetry = {},
            onSignIn = {},
            onFailureShown = {},
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, name = "收款码 · 自己的码")
@Composable
private fun StardustReceiveOwnCardPreview() {
    PlazaTheme {
        StardustReceiveCard(
            node =
            RichNode.StardustReceive(
                memberId = 52425,
                refId = 866042,
                amount = 10,
                description = "拼车续费 第 3 期",
            ),
            state =
            StardustReceiveUiState(
                payerCount = 0,
                received = 0,
                paidByMe = false,
                isLoading = false,
                isSignedIn = true,
                selfUid = 52425,
            ),
            onPay = {},
            onRetry = {},
            onSignIn = {},
            onFailureShown = {},
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}
