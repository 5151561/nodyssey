package io.github.nodyssey.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.R
import io.github.nodyssey.data.account.TelegramBinding
import io.github.nodyssey.ui.common.NodysseyIcons
import io.github.nodyssey.ui.theme.NodysseyTheme
import io.github.nodyssey.ui.theme.Spacing
import io.github.nodyssey.ui.theme.readableWidth

@Composable
fun ContactRoute(
    viewModel: ContactViewModel,
    onBack: () -> Unit,
    /** The URL, and whether this trip is the Telegram bind — which knows when it is finished. */
    onOpenSite: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.let { accountMessageText(it) }

    LaunchedEffect(state.message, messageText) {
        if (messageText == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(messageText)
        viewModel.consumeMessage()
    }

    // Opened from here rather than the ViewModel — where a URL goes is a UI concern. Both of this
    // screen's site errands (修改邮箱, 绑定 Telegram) need the signed-in session, which lives in the
    // WebView's cookie jar; a Custom Tab is the *browser's* jar and shows 用户未登录 instead.
    LaunchedEffect(state.urlToOpen) {
        state.urlToOpen?.let { url ->
            val forBinding = state.awaitingBinding
            viewModel.consumeUrl()
            onOpenSite(url, forBinding)
        }
    }

    // Coming back is the moment the user wants the answer. Two ways to come back, hence two effects:
    // ON_RESUME for the trip that left the app (the Telegram app, an external browser), and the
    // recomposition for the in-app web view, which never takes the Activity out of RESUMED.
    // `onResumed` is a no-op unless a bind is actually in flight, so neither one fires on arrival.
    LaunchedEffect(Unit) {
        viewModel.onResumed()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResumed()
    }

    ContactScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onChangeEmail = viewModel::changeEmailOnSite,
        onRequestBind = viewModel::requestBind,
        onDismissBind = viewModel::dismissBind,
        onConfirmBind = viewModel::confirmBind,
        onRefreshBinding = viewModel::refreshBinding,
        onRequestUnbind = viewModel::requestUnbind,
        onDismissUnbind = viewModel::dismissUnbind,
        onConfirmUnbind = viewModel::confirmUnbind,
        modifier = modifier,
    )
}

/**
 * 联系方式 (d6 3/5): the email address, the phone block the site itself has disabled, and the
 * Telegram card whose full flow is f3.
 *
 * The two changes this screen cannot make itself — a new email address, a new Telegram binding — say
 * so and open the website, because the site gates both behind browser-only challenges. Drawing a
 * native form for either would be a form that can never submit.
 *
 * The phone card is drawn disabled rather than omitted because its caption is the site's own words —
 * "手机短信验证API暂不可用" — and a user who saw a phone field on the website would otherwise go
 * looking for it here and conclude the app forgot it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactScreen(
    state: ContactUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onChangeEmail: () -> Unit,
    onRequestBind: () -> Unit,
    onDismissBind: () -> Unit,
    onConfirmBind: () -> Unit,
    onRefreshBinding: () -> Unit,
    onRequestUnbind: () -> Unit,
    onDismissUnbind: () -> Unit,
    onConfirmUnbind: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_contact_title)) },
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
    ) { padding ->
        Column(
            modifier =
            Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            AccountSectionLabel(stringResource(R.string.account_email_section))
            CurrentEmailRow(
                email = state.email,
                verified = state.emailVerified,
                onChange = onChangeEmail,
            )
            AccountFieldHelper(stringResource(R.string.account_email_change_on_site))

            AccountSectionLabel(
                stringResource(R.string.account_phone_section),
                modifier = Modifier.padding(top = Spacing.xs),
            )
            DisabledPhoneCard()

            AccountSectionLabel(
                stringResource(R.string.account_telegram_section),
                modifier = Modifier.padding(top = Spacing.xs),
            )
            TelegramCard(
                binding = state.telegram,
                onRequestBind = onRequestBind,
                onRequestUnbind = onRequestUnbind,
            )
        }
    }

    if (state.showBindDialog) {
        TelegramBindDialog(
            isRefreshing = state.isRefreshingBinding,
            onConfirm = onConfirmBind,
            onDismiss = onDismissBind,
            onRefresh = onRefreshBinding,
        )
    }

    if (state.showUnbindDialog) {
        HighRiskDialog(
            icon = NodysseyIcons.LinkOff,
            title = stringResource(R.string.account_telegram_unbind_title),
            body =
            stringResource(
                R.string.account_telegram_unbind_body,
                state.telegram?.displayName ?: stringResource(R.string.account_value_unknown),
            ),
            confirmLabel = stringResource(R.string.account_telegram_unbind_confirm),
            onConfirm = onConfirmUnbind,
            onDismiss = onDismissUnbind,
            destructive = true,
        )
    }
}

@Composable
private fun CurrentEmailRow(
    email: String,
    verified: Boolean,
    onChange: () -> Unit,
) {
    OutlinedTextField(
        value = email.ifEmpty { stringResource(R.string.account_value_unknown) },
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(stringResource(R.string.account_email_current)) },
        shape = AccountFieldShape,
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (email.isNotEmpty() && verified) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.account_email_verified),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                TextButton(onClick = onChange) {
                    Icon(
                        NodysseyIcons.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        stringResource(R.string.account_email_change),
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 添加手机, drawn at the site's own state: disabled, with its exact wording. */
@Composable
private fun DisabledPhoneCard() {
    Surface(
        shape = AccountFieldShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().alpha(DISABLED_CARD_ALPHA),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(
                NodysseyIcons.Sms,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.account_phone_add),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                )
                Text(
                    stringResource(R.string.account_phone_disabled_reason),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Text(
                    stringResource(R.string.account_phone_unavailable),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp),
                )
            }
        }
    }
}

