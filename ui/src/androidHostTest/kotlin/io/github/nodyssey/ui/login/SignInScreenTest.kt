package io.github.nodyssey.ui.login

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import io.github.nodyssey.data.session.SignInOutcome
import io.github.nodyssey.data.session.SignInRefusal
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What h1's form must not do: offer a 登录 that cannot work, and answer a refusal in our words when
 * the site sent its own.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class SignInScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(state: SignInUiState) {
        composeRule.setContent {
            PlazaTheme {
                SignInScreen(
                    state = state,
                    accountState = rememberTextFieldState(state.account),
                    passwordState = rememberTextFieldState("x".repeat(state.passwordLength)),
                    snackbarHostState = remember { SnackbarHostState() },
                    onClose = {},
                    onSubmit = {},
                    onOpenSiteSignInPage = {},
                    onUseWebSignIn = {},
                )
            }
        }
    }

    @Test
    fun `a filled form still waiting on the widget cannot be submitted`() {
        setContent(SignInUiState(account = "nssk", passwordLength = 8))

        composeRule.onNodeWithText("登录", substring = false).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `no widget at all says so rather than showing an empty box`() {
        setContent(
            SignInUiState(
                account = "nssk",
                passwordLength = 8,
                verification = VerificationState.NotWired,
            ),
        )

        composeRule.onNodeWithText("登录", substring = false).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("人机验证还没接进 App，原生登录暂时用不了").assertExists()
        // The escape, without which this screen would be a dead end.
        composeRule.onNodeWithText("改用网页登录").performScrollTo().assertExists()
    }

    @Test
    fun `a token in hand arms the button`() {
        setContent(
            SignInUiState(
                account = "nssk",
                passwordLength = 8,
                verification = VerificationState.Passed("token"),
            ),
        )

        composeRule.onNodeWithText("登录", substring = false).performScrollTo().assertIsEnabled()
    }

    @Test
    fun `the refusal banner prefers the site's sentence to ours`() {
        setContent(
            SignInUiState(
                account = "nssk",
                passwordLength = 6,
                verification = VerificationState.Expired,
                refusal = SignInOutcome.Refused(SignInRefusal.Credentials, "该账号已被锁定，请稍后再试"),
            ),
        )

        composeRule.onNodeWithText("该账号已被锁定，请稍后再试").assertExists()
        composeRule.onNodeWithText("密码错误，请重新输入").assertExists()
        // The board's placeholder lockout numbers are in no resource, so nothing can print them.
        composeRule.onNodeWithText("用户名或密码不正确。").assertDoesNotExist()
        composeRule.onNodeWithText("验证已过期，请重新验证").assertExists()
    }
}
