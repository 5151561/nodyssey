package io.github.nodyssey.ui.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.nodyssey.R
import io.github.nodyssey.core.TimeFormat
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.data.StardustEntry
import io.github.nodyssey.data.StardustType
import io.github.nodyssey.ui.common.LoadingState
import io.github.nodyssey.ui.common.NoLedgerEntriesState
import io.github.nodyssey.ui.common.NodeSeekErrorState
import io.github.nodyssey.ui.common.NodysseyIcons
import io.github.nodyssey.ui.common.SpendConfirmDialog
import io.github.nodyssey.ui.common.SpendDetail
import io.github.nodyssey.ui.common.digitsOnly
import io.github.nodyssey.ui.postlist.toNodeSeekError
import io.github.nodyssey.ui.theme.NodysseyTheme
import io.github.nodyssey.ui.theme.Spacing
import io.github.nodyssey.ui.theme.TABULAR_FIGURES
import io.github.nodyssey.ui.theme.readableWidth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Composable
fun StardustRoute(
    viewModel: StardustViewModel,
    onBack: () -> Unit,
    onOpenBrowser: () -> Unit,
    onTransferOnSite: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StardustScreen(
        state = state,
        entries = viewModel.entries,
        amountState = viewModel.amount,
        recipientState = viewModel.recipientUid,
        refState = viewModel.refId,
        onBack = onBack,
        onRetry = viewModel::refresh,
        onOpenBrowser = onOpenBrowser,
        onSignIn = onSignIn,
        onOpenTransfer = viewModel::openTransfer,
        onDismissTransfer = viewModel::dismissTransfer,
        onRequestConfirm = viewModel::requestConfirm,
        onDismissConfirm = viewModel::dismissConfirm,
        onConfirmTransfer = {
            viewModel.transferHandedOff()
            onTransferOnSite()
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StardustScreen(
    state: StardustUiState,
    entries: Flow<PagingData<StardustEntry>>,
    amountState: TextFieldState,
    recipientState: TextFieldState,
    refState: TextFieldState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenBrowser: () -> Unit,
    onSignIn: () -> Unit,
    onOpenTransfer: () -> Unit,
    onDismissTransfer: () -> Unit,
    onRequestConfirm: () -> Unit,
    onDismissConfirm: () -> Unit,
    onConfirmTransfer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = entries.collectAsLazyPagingItems()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stardust_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            // No uid yet means no per-member ledger URL to hand the transfer to — the button that
            // would end on the site's home page with the typed form lost is better absent.
            if (state.uid != null) {
                ExtendedFloatingActionButton(
                    onClick = onOpenTransfer,
                    icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                    text = { Text(stringResource(R.string.transfer_action)) },
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth(),
        ) {
            BalanceHeader(state.balance)
            StardustLedger(
                state = state,
                rows = rows,
                onRetry = onRetry,
                onOpenBrowser = onOpenBrowser,
                onSignIn = onSignIn,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (state.transferOpen) {
        TransferDialog(
            state = state,
            amountState = amountState,
            recipientState = recipientState,
            refState = refState,
            onDismiss = onDismissTransfer,
            onRequestConfirm = onRequestConfirm,
        )
    }

    if (state.confirmOpen) {
        TransferConfirmDialog(
            state = state,
            onConfirm = onConfirmTransfer,
            onDismiss = onDismissConfirm,
        )
    }
}

@Composable
private fun BalanceHeader(balance: Int?) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md)
            .fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.stardust_balance),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = balance?.toString() ?: "—",
                style =
                MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
            )
            Text(
                text = stringResource(R.string.stardust_balance_hint),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun StardustLedger(
    state: StardustUiState,
    rows: LazyPagingItems<StardustEntry>,
    onRetry: () -> Unit,
    onOpenBrowser: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val refresh = rows.loadState.refresh
    // The profile error comes first because it is upstream of everything: without a uid the ledger was
    // never requested, so Paging is sitting in its initial Loading state and would otherwise show a
    // spinner that can never resolve.
    val error = state.error ?: (refresh as? LoadState.Error)?.error?.toNodeSeekError()
    val retry = {
        onRetry()
        rows.retry()
    }
    when {
        error != null && rows.itemCount == 0 ->
            NodeSeekErrorState(
                error = error,
                onRetry = retry,
                onOpenBrowser = onOpenBrowser,
                onSignIn = onSignIn,
                modifier = modifier,
            )

        state.uid == null || (refresh is LoadState.Loading && rows.itemCount == 0) ->
            LoadingState(modifier)

        refresh is LoadState.NotLoading && rows.itemCount == 0 -> NoLedgerEntriesState(modifier)

        else ->
            LazyColumn(modifier) {
                items(count = rows.itemCount, key = { index -> rows.peek(index)?.id ?: "index-$index" }) { index ->
                    rows[index]?.let { entry ->
                        StardustRow(entry)
                        LedgerRowDivider()
                    }
                }
                ledgerFooter(rows, endNote = null)
            }
    }
}

/**
 * One stardust movement.
 *
 * The row is built around the movement's *kind*, which is the correction this screen needed: board 8e
 * drew every row as "点赞 +1" on the belief that liked comments were the only source and transfer the
 * only use. The site's own label map has five kinds, and `diff` is signed — an invite-code purchase and
 * an outgoing transfer both subtract. A row that hardcodes a plus would misreport the ones that matter
 * most, so the amount carries its own sign and the leading icon tells the kinds apart at a glance.
 */
@Composable
private fun StardustRow(entry: StardustEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                entry.type.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = entry.typeLabel(),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                entry.createdAtMillis?.let {
                    Text(
                        text = TimeFormat.absolute(it),
                        style =
                        MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = entry.metaLine(),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = signedAmount(entry.diff),
            style =
            MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
            color =
            if (entry.diff < 0) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}

@Composable
private fun StardustEntry.typeLabel(): String =
    when (type) {
        StardustType.UPVOTE -> stringResource(R.string.stardust_type_upvote)

        StardustType.TRANSFER -> stringResource(R.string.stardust_type_transfer)

        StardustType.BUY_CODE -> stringResource(R.string.stardust_type_buy_code)

        StardustType.SYSTEM -> stringResource(R.string.stardust_type_system)

        StardustType.ADMIN -> stringResource(R.string.stardust_type_admin)

        // The site's own word beats a label we invented for a kind we have never seen.
        StardustType.UNKNOWN -> rawType ?: stringResource(R.string.stardust_type_unknown)
    }

private fun StardustType.icon(): ImageVector =
    when (this) {
        StardustType.UPVOTE -> Icons.Default.ThumbUp
        StardustType.TRANSFER -> Icons.AutoMirrored.Filled.Send
        StardustType.BUY_CODE -> NodysseyIcons.ConfirmationNumber
        StardustType.SYSTEM -> Icons.Default.Info
        StardustType.ADMIN -> NodysseyIcons.Gavel
        StardustType.UNKNOWN -> NodysseyIcons.Wallet
    }

/**
 * The second line: the balance left behind, then whatever identifies this particular movement.
 *
 * Which identifier is worth showing depends on the kind. A liked comment is identified by the comment;
 * a transfer by the other party and the Ref ID the site makes both sides quote. Showing every field on
 * every row would fill the line with `Ref 10` on rows where 10 is a constant nobody needs.
 */
@Composable
private fun StardustEntry.metaLine(): String =
    listOfNotNull(
        balanceAfter?.let { stringResource(R.string.stardust_entry_balance, it) },
        commentId?.takeIf { type == StardustType.UPVOTE }
            ?.let { stringResource(R.string.stardust_entry_comment, it) },
        peerUid?.let { stringResource(R.string.stardust_entry_peer, it) },
        refId?.takeIf { type == StardustType.TRANSFER || type == StardustType.BUY_CODE }
            ?.let { stringResource(R.string.stardust_entry_ref, it) },
    ).joinToString(" · ")

/**
 * The transfer form, matching the site's three fields exactly.
 *
 * A dialog rather than a bottom sheet, for two reasons that point the same way: the site presents
 * transfer as a modal layer (8f's own framing), and a dialog window resizes for the keyboard — the
 * sheet provably did not on a real device, hiding the fields behind the IME as they were typed into.
 *
 * No memo field: the site's own layer has none. It does have a recipient-name lookup the app has not
 * adopted yet (`/api/stardust/payment-prepare` answers with `receiver_name`), so until it does, the
 * caution line under the fields stays the only protection against a mistyped uid.
 */
@Composable
private fun TransferDialog(
    state: StardustUiState,
    amountState: TextFieldState,
    recipientState: TextFieldState,
    refState: TextFieldState,
    onDismiss: () -> Unit,
    onRequestConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.transfer_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                NumberField(amountState, stringResource(R.string.transfer_amount))
                NumberField(recipientState, stringResource(R.string.transfer_recipient))
                NumberField(refState, stringResource(R.string.transfer_ref))
                Text(
                    text = stringResource(R.string.transfer_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onRequestConfirm, enabled = state.form.isComplete) {
                Text(stringResource(R.string.transfer_next))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun NumberField(
    state: TextFieldState,
    label: String,
) {
    OutlinedTextField(
        state = state,
        label = { Text(label) },
        lineLimits = TextFieldLineLimits.SingleLine,
        inputTransformation = digitsOnly(StardustViewModel.MAX_FIELD_LENGTH),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TransferConfirmDialog(
    state: StardustUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val amount = state.form.amountValue ?: return
    SpendConfirmDialog(
        title = stringResource(R.string.transfer_confirm_title, amount),
        details =
        buildList {
            add(
                SpendDetail(
                    stringResource(R.string.transfer_amount),
                    stringResource(R.string.transfer_amount_value, amount),
                ),
            )
            state.form.recipientValue?.let {
                add(SpendDetail(stringResource(R.string.transfer_recipient), it.toString()))
            }
            state.form.refValue?.let {
                add(SpendDetail(stringResource(R.string.transfer_ref), it.toString()))
            }
            state.balance?.let { balance ->
                // With enough balance the row answers "what will be left"; short of it there is no
                // after to describe, so the row honestly labels what it shows — the balance itself.
                add(
                    if (state.shortfall == null) {
                        SpendDetail(
                            stringResource(R.string.transfer_balance_after),
                            stringResource(R.string.transfer_balance_change, balance, balance - amount),
                        )
                    } else {
                        SpendDetail(stringResource(R.string.spend_current_balance), balance.toString())
                    },
                )
            }
        },
        // Two sentences: what cannot be undone, and where the last step actually happens. Saying the
        // hand-off *before* the tap is what stops the web view from looking like a failure.
        caution =
        stringResource(R.string.transfer_caution) + "\n" + stringResource(R.string.transfer_opened_web),
        confirmLabel = stringResource(R.string.transfer_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        icon = NodysseyIcons.Wallet,
        shortfall = state.shortfall?.let { stringResource(R.string.transfer_shortfall, it) },
    )
}

// -------------------------------------------------------------------------------------------------

private val previewEntries =
    listOf(
        StardustEntry(187_103, StardustType.UPVOTE, "upvote", 1, 6, 9_667, 11_491_930, 10, 1_785_496_901_000),
        StardustEntry(187_098, StardustType.UPVOTE, "upvote", 1, 5, 62_158, 11_491_930, 10, 1_785_496_626_000),
        StardustEntry(186_400, StardustType.TRANSFER, "transfer", -2, 3, 4_471, null, 108, 1_785_388_939_000),
        StardustEntry(186_100, StardustType.BUY_CODE, "buyCode", -1, 4, null, null, 10, 1_785_236_042_000),
        StardustEntry(157_149, StardustType.SYSTEM, "system", 3, 5, null, null, 10, 1_781_567_592_000),
    )

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "8e 星辰流水")
@Composable
private fun StardustPreview() {
    NodysseyTheme {
        PreviewScreen(
            StardustUiState(isLoadingBalance = false, uid = 52_425, balance = 6),
            flowOf(PagingData.from(previewEntries)),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "8e 星辰流水 · dark")
@Composable
private fun StardustDarkPreview() {
    NodysseyTheme(darkTheme = true) {
        PreviewScreen(
            StardustUiState(isLoadingBalance = false, uid = 52_425, balance = 6),
            flowOf(PagingData.from(previewEntries)),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "8e 星辰流水 · 未登录")
@Composable
private fun StardustSignInPreview() {
    NodysseyTheme {
        PreviewScreen(
            StardustUiState(isLoadingBalance = false, error = NodeSeekError.LoginRequired),
            flowOf(PagingData.empty()),
        )
    }
}

@Composable
private fun PreviewScreen(
    state: StardustUiState,
    entries: Flow<PagingData<StardustEntry>>,
) {
    StardustScreen(
        state = state,
        entries = entries,
        amountState = rememberTextFieldState(),
        recipientState = rememberTextFieldState(),
        refState = rememberTextFieldState(),
        onBack = {},
        onRetry = {},
        onOpenBrowser = {},
        onSignIn = {},
        onOpenTransfer = {},
        onDismissTransfer = {},
        onRequestConfirm = {},
        onDismissConfirm = {},
        onConfirmTransfer = {},
    )
}
