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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecureTextField
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
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.R
import io.github.plaza.designsys.component.NodysseyIcons
import io.github.plaza.designsys.theme.NodysseyTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.paddingWithKeyboard
import io.github.plaza.designsys.theme.readableWidth

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
        currentPasswordState = viewModel.currentPasswordState,
        newPasswordState = viewModel.newPasswordState,
        confirmPasswordState = viewModel.confirmPasswordState,
        twoFactorPasswordState = viewModel.twoFactorPasswordState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRequestPasswordChange = viewModel::requestPasswordChange,
        onRequestTwoFactor = viewModel::requestTwoFactorEnrolment,
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
    currentPasswordState: TextFieldState,
    newPasswordState: TextFieldState,
    confirmPasswordState: TextFieldState,
    twoFactorPasswordState: TextFieldState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRequestPasswordChange: () -> Unit,
    onRequestTwoFactor: () -> Unit,
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
                // Edge-to-edge plus Scaffold's IME-free insets would leave 确认新密码 under the
                // keyboard and unreachable.
                .paddingWithKeyboard(padding)
                .fillMaxSize()
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            AccountSectionLabel(stringResource(R.string.account_change_password))

            PasswordField(
                fieldState = currentPasswordState,
                label = stringResource(R.string.account_password_current),
                contentType = ContentType.Password,
            )

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PasswordField(
                    fieldState = newPasswordState,
                    label = stringResource(R.string.account_password_new),
                    contentType = ContentType.NewPassword,
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
                fieldState = confirmPasswordState,
                label = stringResource(R.string.account_password_confirm),
                placeholder = stringResource(R.string.account_password_confirm_hint),
                contentType = ContentType.NewPassword,
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
                passwordState = twoFactorPasswordState,
                canConfirm = state.twoFactorPassword.isNotEmpty(),
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
    passwordState: TextFieldState,
    canConfirm: Boolean,
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
                SecureTextField(
                    state = passwordState,
                    label = { Text(stringResource(R.string.account_password_current)) },
                    textObfuscationMode = TextObfuscationMode.RevealLastTyped,
                    shape = AccountFieldShape,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentType = ContentType.Password },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = canConfirm) {
                Text(stringResource(R.string.account_confirm_2fa_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

/**
 * One password box.
 *
 * `SecureTextField` rather than an `OutlinedTextField` carrying a `PasswordVisualTransformation`:
 * it brings the password keyboard, blocks the field from being copied out, and — the reason the
 * hand-rolled eye button is gone — obscures with [TextObfuscationMode.RevealLastTyped], which shows
 * the character just typed and hides it again. That is what the eye was for, without a toggle to
 * leave switched on and without three booleans in the ViewModel to remember which fields are open.
 */
@Composable
private fun PasswordField(
    fieldState: TextFieldState,
    label: String,
    contentType: ContentType,
    placeholder: String? = null,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    SecureTextField(
        state = fieldState,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        textObfuscationMode = TextObfuscationMode.RevealLastTyped,
        shape = AccountFieldShape,
        // Naming the field's content type is what lets a password manager recognise this as a change-
        // password form: `Password` for the credential it already holds, `NewPassword` for the two it
        // should offer to generate and then save. Without it the manager sees three anonymous boxes.
        modifier = Modifier.fillMaxWidth().semantics { this.contentType = contentType },
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
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
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
            currentPasswordState = rememberTextFieldState("hunter2hunter2"),
            newPasswordState = rememberTextFieldState("Correct-Horse-9"),
            confirmPasswordState = rememberTextFieldState("Correct-Horse-9"),
            twoFactorPasswordState = rememberTextFieldState(),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onRequestPasswordChange = {},
            onRequestTwoFactor = {},
            onDismissConfirmation = {},
            onConfirmPasswordChange = {},
            onConfirmTwoFactor = {},
        )
    }
}
