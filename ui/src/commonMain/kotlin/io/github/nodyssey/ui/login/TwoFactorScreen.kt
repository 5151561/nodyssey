package io.github.nodyssey.ui.login

import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.sign_in_2fa_body
import io.github.nodyssey.ui.resources.sign_in_2fa_countdown
import io.github.nodyssey.ui.resources.sign_in_2fa_headline
import io.github.nodyssey.ui.resources.sign_in_2fa_note
import io.github.nodyssey.ui.resources.sign_in_2fa_rejected
import io.github.nodyssey.ui.resources.sign_in_2fa_submit
import io.github.nodyssey.ui.resources.sign_in_2fa_title
import io.github.nodyssey.ui.resources.sign_in_submitting
import io.github.nodyssey.ui.resources.sign_in_use_web
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.digitsOnly
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.paddingWithKeyboard
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource

/**
 * Card 3 of h1 — 两步验证.
 *
 * Its own page rather than a dialog over the form, which is the board's own shape and matches the
 * endpoint: by the time this appears the password has already been accepted, and what is left is a
 * different question authenticated by a different thing (the `otpSession`, not the credentials).
 * Leaving by the back arrow returns to card 1 with both fields still filled in.
 *
 * The board's 使用恢复码 button is not here. The site's own answer to a lost authenticator is
 * 邮箱登录 — a separate sign-in method with its own flow — not a recovery code, and there is no
 * recovery-code field on the endpoint to post one to. 改用网页登录 at the foot of the page is the
 * honest version of that escape.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwoFactorScreen(
    state: SignInUiState,
    /** Seconds left on the current TOTP window; the caller repeats it — see `SignInRoute`. */
    secondsUntilNextCode: Int,
    codeState: TextFieldState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    onUseWebSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.sign_in_2fa_title)) },
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
    ) { padding ->
        Column(
            modifier =
            Modifier
                .paddingWithKeyboard(padding)
                .fillMaxSize()
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(PlazaIcons.Shield, contentDescription = null, modifier = Modifier.size(28.dp))
                    }
                }
                Text(
                    stringResource(Res.string.sign_in_2fa_headline),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    stringResource(Res.string.sign_in_2fa_body, state.challenge?.account.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            CodeBoxes(
                codeState = codeState,
                code = state.code,
                enabled = state.isFormEnabled,
                isError = state.hasCodeRefusal,
                onSubmit = onSubmit,
            )

            if (state.hasCodeRefusal) {
                Text(
                    state.refusal?.detail?.takeIf { it.isNotBlank() }
                        ?: stringResource(Res.string.sign_in_2fa_rejected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    PlazaIcons.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(Res.string.sign_in_2fa_countdown, secondsUntilNextCode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = onSubmit,
                enabled = state.canSubmitCode,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        stringResource(Res.string.sign_in_submitting),
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                } else {
                    Text(stringResource(Res.string.sign_in_2fa_submit), fontWeight = FontWeight.Bold)
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.padding(Spacing.md),
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(stringResource(Res.string.sign_in_2fa_note), style = MaterialTheme.typography.bodySmall)
                }
            }

            TextButton(
                onClick = onUseWebSignIn,
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xl),
            ) {
                Text(stringResource(Res.string.sign_in_use_web), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * The six boxes, over one field.
 *
 * Hand-rolled because Material 3 1.5.0-alpha24 ships no verification-code component — checked
 * against the resolved `material3.aar`, which has `TextField`, `SecureTextField` and nothing of this
 * shape. What is *not* hand-rolled is the editing: a single [BasicTextField] holds the text, owns
 * the IME and the caret, and the boxes are its `decorator`. Six real fields with focus hopping
 * between them is the version that breaks on paste, on backspace and on autofill.
 *
 * [digitsOnly] rejects inside the buffer rather than filtering afterwards — see its own note on what
 * filtering in `onValueChange` does to the caret.
 */
@Composable
private fun CodeBoxes(
    codeState: TextFieldState,
    code: String,
    enabled: Boolean,
    isError: Boolean,
    onSubmit: () -> Unit,
) {
    val focused = code.length.coerceAtMost(TWO_FACTOR_CODE_LENGTH - 1)
    BasicTextField(
        state = codeState,
        enabled = enabled,
        inputTransformation = digitsOnly(TWO_FACTOR_CODE_LENGTH),
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions =
        KeyboardOptions(
            // NumberPassword rather than Number: it brings the digit pad without the suggestion strip
            // offering to complete a one-time code from somewhere else.
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
        ),
        onKeyboardAction = { onSubmit() },
        // The real text is never drawn — the boxes below render it. Both brushes go transparent
        // rather than the field being moved off-screen, which is what keeps the caret, the IME anchor
        // and the tap target on the boxes the user is looking at.
        textStyle = LocalTextStyle.current.copy(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
        decorator = { inner ->
            Box {
                inner()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    repeat(TWO_FACTOR_CODE_LENGTH) { index ->
                        CodeBox(
                            digit = code.getOrNull(index),
                            isActive = enabled && index == focused && code.length < TWO_FACTOR_CODE_LENGTH,
                            isError = isError,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun CodeBox(
    digit: Char?,
    isActive: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val border =
        when {
            isError -> MaterialTheme.colorScheme.error
            isActive -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        }
    Surface(
        modifier = modifier.height(60.dp).border(if (isActive) 2.dp else 1.dp, border, shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = digit?.toString().orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}
