package io.github.nodyssey.ui.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.nodyssey.data.proxy.ProxyConnectionFailure
import io.github.nodyssey.data.proxy.ProxyScope
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class ProxySettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Composable
    private fun Screen(
        state: ProxySettingsUiState,
        onForumOnlyChange: (Boolean) -> Unit = {},
    ) {
        PlazaTheme {
            ProxySettingsScreen(
                state = state,
                snackbarHostState = remember { SnackbarHostState() },
                onBack = {},
                onEnabledChange = {},
                onTypeChange = {},
                onForumOnlyChange = onForumOnlyChange,
                onHostChange = {},
                onPortChange = {},
                onUsernameChange = {},
                onPasswordChange = {},
                onSave = {},
                onTest = {},
            )
        }
    }

    /** The scope switch is a setting for a proxy, so it is as inert as the address fields without one. */
    @Test
    fun `the scope switch is off and disabled until the proxy is`() {
        composeRule.setContent { Screen(ProxySettingsUiState()) }

        composeRule.onNodeWithText("只代理论坛").performScrollTo().assertIsOff().assertIsNotEnabled()
    }

    @Test
    fun `turning the scope switch on asks for forum-only`() {
        var scope = ProxyScope.EVERYTHING
        composeRule.setContent {
            var current by remember { mutableStateOf(ProxyScope.EVERYTHING) }
            Screen(
                state = ProxySettingsUiState(enabled = true, scope = current),
                onForumOnlyChange = { forumOnly ->
                    current = if (forumOnly) ProxyScope.FORUM_ONLY else ProxyScope.EVERYTHING
                    scope = current
                },
            )
        }

        composeRule.onNodeWithText("只代理论坛").performScrollTo().performClick()

        assertEquals(ProxyScope.FORUM_ONLY, scope)
        composeRule.onNodeWithText("只代理论坛").assertIsOn()
    }

    /**
     * The one place the app says out loud what it cannot do: no node protocols of its own, and no say
     * over the login WebView's traffic. Both are read before anything is typed, so both stay legible
     * while the rest of the screen is dimmed.
     */
    @Test
    fun `the advanced-node note is on screen with the proxy off`() {
        composeRule.setContent { Screen(ProxySettingsUiState()) }

        composeRule.onNodeWithText("VLESS、VMess 这类节点").performScrollTo().assertExists()
        composeRule.onNodeWithText("登录用的是系统 WebView，它走自己的网络，不受这里的设置影响。")
            .performScrollTo()
            .assertExists()
    }

    @Test
    fun `a connection-test failure names the failed network layer and exception`() {
        composeRule.setContent {
            Screen(
                ProxySettingsUiState(
                    enabled = true,
                    testFailure = ProxyConnectionFailure(
                        ProxyConnectionFailure.Kind.SOCKS_AUTHENTICATION,
                        "SocketException",
                    ),
                ),
            )
        }

        composeRule.onNodeWithText("SOCKS5 认证失败，请检查用户名和密码（SocketException）")
            .performScrollTo()
            .assertExists()
    }
}