/** The Telegram card, in whichever of f3's two states the account is in. */
@Composable
private fun TelegramCard(
    binding: TelegramBinding?,
    onRequestBind: () -> Unit,
    onRequestUnbind: () -> Unit,
) {
    Surface(
        shape = AccountFieldShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            stringResource(R.string.account_telegram_title),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        )
                        if (binding?.bound == true) BoundChip()
                    }
                    Text(
                        text =
                        when {
                            binding == null -> stringResource(R.string.account_value_unknown)

                            // A binding whose detail call did not answer is still a binding; saying
                            // 未绑定 because a name is missing would be the wrong half to guess.
                            binding.bound ->
                                binding.displayName
                                    ?: stringResource(R.string.account_telegram_bound)

                            else -> stringResource(R.string.account_telegram_unbound)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                stringResource(
                    if (binding?.bound == true) {
                        R.string.account_telegram_bound_hint
                    } else {
                        R.string.account_telegram_bind_hint
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (binding?.bound == true) {
                TextButton(onClick = onRequestUnbind) {
                    Text(
                        stringResource(R.string.account_telegram_unbind),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                Button(onClick = onRequestBind, enabled = binding != null) {
                    Icon(NodysseyIcons.Link, contentDescription = null, modifier = Modifier.size(17.dp))
                    Text(
                        stringResource(R.string.account_telegram_bind),
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
            }
        }
    }
}

@Composable
private fun BoundChip() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(13.dp))
            Text(
                stringResource(R.string.account_telegram_bound),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

/**
 * f3 1/2: the confirmation before leaving for the website.
 *
 * Built by hand rather than with [HighRiskDialog] because it carries two things that dialog cannot
 * hold: the strip explaining why this one leaves the app, and the 「刷新绑定状态」 footer that serves
 * people who already finished on the site before ever seeing this dialog.
 */
@Composable
private fun TelegramBindDialog(
    isRefreshing: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(NodysseyIcons.OpenInNew, contentDescription = null) },
        title = { Text(stringResource(R.string.account_telegram_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    stringResource(R.string.account_telegram_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            stringResource(R.string.account_telegram_dialog_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(R.string.account_telegram_done_question),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onRefresh, enabled = !isRefreshing) {
                        if (isRefreshing) {
                            CircularProgressIndicator(Modifier.size(14.dp))
                        } else {
                            Text(
                                stringResource(R.string.account_telegram_refresh),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.account_telegram_open))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

private const val DISABLED_CARD_ALPHA = 0.55f

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun ContactPreviewUnbound() {
    NodysseyTheme {
        ContactScreen(
            state =
            ContactUiState(
                isLoading = false,
                email = "hikari.zhg@gmail.com",
                emailVerified = true,
                telegram = TelegramBinding(bound = false),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onChangeEmail = {},
            onRequestBind = {},
            onDismissBind = {},
            onConfirmBind = {},
            onRefreshBinding = {},
            onRequestUnbind = {},
            onDismissUnbind = {},
            onConfirmUnbind = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun ContactPreviewBound() {
    NodysseyTheme {
        ContactScreen(
            state =
            ContactUiState(
                isLoading = false,
                email = "hikari.zhg@gmail.com",
                emailVerified = true,
                telegram =
                TelegramBinding(
                    bound = true,
                    displayName = "Hikari Zhg",
                    avatarUrl = "https://t.me/i/userpic/320/hikari.jpg",
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onChangeEmail = {},
            onRequestBind = {},
            onDismissBind = {},
            onConfirmBind = {},
            onRefreshBinding = {},
            onRequestUnbind = {},
            onDismissUnbind = {},
            onConfirmUnbind = {},
        )
    }
}
