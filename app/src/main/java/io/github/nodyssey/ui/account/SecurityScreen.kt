package io.github.nodyssey.ui.account

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.R
import io.github.nodyssey.ui.common.NodysseyIcons
import io.github.nodyssey.ui.theme.NodysseyTheme
import io.github.nodyssey.ui.theme.Spacing
import io.github.nodyssey.ui.theme.readableWidth

@Composable
fun SecurityRoute(
    viewModel: SecurityViewModel,
    onBack: () -> Unit,
    onOpenEnrolmentUri: (String) -> Boolean,
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

    // Enrolment hands off to whichever authenticator app claims `otpauth://`; the app never stores the
    // secret, which is the entire reason to pass the URI straight through rather than render the code.
    LaunchedEffect(state.enrolmentUri) {
        val uri = state.enrolmentUri ?: return@LaunchedEffect
        val opened = onOpenEnrolmentUri(uri)
        viewModel.consumeEnrolmentUri()
        if (!opened) viewModel.reportMissingAuthenticatorApp()
    }

    SecurityScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onCurrentPasswordChange = viewModel::updateCurrentPassword,
        onNewPasswordChange = viewModel::updateNewPassword,
        onRepeatPasswordChange = viewModel::updateConfirmPassword,
        onToggleCurrentVisible = viewModel::toggleCurrentVisible,
        onToggleNewVisible = viewModel::toggleNewVisible,
        onToggleConfirmVisible = viewModel::toggleConfirmVisible,
        onRequestPasswordChange = viewModel::requestPasswordChange,
        onRequestTwoFactor = viewModel::requestTwoFactorEnrolment,
        onTwoFactorPasswordChange = viewModel::updateTwoFactorPassword,
        onDismissConfirmation = viewModel::dismissConfirmation,
        onConfirmPasswordChange = viewModel::confirmPasswordChange,
        onConfirmTwoFactor = viewModel::confirmTwoFactorEnrolment,
        modifier = modifier,
    )
}

/**
 * 安全 (d6 2/4) — 修改密码 and 两步验证.
 *
 * Both actions confirm twice by design. Changing a password signs every other device out and binding
 * a second factor can lock the account if the authenticator is lost, and neither is undoable from
 * inside this app; a dialog that names the consequence is cheap next to that.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    state: SecurityUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onRepeatPasswordChange: (String) -> Unit,
    onToggleCurrentVisible: () -> Unit,
    onToggleNewVisible: () -> Unit,
    onToggleConfirmVisible: () -> Unit,
    onRequestPasswordChange: () -> Unit,
    onRequestTwoFactor: () -> Unit,
    onTwoFactorPasswordChange: (String) -> Unit,
    onDismissConfirmation: () -> Unit,
    onConfirmPasswordChange: () -> Unit,
    onConfirmTwoFactor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_security_title)) },
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
                // See ProfileFieldsScreen: edge-to-edge plus Scaffold's IME-free insets would leave
                // 确认新密码 under the keyboard and unreachable.
                .imePadding()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            AccountSectionLabel(stringResource(R.string.account_change_password))

            PasswordField(
                value = state.currentPassword,
                onValueChange = onCurrentPasswordChange,
                label = stringResource(R.string.account_password_current),
                visible = state.currentVisible,
                onToggleVisible = onToggleCurrentVisible,
            )

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PasswordField(
                    value = state.newPassword,
                    onValueChange = onNewPasswordChange,
                    label = stringResource(R.string.account_password_new),
                    visible = state.newVisible,
                    onToggleVisible = onToggleNewVisible,
                    isError = state.isTooShort,
                    supportingText =
                    if (state.isTooShort) {
                        stringResource(R.string.account_password_too_short, MIN_PASSWORD_LENGTH)
                    } else {
                        null
                    },
                )
                state.strength?.let { StrengthMeter(it) }
            }

            PasswordField(
                value = state.confirmPassword,
                onValueChange = onRepeatPasswordChange,
                label = stringResource(R.string.account_password_confirm),
                placeholder = stringResource(R.string.account_password_confirm_hint),
                visible = state.confirmVisible,
                onToggleVisible = onToggleConfirmVisible,
                isError = state.isMismatched,
                supportingText =
                if (state.isMismatched) stringResource(R.string.account_password_mismatch) else null,
            )

            Button(
                onClick = onRequestPasswordChange,
                enabled = state.canSubmitPassword,
                shape = RoundedCornerShape(23.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp),
            ) {
                Text(stringResource(R.string.account_password_update))
            }

            AccountSectionLabel(
                text = stringResource(R.string.account_two_factor_section),
                modifier = Modifier.padding(top = Spacing.sm),
            )
            TwoFactorCard(
                enabled = state.twoFactorEnabled,
                busy = state.isSubmitting,
                onBind = onRequestTwoFactor,
            )
        }
    }

    when (state.confirming) {
        SecurityConfirmation.Password ->
            HighRiskDialog(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.account_confirm_password_title),
                body = stringResource(R.string.account_confirm_password_body),
                confirmLabel = stringResource(R.string.account_confirm_password_action),
                onConfirm = onConfirmPasswordChange,
                onDismiss = onDismissConfirmation,
            )

        SecurityConfirmation.TwoFactor ->
            TwoFactorDialog(
                password = state.twoFactorPassword,
                onPasswordChange = onTwoFactorPasswordChange,
                onConfirm = onConfirmTwoFactor,
                onDismiss = onDismissConfirmation,
            )

        null -> Unit
    }
}

/**
 * d6 2/4's 绑定验证器 confirmation, with the password the site's enrolment endpoint requires.
 *
 * [HighRiskDialog] cannot hold an input, and the password belongs here rather than on the form
 * above: that form's 当前密码 is part of a *password change*, and borrowing it would make starting
 * 2FA look like the first step of changing the password.
 *
 * Not covered by `SecurityScreenTest`: a text field inside an `AlertDialog` never reaches idle under
 * Robolectric, so `composeRule.setContent` times out before a single assertion runs. The rule that
 * matters — no enrolment request without a password — is pinned in `SecurityViewModelTest` instead.
 */
