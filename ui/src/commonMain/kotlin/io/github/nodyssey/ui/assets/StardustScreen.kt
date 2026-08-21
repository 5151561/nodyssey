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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import io.github.nodyssey.data.StardustEntry
import io.github.nodyssey.data.StardustType
import io.github.nodyssey.ui.common.NoLedgerEntriesState
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.nodyssey.ui.common.SpendConfirmDialog
import io.github.nodyssey.ui.common.SpendDetail
import io.github.nodyssey.ui.common.siteErrorRecovery
import io.github.nodyssey.ui.common.snackbarDuration
import io.github.nodyssey.ui.postlist.toSiteError
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.spend_current_balance
import io.github.nodyssey.ui.resources.stardust_balance
import io.github.nodyssey.ui.resources.stardust_balance_hint
import io.github.nodyssey.ui.resources.stardust_entry_balance
import io.github.nodyssey.ui.resources.stardust_entry_comment
import io.github.nodyssey.ui.resources.stardust_entry_peer
import io.github.nodyssey.ui.resources.stardust_entry_ref
import io.github.nodyssey.ui.resources.stardust_title
import io.github.nodyssey.ui.resources.stardust_type_admin
import io.github.nodyssey.ui.resources.stardust_type_buy_code
import io.github.nodyssey.ui.resources.stardust_type_system
import io.github.nodyssey.ui.resources.stardust_type_transfer
import io.github.nodyssey.ui.resources.stardust_type_unknown
import io.github.nodyssey.ui.resources.stardust_type_upvote
import io.github.nodyssey.ui.resources.status_challenge_title
import io.github.nodyssey.ui.resources.status_network_title
import io.github.nodyssey.ui.resources.status_rate_limited_title
import io.github.nodyssey.ui.resources.status_sign_in_title
import io.github.nodyssey.ui.resources.transfer_action
import io.github.nodyssey.ui.resources.transfer_amount
import io.github.nodyssey.ui.resources.transfer_amount_value
import io.github.nodyssey.ui.resources.transfer_balance_after
import io.github.nodyssey.ui.resources.transfer_balance_change
import io.github.nodyssey.ui.resources.transfer_caution
import io.github.nodyssey.ui.resources.transfer_checking_name
import io.github.nodyssey.ui.resources.transfer_confirm
import io.github.nodyssey.ui.resources.transfer_confirm_title
import io.github.nodyssey.ui.resources.transfer_failed
import io.github.nodyssey.ui.resources.transfer_hint
import io.github.nodyssey.ui.resources.transfer_name_unknown
import io.github.nodyssey.ui.resources.transfer_next
import io.github.nodyssey.ui.resources.transfer_recipient
import io.github.nodyssey.ui.resources.transfer_ref
import io.github.nodyssey.ui.resources.transfer_sending
import io.github.nodyssey.ui.resources.transfer_sent
import io.github.nodyssey.ui.resources.transfer_shortfall
import io.github.nodyssey.ui.resources.transfer_title
import io.github.plaza.core.TimeFormat
import io.github.plaza.core.net.SiteError
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.digitsOnly
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.readableWidth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.compose.resources.stringResource

@Composable
fun StardustRoute(
    viewModel: StardustViewModel,
    onBack: () -> Unit,
    onOpenBrowser: () -> Unit,
    onSignIn: () -> Unit,
    /** Clears a Cloudflare challenge on the ledger, then comes back to the transfer form. */
    onVerify: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val message = state.message
    val messageText = message?.let { stardustMessageText(it) }
    // A refused 转账 is the one refusal on this screen worth a button: the amount and the recipient
    // are still in the form, so clearing the wall is the difference between sending it and typing it
    // again. Only [StardustMessage.Failed] carries an error — 「已转出」 has nothing to recover from.
    val recovery =
        (message as? StardustMessage.Failed)?.error?.let { error ->
            siteErrorRecovery(error, onVerify = onVerify, onSignIn = onSignIn)
        }

    LaunchedEffect(state.message, messageText) {
        if (messageText == null) return@LaunchedEffect
        val result =
            snackbarHostState.showSnackbar(
                message = messageText,
                actionLabel = recovery?.label,
                duration =
                (message as? StardustMessage.Failed)
                    ?.error
                    ?.let { snackbarDuration(it) } ?: SnackbarDuration.Short,
            )
        viewModel.consumeMessage()
        if (result == SnackbarResult.ActionPerformed) recovery?.onClick?.invoke()
    }

    StardustScreen(
        state = state,
        entries = viewModel.entries,
        snackbarHostState = snackbarHostState,
        amountState = viewModel.amount,
        recipientState = viewModel.recipientUid,
        refState = viewModel.refId,
        onBack = onBack,
        onRetry = viewModel::refresh,
        onOpenBrowser = onOpenBrowser,
        onSignIn = onSignIn,
        onVerify = onVerify,
        onOpenTransfer = viewModel::openTransfer,
        onDismissTransfer = viewModel::dismissTransfer,
        onRequestConfirm = viewModel::requestConfirm,
        onDismissConfirm = viewModel::dismissConfirm,
        onConfirmTransfer = viewModel::confirmTransfer,
        modifier = modifier,
    )
}

