package io.github.nsreader.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nsreader.R
import io.github.nsreader.data.account.TelegramBinding
import io.github.nsreader.ui.common.NodeSeekIcons
import io.github.nsreader.ui.theme.NodeSeekTheme
import io.github.nsreader.ui.theme.Spacing
import io.github.nsreader.ui.theme.readableWidth

@Composable
fun ContactRoute(
    viewModel: ContactViewModel,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
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

    // The bot link is opened from here rather than the ViewModel — leaving the app is a UI concern.
    LaunchedEffect(state.bindUrlToOpen) {
        state.bindUrlToOpen?.let { url ->
            viewModel.consumeBindUrl()
            onOpenUrl(url)
        }
    }

    // Coming back from Telegram is the moment the user wants the answer; see onResumed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResumed()
    }

    ContactScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onToggleEmailChange = viewModel::toggleEmailChange,
        onPasswordChange = viewModel::updatePassword,
        onNewEmailChange = viewModel::updateNewEmail,
        onCodeChange = viewModel::updateCode,
        onSendCode = viewModel::sendCode,
        onConfirmEmailChange = viewModel::confirmEmailChange,
        onRequestBind = viewModel::requestBind,
        onDismissBind = viewModel::dismissBind,
        onConfirmBind = viewModel::confirmBind,
        onRefreshBinding = viewModel::refreshBinding,
        onRequestUnbind = viewModel::requestUnbind,
        onDismissUnbind = viewModel::dismissUnbind,
        onConfirmUnbind = viewModel::confirmUnbind,
        onOpenBotChat = { onOpenUrl(TELEGRAM_BOT_CHAT_URL) },
        modifier = modifier,
    )
}

