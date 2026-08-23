package io.github.nodyssey.ui.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.session.SignInOutcome
import io.github.nodyssey.data.session.SignInRefusal
import io.github.nodyssey.data.session.TwoFactorChallenge
import io.github.nodyssey.ui.common.SiteErrorSnackbar
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_close
import io.github.nodyssey.ui.resources.sign_in_account
import io.github.nodyssey.ui.resources.sign_in_forgot
import io.github.nodyssey.ui.resources.sign_in_mark
import io.github.nodyssey.ui.resources.sign_in_password
import io.github.nodyssey.ui.resources.sign_in_password_hint
import io.github.nodyssey.ui.resources.sign_in_password_rejected
import io.github.nodyssey.ui.resources.sign_in_readonly_hint
import io.github.nodyssey.ui.resources.sign_in_refused_generic
import io.github.nodyssey.ui.resources.sign_in_register_hint
import io.github.nodyssey.ui.resources.sign_in_submit
import io.github.nodyssey.ui.resources.sign_in_submitting
import io.github.nodyssey.ui.resources.sign_in_subtitle
import io.github.nodyssey.ui.resources.sign_in_terms
import io.github.nodyssey.ui.resources.sign_in_title
import io.github.nodyssey.ui.resources.sign_in_use_web
import io.github.nodyssey.ui.resources.sign_in_verify_brand
import io.github.nodyssey.ui.resources.sign_in_verify_expired
import io.github.nodyssey.ui.resources.sign_in_verify_not_required
import io.github.nodyssey.ui.resources.sign_in_verify_not_wired
import io.github.plaza.core.net.UserAgent
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.paddingWithKeyboard
import io.github.plaza.designsys.theme.readableWidth
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * 登录 · 原生表单 (h1).
 *
 * The route owns the two things a state holder cannot: leaving once the session lands, and the
 * snackbar that says a *request* failed. Everything else is [SignInUiState].
 *
 * @param onUseWebSignIn opens the site's own sign-in page in the restricted web view — h1's
 *   改用网页登录, and today also the way out for a user this screen cannot serve.
 * @param onOpenSiteSignInPage where 忘记密码 and 还没有账号 lead. Both go to the site's sign-in page
 *   rather than to a reset or a registration URL of their own: those paths have not been verified
 *   from the site, and a guessed URL is a dead end wearing a working button.
 */
@Composable
fun SignInRoute(
    viewModel: SignInViewModel,
    userAgent: UserAgent,
    onClose: () -> Unit,
    onSignedIn: () -> Unit,
    onUseWebSignIn: () -> Unit,
    onOpenSiteSignInPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    SiteErrorSnackbar(
        error = state.failure,
        snackbarHostState = snackbarHostState,
        onShown = viewModel::consumeFailure,
        detail = state.failureDetail,
        // No 去登录 recovery: this *is* the sign-in screen. A challenge is the one failure the web
        // view can clear, and it lands on the same page 改用网页登录 would have opened anyway.
        onVerify = onUseWebSignIn,
        onRetry = viewModel::retry,
    )

    LaunchedEffect(state.signedIn) {
        if (state.signedIn) onSignedIn()
    }

    /*
     * Card 3's countdown, repeated here rather than in the ViewModel.
     *
     * `produceState` ends with the composition, which is what a countdown should do — and keeps the
     * `while (true)` out of `viewModelScope`, where it would be a state holder that never reaches
     * idle and would hang every test that got as far as this step. See
     * [SignInViewModel.secondsUntilNextCode]; keyed on the step so it does not run on card 1.
     */
    val secondsUntilNextCode by produceState(SignInViewModel.TOTP_PERIOD_SECONDS, state.step) {
        if (state.step != SignInStep.TwoFactor) return@produceState
        while (true) {
            value = viewModel.secondsUntilNextCode()
            delay(SignInViewModel.TICK_MILLIS)
        }
    }

    when (state.step) {
        SignInStep.Credentials ->
            SignInScreen(
                state = state,
                accountState = viewModel.accountState,
                passwordState = viewModel.passwordState,
                snackbarHostState = snackbarHostState,
                onClose = onClose,
                onSubmit = viewModel::submitCredentials,
                onOpenSiteSignInPage = onOpenSiteSignInPage,
                onUseWebSignIn = onUseWebSignIn,
                modifier = modifier,
                turnstile = {
                    TurnstileWidget(
                        sitekey = NodeSeekSite.TURNSTILE_SITEKEY,
                        // From the app's own scheme rather than the system setting: the user may have
                        // put Nodyssey in a theme the OS is not in, and a light widget on a dark form
                        // is a white brick.
                        darkTheme = MaterialTheme.colorScheme.surface.luminance() < LIGHT_SURFACE_LUMINANCE,
                        userAgent = userAgent,
                        resetSignal = state.verificationGeneration,
                        onToken = viewModel::onVerified,
                        onExpired = viewModel::onVerificationExpired,
                        onUnavailable = viewModel::onVerificationUnavailable,
                        modifier = Modifier.fillMaxWidth().height(TURNSTILE_HEIGHT_DP.dp),
                    )
                },
            )

        SignInStep.TwoFactor ->
            TwoFactorScreen(
                state = state,
                secondsUntilNextCode = secondsUntilNextCode,
                codeState = viewModel.codeState,
                snackbarHostState = snackbarHostState,
                onBack = viewModel::leaveTwoFactor,
                onSubmit = viewModel::submitTwoFactor,
                onUseWebSignIn = onUseWebSignIn,
                modifier = modifier,
            )
    }
}

