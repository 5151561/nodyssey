package io.github.nodyssey.ui.account

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.imagehost_key_saved
import io.github.plaza.core.net.SiteError
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The snackbar every account sub-page says its news through.
 *
 * These pages used to say the right sentence and offer nothing to press, which for a wall is the
 * whole failure: 需要确认一下你不是机器人 as a flat statement leaves the reader with a setting that
 * will not save and no idea that a web view would fix it. What is asserted here is that the button
 * follows the error rather than being decided per page.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class AccountMessageSnackbarTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var verified = false
    private var signedIn = false
    private var retried = false

    private fun setMessage(
        message: AccountMessage?,
        onRetry: (() -> Unit)? = null,
    ) {
        composeRule.setContent {
            PlazaTheme {
                val hostState = remember { SnackbarHostState() }
                // A bare host rather than a Scaffold: the snackbar only needs somewhere to draw, and
                // a Scaffold here would be an inset contract nothing in this test honours.
                Box {
                    SnackbarHost(hostState)
                    AccountMessageSnackbar(
                        message = message,
                        snackbarHostState = hostState,
                        onShown = {},
                        onSignIn = { signedIn = true },
                        onVerify = { verified = true },
                        onRetry = onRetry,
                    )
                }
            }
        }
    }

    @Test
    fun `a Cloudflare wall offers the verify button`() {
        setMessage(AccountMessage.Failure(SiteError.Cloudflare))

        composeRule.onNodeWithText("需要确认一下你不是机器人").assertIsDisplayed()
        composeRule.onNodeWithText("去验证").performClick()

        assertTrue(verified)
    }

    @Test
    fun `being signed out offers signing in rather than verifying`() {
        setMessage(AccountMessage.Failure(SiteError.LoginRequired))

        composeRule.onNodeWithText("去验证").assertDoesNotExist()
        composeRule.onNodeWithText("登录").performClick()

        assertTrue(signedIn)
    }

    /** The rule is per error, not "always offer something": a retry needs a caller that can retry. */
    @Test
    fun `a network failure offers a retry only when the page supplied one`() {
        setMessage(AccountMessage.Failure(SiteError.Network), onRetry = { retried = true })

        composeRule.onNodeWithText("重试").performClick()

        assertTrue(retried)
    }

    @Test
    fun `a network failure with no retry wired shows no button at all`() {
        setMessage(AccountMessage.Failure(SiteError.Network))

        composeRule.onNodeWithText("网络开小差了").assertIsDisplayed()
        composeRule.onNodeWithText("重试").assertDoesNotExist()
    }

    /** Good news has nothing to recover from, so it carries no button and times out on its own. */
    @Test
    fun `an informational message carries no action`() {
        setMessage(AccountMessage.Info(Res.string.imagehost_key_saved))

        composeRule.onNodeWithText("去验证").assertDoesNotExist()
        composeRule.onNodeWithText("重试").assertDoesNotExist()
    }
}
