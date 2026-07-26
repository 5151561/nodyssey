package io.github.nsreader.ui.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nsreader.R
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.data.StardustEntry
import io.github.nsreader.ui.common.LoadingState
import io.github.nsreader.ui.common.NodeSeekErrorState
import io.github.nsreader.ui.common.NodeSeekIcons
import io.github.nsreader.ui.common.SpendConfirmDialog
import io.github.nsreader.ui.common.SpendDetail
import io.github.nsreader.ui.theme.NodeSeekTheme
import io.github.nsreader.ui.theme.Spacing
import io.github.nsreader.ui.theme.TABULAR_FIGURES
import io.github.nsreader.ui.theme.readableWidth

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
        onBack = onBack,
        onRetry = viewModel::refresh,
        onOpenBrowser = onOpenBrowser,
        onSignIn = onSignIn,
        onOpenTransfer = viewModel::openTransfer,
        onDismissTransfer = viewModel::dismissTransfer,
        onFormChange = viewModel::updateForm,
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
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenBrowser: () -> Unit,
    onSignIn: () -> Unit,
    onOpenTransfer: () -> Unit,
    onDismissTransfer: () -> Unit,
    onFormChange: (TransferForm) -> Unit,
    onRequestConfirm: () -> Unit,
    onDismissConfirm: () -> Unit,
    onConfirmTransfer: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    icon = { Icon(Icons.Default.Send, contentDescription = null) },
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
            onDismiss = onDismissTransfer,
            onFormChange = onFormChange,
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
    onRetry: () -> Unit,
    onOpenBrowser: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading && state.entries.isEmpty() -> LoadingState(modifier)

        state.error != null && state.entries.isEmpty() ->
            NodeSeekErrorState(
                error = state.error,
                onRetry = onRetry,
                onOpenBrowser = onOpenBrowser,
                onSignIn = onSignIn,
                modifier = modifier,
            )

        else ->
            LazyColumn(modifier) {
                items(count = state.entries.size, key = { state.entries[it].commentId ?: it.toLong() }) { index ->
                    StardustRow(state.entries[index])
                }
                item(key = "footer") {
                    Text(
                        text = stringResource(R.string.stardust_end),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp, bottom = 96.dp),
                    )
                }
            }
    }
}

/**
 * One +1.
 *
 * Every stardust row is a comment of yours being liked, so the icon is a thumb and the amount is always
 * a gain. The meta line carries what the site's table carried: the balance it left, which comment, when.
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
                Icons.Default.ThumbUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.stardust_entry_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text =
                listOfNotNull(
                    entry.balanceAfter?.let { stringResource(R.string.stardust_entry_balance, it) },
                    entry.commentId?.let { stringResource(R.string.stardust_entry_comment, it) },
                    entry.timestampText,
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.stardust_entry_amount, entry.amount),
            style =
            MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * The transfer form, matching the site's three fields exactly.
 *
 * A dialog rather than a bottom sheet, for two reasons that point the same way: the site presents
 * transfer as a modal layer (8f's own framing), and a dialog window resizes for the keyboard — the
 * sheet provably did not on a real device, hiding the fields behind the IME as they were typed into.
 *
 * No memo field and no recipient name lookup: the site's own layer has neither, and a nickname echo
 * would need an endpoint that does not exist. The caution line under the fields says so, because the
 * only protection against a mistyped uid here is the user reading it back.
 */
@Composable
private fun TransferDialog(
    state: StardustUiState,
    onDismiss: () -> Unit,
    onFormChange: (TransferForm) -> Unit,
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
                NumberField(
                    value = state.form.amount,
                    label = stringResource(R.string.transfer_amount),
                    onValueChange = { onFormChange(state.form.copy(amount = it)) },
                )
                NumberField(
                    value = state.form.recipientUid,
                    label = stringResource(R.string.transfer_recipient),
                    onValueChange = { onFormChange(state.form.copy(recipientUid = it)) },
                )
                NumberField(
                    value = state.form.refId,
                    label = stringResource(R.string.transfer_ref),
                    onValueChange = { onFormChange(state.form.copy(refId = it)) },
                )
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
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter(Char::isDigit).take(MAX_NUMBER_LENGTH)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val MAX_NUMBER_LENGTH = 12

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
        icon = NodeSeekIcons.Wallet,
        shortfall = state.shortfall?.let { stringResource(R.string.transfer_shortfall, it) },
    )
}

// -------------------------------------------------------------------------------------------------

private val previewEntries =
    listOf(
        StardustEntry(1, 4, 866042, "2026/7/21 18:09:50"),
        StardustEntry(1, 3, 861377, "2026/6/30 09:41:12"),
        StardustEntry(1, 2, 852206, "2026/5/18 22:03:45"),
        StardustEntry(1, 1, 838914, "2026/3/2 12:57:08"),
    )

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "8e 星辰流水")
@Composable
private fun StardustPreview() {
    NodeSeekTheme {
        PreviewScreen(StardustUiState(isLoading = false, balance = 4, entries = previewEntries))
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "8e 流水未接入 · dark")
@Composable
private fun StardustNotWiredPreview() {
    NodeSeekTheme(darkTheme = true) {
        PreviewScreen(StardustUiState(isLoading = false, balance = 4, error = NodeSeekError.NotWired))
    }
}

@Composable
private fun PreviewScreen(state: StardustUiState) {
    StardustScreen(
        state = state,
        onBack = {},
        onRetry = {},
        onOpenBrowser = {},
        onSignIn = {},
        onOpenTransfer = {},
        onDismissTransfer = {},
        onFormChange = {},
        onRequestConfirm = {},
        onDismissConfirm = {},
        onConfirmTransfer = {},
    )
}