/**
 * The one line the transfer gets to say afterwards.
 *
 * A refusal is shown in the site's own words whenever it gave any — "余额不足", "Ref ID 不正确" — because
 * those name the field to fix, and every sentence this app could substitute would be vaguer.
 */
@Composable
private fun stardustMessageText(message: StardustMessage): String =
    when (message) {
        is StardustMessage.Sent -> stringResource(Res.string.transfer_sent, message.amount)

        is StardustMessage.Failed ->
            message.detail ?: stringResource(
                when (message.error) {
                    SiteError.Cloudflare -> Res.string.status_challenge_title
                    SiteError.LoginRequired -> Res.string.status_sign_in_title
                    SiteError.Network -> Res.string.status_network_title
                    SiteError.RateLimited -> Res.string.status_rate_limited_title
                    else -> Res.string.transfer_failed
                },
            )
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StardustScreen(
    state: StardustUiState,
    entries: Flow<PagingData<StardustEntry>>,
    snackbarHostState: SnackbarHostState,
    amountState: TextFieldState,
    recipientState: TextFieldState,
    refState: TextFieldState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenBrowser: () -> Unit,
    onSignIn: () -> Unit,
    /** Clears a Cloudflare challenge on the ledger; see [StardustRoute] for why it is its own. */
    onVerify: () -> Unit,
    onOpenTransfer: () -> Unit,
    onDismissTransfer: () -> Unit,
    onRequestConfirm: () -> Unit,
    onDismissConfirm: () -> Unit,
    onConfirmTransfer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = entries.collectAsLazyPagingItems()
    val appBarState = rememberOneHandAppBarState(initiallyExpanded = false)
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(Res.string.stardust_title),
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // No uid means the profile call has not answered — nobody to send from and no balance to
            // check the amount against, so the form would be a form that cannot submit.
            if (state.uid != null) {
                ExtendedFloatingActionButton(
                    onClick = onOpenTransfer,
                    icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                    text = { Text(stringResource(Res.string.transfer_action)) },
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
                onVerify = onVerify,
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
                text = stringResource(Res.string.stardust_balance),
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
                text = stringResource(Res.string.stardust_balance_hint),
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
    onVerify: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val refresh = rows.loadState.refresh
    // The profile error comes first because it is upstream of everything: without a uid the ledger was
    // never requested, so Paging is sitting in its initial Loading state and would otherwise show a
    // spinner that can never resolve.
    val error = state.error ?: (refresh as? LoadState.Error)?.error?.toSiteError()
    val retry = {
        onRetry()
        rows.retry()
    }
    when {
        error != null && rows.itemCount == 0 ->
            SiteErrorState(
                error = error,
                onRetry = retry,
                onOpenBrowser = onOpenBrowser,
                onSignIn = onSignIn,
                onVerify = onVerify,
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
        StardustType.UPVOTE -> stringResource(Res.string.stardust_type_upvote)

        StardustType.TRANSFER -> stringResource(Res.string.stardust_type_transfer)

        StardustType.BUY_CODE -> stringResource(Res.string.stardust_type_buy_code)

        StardustType.SYSTEM -> stringResource(Res.string.stardust_type_system)

        StardustType.ADMIN -> stringResource(Res.string.stardust_type_admin)

        // The site's own word beats a label we invented for a kind we have never seen.
        StardustType.UNKNOWN -> rawType ?: stringResource(Res.string.stardust_type_unknown)
    }

private fun StardustType.icon(): ImageVector =
    when (this) {
        StardustType.UPVOTE -> Icons.Default.ThumbUp
        StardustType.TRANSFER -> Icons.AutoMirrored.Filled.Send
        StardustType.BUY_CODE -> PlazaIcons.ConfirmationNumber
        StardustType.SYSTEM -> Icons.Default.Info
        StardustType.ADMIN -> PlazaIcons.Gavel
        StardustType.UNKNOWN -> PlazaIcons.Wallet
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
        balanceAfter?.let { stringResource(Res.string.stardust_entry_balance, it) },
        commentId?.takeIf { type == StardustType.UPVOTE }
            ?.let { stringResource(Res.string.stardust_entry_comment, it) },
        peerUid?.let { stringResource(Res.string.stardust_entry_peer, it) },
        refId?.takeIf { type == StardustType.TRANSFER || type == StardustType.BUY_CODE }
            ?.let { stringResource(Res.string.stardust_entry_ref, it) },
    ).joinToString(" · ")

/**
 * The transfer form, matching the site's three fields exactly.
 *
 * A dialog rather than a bottom sheet, for two reasons that point the same way: the site presents
 * transfer as a modal layer (8f's own framing), and a dialog window resizes for the keyboard — the
 * sheet provably did not on a real device, hiding the fields behind the IME as they were typed into.
 *
 * No memo field: the site's own layer has none. The recipient's name is not asked for here either —
 * 下一步 fetches it, so the check lands on the step that is about to spend rather than on the one
 * still being typed into.
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
        title = { Text(stringResource(Res.string.transfer_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                NumberField(amountState, stringResource(Res.string.transfer_amount))
                NumberField(recipientState, stringResource(Res.string.transfer_recipient))
                NumberField(refState, stringResource(Res.string.transfer_ref))
                Text(
                    text = stringResource(Res.string.transfer_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onRequestConfirm, enabled = state.form.isComplete) {
                Text(stringResource(Res.string.transfer_next))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
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

/**
 * The step that spends.
 *
 * The recipient row is the point of it: `payment-prepare` answers the uid with the name the site
 * itself would show, so the row carries that name and the caution line underneath says which of the
 * two situations the user is in — a named recipient, or a uid nothing came back for.
 */
@Composable
private fun TransferConfirmDialog(
    state: StardustUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val amount = state.form.amountValue ?: return
    SpendConfirmDialog(
        title = stringResource(Res.string.transfer_confirm_title, amount),
        details =
        buildList {
            add(
                SpendDetail(
                    stringResource(Res.string.transfer_amount),
                    stringResource(Res.string.transfer_amount_value, amount),
                ),
            )
            state.form.recipientValue?.let {
                add(
                    SpendDetail(
                        stringResource(Res.string.transfer_recipient),
                        it.toString(),
                        note = state.recipient?.note(),
                    ),
                )
            }
            state.form.refValue?.let {
                add(SpendDetail(stringResource(Res.string.transfer_ref), it.toString()))
            }
            state.balance?.let { balance ->
                // With enough balance the row answers "what will be left"; short of it there is no
                // after to describe, so the row honestly labels what it shows — the balance itself.
                add(
                    if (state.shortfall == null) {
                        SpendDetail(
                            stringResource(Res.string.transfer_balance_after),
                            stringResource(Res.string.transfer_balance_change, balance, balance - amount),
                        )
                    } else {
                        SpendDetail(stringResource(Res.string.spend_current_balance), balance.toString())
                    },
                )
            }
        },
        // Two sentences: that it cannot be undone, and — when the lookup came back empty-handed — that
        // the uid on the row above went unverified, which is the one thing left for the user to check.
        caution =
        listOfNotNull(
            stringResource(Res.string.transfer_caution),
            (state.recipient as? RecipientCheck.Unnamed)
                ?.let { it.reason ?: stringResource(Res.string.transfer_name_unknown) },
        ).joinToString("\n"),
        confirmLabel =
        stringResource(if (state.isSending) Res.string.transfer_sending else Res.string.transfer_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        icon = PlazaIcons.Wallet,
        shortfall = state.shortfall?.let { stringResource(Res.string.transfer_shortfall, it) },
        isSending = state.isSending,
    )
}

/** What the recipient row says beside the uid while, and after, the site is asked who it belongs to. */
@Composable
private fun RecipientCheck.note(): String? =
    when (this) {
        RecipientCheck.Checking -> stringResource(Res.string.transfer_checking_name)

        is RecipientCheck.Named -> name

        // The reason belongs in the caution line, not here: a refusal sentence sitting where a name
        // goes would read as the name.
        is RecipientCheck.Unnamed -> null
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
    PlazaTheme {
        PreviewScreen(
            StardustUiState(isLoadingBalance = false, uid = 52_425, balance = 6),
            flowOf(PagingData.from(previewEntries)),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "8e 星辰流水 · dark")
@Composable
private fun StardustDarkPreview() {
    PlazaTheme(darkTheme = true) {
        PreviewScreen(
            StardustUiState(isLoadingBalance = false, uid = 52_425, balance = 6),
            flowOf(PagingData.from(previewEntries)),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "8e 星辰流水 · 未登录")
@Composable
private fun StardustSignInPreview() {
    PlazaTheme {
        PreviewScreen(
            StardustUiState(isLoadingBalance = false, error = SiteError.LoginRequired),
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
        snackbarHostState = remember { SnackbarHostState() },
        amountState = rememberTextFieldState(),
        recipientState = rememberTextFieldState(),
        refState = rememberTextFieldState(),
        onBack = {},
        onRetry = {},
        onOpenBrowser = {},
        onSignIn = {},
        onVerify = {},
        onOpenTransfer = {},
        onDismissTransfer = {},
        onRequestConfirm = {},
        onDismissConfirm = {},
        onConfirmTransfer = {},
    )
}
