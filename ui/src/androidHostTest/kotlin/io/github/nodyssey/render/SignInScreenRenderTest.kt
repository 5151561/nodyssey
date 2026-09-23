package io.github.nodyssey.render

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.nodyssey.core.ActiveSite
import io.github.nodyssey.core.Site
import io.github.nodyssey.data.session.SignInOutcome
import io.github.nodyssey.data.session.SignInRefusal
import io.github.nodyssey.data.session.TwoFactorChallenge
import io.github.nodyssey.ui.login.SignInScreen
import io.github.nodyssey.ui.login.SignInStep
import io.github.nodyssey.ui.login.SignInUiState
import io.github.nodyssey.ui.login.TwoFactorScreen
import io.github.nodyssey.ui.login.VerificationState
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Boards 8h (the form, refused), 10c (a site with 一键登录) and 9c (两步验证), light and dark.
 *
 * The Turnstile widget is a web view and draws nothing on the JVM; the slot is left empty, which is
 * the gap it would fill.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class SignInScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @After
    fun restoreSite() = ActiveSite.install(Site.DEFAULT)

    @Composable
    private fun Form(
        darkTheme: Boolean,
        state: SignInUiState,
    ) {
        PlazaTheme(darkTheme = darkTheme) {
            SignInScreen(
                state = state,
                accountState = rememberTextFieldState(state.account),
                passwordState = rememberTextFieldState("x".repeat(state.passwordLength)),
                snackbarHostState = remember { SnackbarHostState() },
                onClose = {},
                onSubmit = {},
                onOpenSiteSignInPage = {},
                onUseWebSignIn = {},
                onOneTapSignIn = {},
            )
        }
    }

    @Composable
    private fun TwoFactor(darkTheme: Boolean) {
        PlazaTheme(darkTheme = darkTheme) {
            TwoFactorScreen(
                state =
                SignInUiState(
                    step = SignInStep.TwoFactor,
                    challenge = TwoFactorChallenge(account = "homelab_er", otpSession = "render"),
                    code = "481",
                ),
                secondsUntilNextCode = 18,
                codeState = rememberTextFieldState("481"),
                snackbarHostState = remember { SnackbarHostState() },
                onBack = {},
                onSubmit = {},
                onUseWebSignIn = {},
            )
        }
    }

    @Test
    fun `sign-in refused in light`() {
        composeRule.setContent { Form(darkTheme = false, state = REFUSED) }

        composeRule.onRoot().captureRender("sign-in-light")
    }

    @Test
    fun `sign-in refused in dark`() {
        composeRule.setContent { Form(darkTheme = true, state = REFUSED) }

        composeRule.onRoot().captureRender("sign-in-dark")
    }

    @Test
    fun `one-tap sign-in in light`() {
        ActiveSite.install(Site.DEEPFLOOD)
        composeRule.setContent { Form(darkTheme = false, state = SignInUiState()) }

        composeRule.onRoot().captureRender("sign-in-one-tap-light")
    }

    @Test
    fun `two-factor in light`() {
        composeRule.setContent { TwoFactor(darkTheme = false) }

        composeRule.onRoot().captureRender("two-factor-light")
    }

    @Test
    fun `two-factor in dark`() {
        composeRule.setContent { TwoFactor(darkTheme = true) }

        composeRule.onRoot().captureRender("two-factor-dark")
    }

    private companion object {
        val REFUSED =
            SignInUiState(
                account = "homelab_er",
                passwordLength = 10,
                verification = VerificationState.Passed("token"),
                refusal = SignInOutcome.Refused(SignInRefusal.Credentials, "用户名或密码不对，再检查一下"),
            )
    }
}