@Composable
private fun TwoFactorDialog(
    password: String,
    onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(NodysseyIcons.Shield, contentDescription = null) },
        title = { Text(stringResource(R.string.account_confirm_2fa_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    stringResource(R.string.account_confirm_2fa_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text(stringResource(R.string.account_password_current)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    shape = AccountFieldShape,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = password.isNotEmpty()) {
                Text(stringResource(R.string.account_confirm_2fa_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    placeholder: String? = null,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        // `PasswordVisualTransformation` only masks what is drawn. Without the password keyboard type
        // a third-party IME — which on this forum's audience is essentially everyone — keeps
        // autocorrecting and learning, so a new password can end up in the IME's personal dictionary
        // and be suggested elsewhere. That would undo the point of this screen.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation =
        if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        shape = AccountFieldShape,
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(
                    imageVector = if (visible) NodysseyIcons.VisibilityOff else NodysseyIcons.Visibility,
                    contentDescription =
                    stringResource(
                        if (visible) R.string.account_password_hide else R.string.account_password_show,
                    ),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Four bars and one line of advice — the meter is guidance, never a gate. See [passwordStrength]. */
@Composable
private fun StrengthMeter(strength: PasswordStrength) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.padding(horizontal = Spacing.xs),
        ) {
            repeat(PasswordStrength.TOTAL_BARS) { index ->
                val color by animateColorAsState(
                    targetValue =
                    if (index < strength.filledBars) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    label = "strength_bar_$index",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color),
                )
            }
        }
        Text(
            text =
            stringResource(R.string.account_password_strength, stringResource(strength.labelRes)) +
                " · " + stringResource(strength.hintRes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.xs),
        )
    }
}

@Composable
private fun TwoFactorCard(
    enabled: Boolean?,
    busy: Boolean,
    onBind: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Icon(
                    NodysseyIcons.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.account_two_factor_totp),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text =
                        stringResource(
                            when (enabled) {
                                true -> R.string.account_two_factor_on_hint
                                false -> R.string.account_two_factor_off_hint
                                null -> R.string.account_value_unknown
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                stringResource(R.string.account_two_factor_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onBind,
                enabled = !busy,
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(NodysseyIcons.QrCode, contentDescription = null, modifier = Modifier.size(17.dp))
                Text(
                    text =
                    stringResource(
                        if (enabled == true) {
                            R.string.account_two_factor_rebind
                        } else {
                            R.string.account_two_factor_bind
                        },
                    ),
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun SecurityPreview() {
    NodysseyTheme {
        SecurityScreen(
            state =
            SecurityUiState(
                isLoading = false,
                twoFactorEnabled = false,
                currentPassword = "hunter2hunter2",
                newPassword = "Correct-Horse-9",
                confirmPassword = "Correct-Horse-9",
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onCurrentPasswordChange = {},
            onNewPasswordChange = {},
            onRepeatPasswordChange = {},
            onToggleCurrentVisible = {},
            onToggleNewVisible = {},
            onToggleConfirmVisible = {},
            onRequestPasswordChange = {},
            onRequestTwoFactor = {},
            onTwoFactorPasswordChange = {},
            onDismissConfirmation = {},
            onConfirmPasswordChange = {},
            onConfirmTwoFactor = {},
        )
    }
}
