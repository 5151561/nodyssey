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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nsreader.R
import io.github.nsreader.data.account.AccountContact
import io.github.nsreader.data.account.BlockedUser
import io.github.nsreader.ui.common.NodeSeekIcons
import io.github.nsreader.ui.common.UserAvatar
import io.github.nsreader.ui.theme.LocalNodeSeekExtraColors
import io.github.nsreader.ui.theme.NodeSeekTheme
import io.github.nsreader.ui.theme.Sizes
import io.github.nsreader.ui.theme.Spacing
import io.github.nsreader.ui.theme.readableWidth

@Composable
fun ContactBlockRoute(
    viewModel: ContactBlockViewModel,
    onBack: () -> Unit,
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

    ContactBlockScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onEmailChange = viewModel::updateEmail,
        onBackupEmailChange = viewModel::updateBackupEmail,
        onResend = viewModel::resendVerification,
        onSave = viewModel::save,
        onRequestUnblock = viewModel::requestUnblock,
        onDismissUnblock = viewModel::dismissUnblock,
        onConfirmUnblock = viewModel::confirmUnblock,
        modifier = modifier,
    )
}

/**
 * 联系方式与屏蔽 (d6 3/4).
 *
 * The verified badge is the load-bearing part: an email address that looks saved but is not verified
 * cannot recover the account, and the site's own page shows nothing about it. Changing an address
 * clears the badge immediately rather than after a refresh, so the state on screen is never a claim
 * the server has not agreed to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactBlockScreen(
    state: ContactBlockUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onEmailChange: (String) -> Unit,
    onBackupEmailChange: (String) -> Unit,
    onResend: (String) -> Unit,
    onSave: () -> Unit,
    onRequestUnblock: (BlockedUser) -> Unit,
    onDismissUnblock: () -> Unit,
    onConfirmUnblock: () -> Unit,
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
                // See ProfileFieldsScreen: without this the keyboard hides the backup-address field.
                .imePadding()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (state.endpointPending) EndpointPendingBanner()

            AccountSectionLabel(stringResource(R.string.account_contact_section))

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                EmailField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = stringResource(R.string.account_email),
                    verified = state.emailVerified,
                    isError = state.isEmailMalformed,
                )
                AccountFieldHelper(stringResource(R.string.account_email_helper))
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                EmailField(
                    value = state.backupEmail,
                    onValueChange = onBackupEmailChange,
                    label = stringResource(R.string.account_email_backup),
                    verified = state.backupEmailVerified,
                    isError = state.isBackupMalformed,
                )
                if (state.backupEmail.isNotEmpty() && !state.backupEmailVerified) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AccountFieldHelper(
                            text = stringResource(R.string.account_email_verification_sent),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onResend(state.backupEmail) }) {
                            Text(stringResource(R.string.account_email_resend))
                        }
                    }
                }
            }

            Button(
                onClick = onSave,
                enabled = state.canSave,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.account_contact_save))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountSectionLabel(
                    text = stringResource(R.string.account_block_section),
                    modifier = Modifier.weight(1f),
                )
                if (state.blocked.isNotEmpty()) {
                    Text(
                        stringResource(R.string.account_blocked_count, state.blocked.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.blocked.isEmpty()) {
                BlockedEmptyState()
            } else {
                Column {
                    state.blocked.forEachIndexed { index, user ->
                        BlockedRow(user = user, onUnblock = { onRequestUnblock(user) })
                        if (index != state.blocked.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }

    state.unblocking?.let { target ->
        HighRiskDialog(
            icon = NodeSeekIcons.Block,
            title = stringResource(R.string.account_confirm_unblock_title, target.name),
            body = stringResource(R.string.account_confirm_unblock_body),
            confirmLabel = stringResource(R.string.account_confirm_unblock_action),
            onConfirm = onConfirmUnblock,
            onDismiss = onDismissUnblock,
        )
    }
}

@Composable
private fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    verified: Boolean,
    isError: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        supportingText =
        if (isError) {
            { Text(stringResource(R.string.account_email_invalid)) }
        } else {
            null
        },
        shape = AccountFieldShape,
        trailingIcon = { if (value.isNotEmpty()) VerificationBadge(verified) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Verified reads as primary, unverified as the warning family — never as an error, since it is not one. */
@Composable
private fun VerificationBadge(verified: Boolean) {
    val extra = LocalNodeSeekExtraColors.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (verified) MaterialTheme.colorScheme.primaryContainer else extra.warningContainer,
        contentColor =
        if (verified) MaterialTheme.colorScheme.onPrimaryContainer else extra.onWarningContainer,
        modifier = Modifier.padding(end = Spacing.sm),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(
                imageVector = if (verified) Icons.Default.Check else NodeSeekIcons.Schedule,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(
                stringResource(
                    if (verified) R.string.account_email_verified else R.string.account_email_unverified,
                ),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun BlockedRow(
    user: BlockedUser,
    onUnblock: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        UserAvatar(url = user.avatarUrl, name = user.name, size = Sizes.avatarList)
        Column(Modifier.weight(1f)) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.account_block_row_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onUnblock) {
            Text(stringResource(R.string.account_block_unblock))
        }
    }
}

@Composable
private fun BlockedEmptyState() {
    Surface(
        shape = AccountFieldShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                NodeSeekIcons.VisibilityOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                stringResource(R.string.account_block_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun ContactBlockPreview() {
    NodeSeekTheme {
        ContactBlockScreen(
            state =
            ContactBlockUiState(
                isLoading = false,
                email = "hikari.zhg@gmail.com",
                backupEmail = "ns.backup@outlook.com",
                saved =
                AccountContact(
                    email = "hikari.zhg@gmail.com",
                    emailVerified = true,
                    backupEmail = "ns.backup@outlook.com",
                    backupEmailVerified = false,
                ),
                blocked =
                listOf(
                    BlockedUser(uid = 1, name = "机场信仰充值中"),
                    BlockedUser(uid = 2, name = "vps_matthew"),
                    BlockedUser(uid = 3, name = "白嫖失败选手"),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onEmailChange = {},
            onBackupEmailChange = {},
            onResend = {},
            onSave = {},
            onRequestUnblock = {},
            onDismissUnblock = {},
            onConfirmUnblock = {},
        )
    }
}