/**
 * 联系方式 (d6 3/5): the email with its two-step change flow, the phone block the site itself has
 * disabled, and the Telegram card whose full flow is f3.
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
    onToggleEmailChange: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onNewEmailChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onConfirmEmailChange: () -> Unit,
    onRequestBind: () -> Unit,
    onDismissBind: () -> Unit,
    onConfirmBind: () -> Unit,
    onRefreshBinding: () -> Unit,
    onRequestUnbind: () -> Unit,
    onDismissUnbind: () -> Unit,
    onConfirmUnbind: () -> Unit,
    onOpenBotChat: () -> Unit,
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
                // Without this the keyboard hides the code field at the bottom of the change flow.
                .imePadding()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (state.endpointPending) EndpointPendingBanner()

            AccountSectionLabel(stringResource(R.string.account_email_section))
            CurrentEmailRow(
                email = state.email,
                verified = state.emailVerified,
                changeExpanded = state.changeExpanded,
                onToggleChange = onToggleEmailChange,
            )
            if (state.changeExpanded) {
                EmailChangeFlow(
                    state = state,
                    onPasswordChange = onPasswordChange,
                    onNewEmailChange = onNewEmailChange,
                    onCodeChange = onCodeChange,
                    onSendCode = onSendCode,
                    onConfirm = onConfirmEmailChange,
                )
            }

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
                onOpenBotChat = onOpenBotChat,
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
            icon = NodeSeekIcons.LinkOff,
            title = stringResource(R.string.account_telegram_unbind_title),
            body =
            stringResource(
                R.string.account_telegram_unbind_body,
                state.telegram?.username ?: stringResource(R.string.account_value_unknown),
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
    changeExpanded: Boolean,
    onToggleChange: () -> Unit,
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
                TextButton(onClick = onToggleChange) {
                    Text(
                        stringResource(
                            if (changeExpanded) R.string.action_cancel else R.string.account_email_change,
                        ),
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The site's own two-step ritual, drawn as one card so it reads as a single transaction. */
@Composable
private fun EmailChangeFlow(
    state: ContactUiState,
    onPasswordChange: (String) -> Unit,
    onNewEmailChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onConfirm: () -> Unit,
) {
    Surface(
        shape = AccountFieldShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                stringResource(R.string.account_email_step_password),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                label = { Text(stringResource(R.string.account_password_current)) },
                shape = AccountFieldShape,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                stringResource(R.string.account_email_step_verify),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = Spacing.xs),
            )
            OutlinedTextField(
                value = state.newEmail,
                onValueChange = onNewEmailChange,
                singleLine = true,
                isError = state.isNewEmailMalformed,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                label = { Text(stringResource(R.string.account_email_new)) },
                supportingText =
                if (state.isNewEmailMalformed) {
                    { Text(stringResource(R.string.account_email_invalid)) }
                } else {
                    null
                },
                shape = AccountFieldShape,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedTextField(
                    value = state.code,
                    onValueChange = onCodeChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text(stringResource(R.string.account_email_code)) },
                    placeholder = { Text(stringResource(R.string.account_email_code_hint)) },
                    shape = AccountFieldShape,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = onSendCode, enabled = state.canSendCode) {
                    if (state.isSendingCode) {
                        CircularProgressIndicator(Modifier.size(18.dp))
                    } else {
                        Text(stringResource(R.string.account_email_send_code))
                    }
                }
            }
            if (state.codeSent) {
                AccountFieldHelper(stringResource(R.string.account_email_code_sent))
            }
            Button(
                onClick = onConfirm,
                enabled = state.canConfirmChange,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isConfirming) {
                    CircularProgressIndicator(Modifier.size(18.dp))
                } else {
                    Text(stringResource(R.string.action_confirm))
                }
            }
        }
    }
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
                NodeSeekIcons.Sms,
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
    onOpenBotChat: () -> Unit,
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
                    NodeSeekIcons.Send,
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

                            binding.bound ->
                                listOfNotNull(
                                    binding.username,
                                    binding.boundAtDisplay?.let {
                                        stringResource(R.string.account_telegram_bound_at, it)
                                    },
                                ).joinToString(" · ")

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onRequestUnbind) {
                        Text(
                            stringResource(R.string.account_telegram_unbind),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(Modifier.weight(1f)) {}
                    TextButton(onClick = onOpenBotChat) {
                        Icon(
                            NodeSeekIcons.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            stringResource(R.string.account_telegram_open_bot),
                            modifier = Modifier.padding(start = Spacing.xs),
                        )
                    }
                }
            } else {
                Button(onClick = onRequestBind, enabled = binding != null) {
                    Icon(NodeSeekIcons.Link, contentDescription = null, modifier = Modifier.size(17.dp))
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
 * f3 1/2: the confirmation before leaving for Telegram.
 *
 * Built by hand rather than with [HighRiskDialog] because it carries two things that dialog cannot
 * hold: the info strip about the web fallback, and the 「刷新绑定状态」 footer that serves people who
 * already finished in Telegram before ever seeing this dialog.
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
        icon = { Icon(NodeSeekIcons.OpenInNew, contentDescription = null) },
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

/**
 * The bot conversation 「打开 Bot 会话」 reopens. `t.me/nodeseek` is the support bot the site's footer
 * links to; whether the *binding* bot is the same account is part of what the on-device probe of
 * 「立即绑定」 has to answer (see `NetworkAccountSettingsRepository.beginTelegramBinding`).
 */
internal const val TELEGRAM_BOT_CHAT_URL = "https://t.me/nodeseek"

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun ContactPreviewUnbound() {
    NodeSeekTheme {
        ContactScreen(
            state =
            ContactUiState(
                isLoading = false,
                email = "hikari.zhg@gmail.com",
                emailVerified = true,
                changeExpanded = true,
                newEmail = "ns.hikari@outlook.com",
                codeSent = true,
                telegram = TelegramBinding(bound = false),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onToggleEmailChange = {},
            onPasswordChange = {},
            onNewEmailChange = {},
            onCodeChange = {},
            onSendCode = {},
            onConfirmEmailChange = {},
            onRequestBind = {},
            onDismissBind = {},
            onConfirmBind = {},
            onRefreshBinding = {},
            onRequestUnbind = {},
            onDismissUnbind = {},
            onConfirmUnbind = {},
            onOpenBotChat = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun ContactPreviewBound() {
    NodeSeekTheme {
        ContactScreen(
            state =
            ContactUiState(
                isLoading = false,
                email = "hikari.zhg@gmail.com",
                emailVerified = true,
                telegram =
                TelegramBinding(
                    bound = true,
                    username = "@hikari_zhg",
                    boundAtDisplay = "2026/7/27",
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onToggleEmailChange = {},
            onPasswordChange = {},
            onNewEmailChange = {},
            onCodeChange = {},
            onSendCode = {},
            onConfirmEmailChange = {},
            onRequestBind = {},
            onDismissBind = {},
            onConfirmBind = {},
            onRefreshBinding = {},
            onRequestUnbind = {},
            onDismissUnbind = {},
            onConfirmUnbind = {},
            onOpenBotChat = {},
        )
    }
}