/**
 * Cards 1 and 2 of h1 — the form at rest, and the form refused or in flight.
 *
 * One composable for both because they differ only in what [SignInUiState] says. The rule card 2
 * exists to state is that a submit costs the user nothing: the fields keep their text through a
 * refusal and through a failure, and the only thing that changes is what is drawn around them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(
    state: SignInUiState,
    accountState: TextFieldState,
    passwordState: TextFieldState,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
    onSubmit: () -> Unit,
    onOpenSiteSignInPage: () -> Unit,
    /**
     * 改用网页登录, drawn only when the verification block has nothing to offer.
     *
     * Card 3 carries this on the board and card 1 does not, because on the board card 1 always has a
     * working checkbox. It does not always: a widget that fails to start leaves 登录 disabled with
     * no way forward, and a sign-in screen the user cannot sign in from — when the web page next
     * door still works — is the worst thing this screen could do.
     */
    onUseWebSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * h1's 人机验证 widget.
     *
     * A slot rather than a call, so this screen composes with no web view under it — which is what
     * lets the previews draw and `SignInScreenTest` run. The empty default is also the honest one:
     * a host that cannot supply a widget has already told the state holder so, and the block draws
     * its 未接入 line instead of an empty box.
     */
    turnstile: @Composable () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_close))
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
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SignInHeader()

            if (state.hasCredentialRefusal) RefusalBanner(state.refusal)

            TextField(
                state = accountState,
                enabled = state.isFormEnabled,
                label = { Text(stringResource(Res.string.sign_in_account)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username },
            )

            SecureTextField(
                state = passwordState,
                enabled = state.isFormEnabled,
                label = { Text(stringResource(Res.string.sign_in_password)) },
                isError = state.hasCredentialRefusal,
                // `RevealLastTyped` rather than the board's eye toggle, which is the same decision
                // 安全 (d6 2/4) made and for the same reason: it shows the character just typed and
                // hides it again, with no switch left on and nothing in the state to remember.
                textObfuscationMode = TextObfuscationMode.RevealLastTyped,
                supportingText = { PasswordSupport(state, onOpenSiteSignInPage) },
                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
            )

            VerificationBlock(state.verification, turnstile)

            if (state.verification is VerificationState.NotWired) {
                TextButton(onClick = onUseWebSignIn, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.sign_in_use_web), fontWeight = FontWeight.SemiBold)
                }
            }

            Button(
                onClick = onSubmit,
                enabled = state.canSubmitCredentials,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(stringResource(Res.string.sign_in_submitting))
                } else {
                    Text(stringResource(Res.string.sign_in_submit), fontWeight = FontWeight.Bold)
                }
            }

            // The board puts this line under card 2's button, where it explains a control that is
            // deliberately dead. Shown whenever the button is down, which is the state that ships.
            if (!state.canSubmitCredentials) {
                Text(
                    stringResource(Res.string.sign_in_readonly_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm),
                )
            }

            Spacer(Modifier.height(Spacing.xs))

            TextButton(onClick = onOpenSiteSignInPage, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.sign_in_register_hint), fontWeight = FontWeight.SemiBold)
            }

            Text(
                stringResource(Res.string.sign_in_terms),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xl),
            )
        }
    }
}

