package io.github.nodyssey.render

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.nodyssey.data.account.BlockedUser
import io.github.nodyssey.data.account.TelegramBinding
import io.github.nodyssey.ui.account.BlockListScreen
import io.github.nodyssey.ui.account.BlockListUiState
import io.github.nodyssey.ui.account.ContactScreen
import io.github.nodyssey.ui.account.ContactUiState
import io.github.nodyssey.ui.account.SecurityScreen
import io.github.nodyssey.ui.account.SecurityUiState
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The 账户与安全 sub-pages that have no board of their own, on the card system — light only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class AccountScreensRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Test
    fun `security in light`() {
        composeRule.setContent {
            PlazaTheme {
                SecurityScreen(
                    state = SecurityUiState(isLoading = false, twoFactorEnabled = false),
                    currentPasswordState = rememberTextFieldState(),
                    newPasswordState = rememberTextFieldState(),
                    confirmPasswordState = rememberTextFieldState(),
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

        composeRule.onRoot().captureRender("account-security-light")
    }

    @Test
    fun `block list in light`() {
        composeRule.setContent {
            PlazaTheme {
                BlockListScreen(
                    state =
                    BlockListUiState(
                        isLoading = false,
                        blocked = listOf(BlockedUser(uid = 1, name = "机场信仰充值中"), BlockedUser(uid = 2, name = "vps_matthew")),
                    ),
                    snackbarHostState = remember { SnackbarHostState() },
                    onBack = {},
                    onShowBlockedChange = {},
                    onNameChange = {},
                    onBlock = {},
                    onRequestUnblock = {},
                    onDismissUnblock = {},
                    onConfirmUnblock = {},
                    onOpenUser = {},
                )
            }
        }

        composeRule.onRoot().captureRender("account-block-list-light")
    }

    @Test
    fun `contact in dark`() = contact(darkTheme = true, bound = false, name = "account-contact-dark")

    /** A bound Telegram account: 「已绑定」 on its tag beside the card's title. */
    @Test
    fun `contact with Telegram bound, in light`() = contact(darkTheme = false, bound = true, name = "account-contact-bound-light")

    private fun contact(
        darkTheme: Boolean,
        bound: Boolean,
        name: String,
    ) {
        composeRule.setContent {
            PlazaTheme(darkTheme = darkTheme) {
                ContactScreen(
                    state =
                    ContactUiState(
                        isLoading = false,
                        email = "someone@example.com",
                        emailVerified = true,
                        telegram =
                        if (bound) TelegramBinding(bound = true, displayName = "Nody Sseus") else TelegramBinding(bound = false),
                    ),
                    snackbarHostState = remember { SnackbarHostState() },
                    onBack = {},
                    onChangeEmail = {},
                    onRequestBind = {},
                    onDismissBind = {},
                    onConfirmBind = {},
                    onRefreshBinding = {},
                    onRequestUnbind = {},
                    onDismissUnbind = {},
                    onConfirmUnbind = {},
                )
            }
        }

        composeRule.onRoot().captureRender(name)
    }
}
