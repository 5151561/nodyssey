package io.github.nodyssey.ui.account

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.nodyssey.ui.theme.NodysseyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The claim 安全 makes is that nothing high-risk happens on one tap. These tests hold it to that.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class SecurityScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val ready =
        SecurityUiState(
            isLoading = false,
            twoFactorEnabled = false,
            currentPassword = "old-password",
            newPassword = "Correct-Horse-9",
            confirmPassword = "Correct-Horse-9",
        )

    private fun setContent(
        state: SecurityUiState,
        onRequestPasswordChange: () -> Unit = {},
        onConfirmPasswordChange: () -> Unit = {},
        onDismissConfirmation: () -> Unit = {},
    ) {
        composeRule.setContent {
            NodysseyTheme {
                SecurityScreen(
                    state = state,
                    currentPasswordState = rememberTextFieldState(state.currentPassword),
                    newPasswordState = rememberTextFieldState(state.newPassword),
                    confirmPasswordState = rememberTextFieldState(state.confirmPassword),
                    twoFactorPasswordState = rememberTextFieldState(),
                    snackbarHostState = remember { SnackbarHostState() },
                    onBack = {},
                    onRequestPasswordChange = onRequestPasswordChange,
                    onRequestTwoFactor = {},
                    onDismissConfirmation = onDismissConfirmation,
                    onConfirmPasswordChange = onConfirmPasswordChange,
                    onConfirmTwoFactor = {},
                )
            }
        }
    }

    @Test
    fun `pressing update only opens the dialog, it does not submit`() {
        var requested = false
        var submitted = false
        setContent(
            state = ready,
            onRequestPasswordChange = { requested = true },
            onConfirmPasswordChange = { submitted = true },
        )

        composeRule.onNodeWithText("更新密码").performScrollTo().performClick()

        assertEquals(true, requested)
        assertFalse(submitted)
    }

    @Test
    fun `only the dialog's confirm submits`() {
        var submitted = false
        setContent(
            state = ready.copy(confirming = SecurityConfirmation.Password),
            onConfirmPasswordChange = { submitted = true },
        )

        composeRule.onNodeWithText("确认修改").performClick()

        assertEquals(true, submitted)
    }

    /** The dialog names the consequence, which is the point of having one. */
    @Test
    fun `the confirmation says what changing the password does to other devices`() {
        setContent(ready.copy(confirming = SecurityConfirmation.Password))

        composeRule.onNodeWithText("确认修改密码？").assertExists()
        composeRule
            .onNodeWithText("修改后除当前设备外，其他已登录设备将全部退出，需要用新密码重新登录。")
            .assertExists()
    }

    @Test
    fun `mismatched confirmations block the update`() {
        setContent(ready.copy(confirmPassword = "Correct-Horse-8"))

        composeRule.onNodeWithText("两次输入的新密码不一致").performScrollTo().assertExists()
        composeRule.onNodeWithText("更新密码").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `a too-short password blocks the update and says the minimum`() {
        setContent(ready.copy(newPassword = "short", confirmPassword = "short"))

        composeRule.onNodeWithText("新密码至少 8 位").performScrollTo().assertExists()
        composeRule.onNodeWithText("更新密码").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `a complete, matching form enables the update`() {
        setContent(ready)

        composeRule.onNodeWithText("更新密码").performScrollTo().assertIsEnabled()
    }
}