/** The NS tile, the headline and the promise underneath it — the top of card 1. */
@Composable
private fun SignInHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    stringResource(Res.string.sign_in_mark),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
        Text(
            stringResource(Res.string.sign_in_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            stringResource(Res.string.sign_in_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 区分大小写 on the left, 忘记密码 on the right — or the refusal, which replaces the left half. */
@Composable
private fun PasswordSupport(state: SignInUiState, onForgotPassword: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text =
            if (state.hasCredentialRefusal) {
                stringResource(Res.string.sign_in_password_rejected)
            } else {
                stringResource(Res.string.sign_in_password_hint)
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(Res.string.sign_in_forgot),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onForgotPassword),
        )
    }
}

/**
 * The site's own sentence for a refusal, or a plain one when it sent none.
 *
 * The app writes as little of this as it can get away with. The endpoint answers
 * `{"success":false,"message":…}` and that message is what the forum wants said; the fallback exists
 * only for the case where there is no message at all, and deliberately stops short of the board's
 * placeholder lockout numbers, which nothing has confirmed.
 */
@Composable
private fun RefusalBanner(refusal: SignInOutcome.Refused?) {
    val text = refusal?.detail?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.sign_in_refused_generic)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
        ) {
            Icon(PlazaIcons.ErrorCircle, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * h1's 人机验证 strip, which is the widget itself plus whatever has to be said around it.
 *
 * Cloudflare draws its own checkbox, its own wording and its own branding, so the board's strip *is*
 * the widget once there is one — the surface below is the frame the board puts around it, not a
 * second copy of its contents. Only [VerificationState.NotWired] replaces it, because in that state
 * there is nothing to frame.
 *
 * The widget stays mounted once it is up, [VerificationState.Passed] included. Unmounting it would
 * destroy the web view and its script, and the very next thing that happens after a refusal is that
 * the screen needs a fresh token from it.
 */
@Composable
private fun VerificationBlock(verification: VerificationState, turnstile: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.padding(Spacing.sm),
        ) {
            if (verification is VerificationState.NotWired) {
                VerificationNotice(Icons.Default.Info, stringResource(Res.string.sign_in_verify_not_wired))
            } else {
                turnstile()
                when (verification) {
                    VerificationState.Expired ->
                        VerificationNotice(
                            Icons.Default.Refresh,
                            stringResource(Res.string.sign_in_verify_expired),
                        )

                    VerificationState.NotRequired ->
                        VerificationNotice(
                            Icons.Default.CheckCircle,
                            stringResource(Res.string.sign_in_verify_not_required),
                        )

                    // 已通过 and 等待勾选 are both already drawn by the widget itself, in its own
                    // words. A line of ours underneath would be the app narrating what the user can
                    // see.
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun VerificationNotice(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xs),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(
            stringResource(Res.string.sign_in_verify_brand),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
        )
    }
}

/** Below this, a colour scheme is dark enough that Turnstile should draw its dark widget. */
private const val LIGHT_SURFACE_LUMINANCE = 0.5f

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun SignInFormPreview() {
    PlazaTheme {
        SignInScreen(
            state = SignInUiState(account = "nssk", passwordLength = 8, verification = VerificationState.NotWired),
            accountState = rememberTextFieldState("nssk"),
            passwordState = rememberTextFieldState("hunter22"),
            snackbarHostState = remember { SnackbarHostState() },
            onClose = {},
            onSubmit = {},
            onOpenSiteSignInPage = {},
            onUseWebSignIn = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun SignInRefusedPreview() {
    PlazaTheme {
        SignInScreen(
            state =
            SignInUiState(
                account = "nssk",
                passwordLength = 6,
                isSubmitting = true,
                verification = VerificationState.Expired,
                refusal = SignInOutcome.Refused(SignInRefusal.Credentials, "用户名或密码不正确"),
            ),
            accountState = rememberTextFieldState("nssk"),
            passwordState = rememberTextFieldState("hunter"),
            snackbarHostState = remember { SnackbarHostState() },
            onClose = {},
            onSubmit = {},
            onOpenSiteSignInPage = {},
            onUseWebSignIn = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun TwoFactorPreview() {
    PlazaTheme {
        TwoFactorScreen(
            state =
            SignInUiState(
                step = SignInStep.TwoFactor,
                challenge = TwoFactorChallenge(account = "nssk", otpSession = "preview"),
                code = "4917",
            ),
            secondsUntilNextCode = 18,
            codeState = rememberTextFieldState("4917"),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onSubmit = {},
            onUseWebSignIn = {},
        )
    }
}
