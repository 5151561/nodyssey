package io.github.nodyssey.ui.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.nodyssey.data.dns.DnsResolution
import io.github.nodyssey.data.dns.DohCapabilities
import io.github.nodyssey.data.dns.DohProvider
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
class DohSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Composable
    private fun Screen(
        state: DohSettingsUiState,
        onProviderChange: (DohProvider) -> Unit = {},
    ) {
        PlazaTheme {
            DohSettingsScreen(
                state = state,
                snackbarHostState = remember { SnackbarHostState() },
                onBack = {},
                onEnabledChange = {},
                onProviderChange = onProviderChange,
                onUrlChange = {},
                onBootstrapChange = {},
                onIncludeIPv6Change = {},
                onFallbackChange = {},
                onSave = {},
                onTest = {},
            )
        }
    }

    /** Nothing below the master switch is worth touching until there is something to configure. */
    @Test
    fun `the provider rows are inert until 加密 DNS is on`() {
        composeRule.setContent { Screen(DohSettingsUiState()) }

        composeRule.onNodeWithText("阿里 DNS").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("解析失败时回退系统 DNS").performScrollTo().assertIsOff().assertIsNotEnabled()
    }

    @Test
    fun `picking a provider selects its row`() {
        var picked: DohProvider? = null
        composeRule.setContent {
            var provider by remember { mutableStateOf(DohProvider.ALIDNS) }
            Screen(
                state = DohSettingsUiState(enabled = true, provider = provider),
                onProviderChange = {
                    provider = it
                    picked = it
                },
            )
        }

        composeRule.onNodeWithText("Cloudflare").performScrollTo().performClick()

        assertEquals(DohProvider.CLOUDFLARE, picked)
        composeRule.onNodeWithText("Cloudflare").assertIsSelected()
    }

    /** The address fields belong to 自定义 and to nothing else; a preset already knows its server. */
    @Test
    fun `the server fields appear only for 自定义`() {
        composeRule.setContent {
            var provider by remember { mutableStateOf(DohProvider.ALIDNS) }
            Screen(
                state = DohSettingsUiState(enabled = true, provider = provider),
                onProviderChange = { provider = it },
            )
        }

        composeRule.onNodeWithText("服务器地址").assertDoesNotExist()

        composeRule.onNodeWithText("自定义").performScrollTo().performClick()

        composeRule.onNodeWithText("服务器地址").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("引导 IP（可选）").performScrollTo().assertIsDisplayed()
    }

    /**
     * The answer, addresses and all: someone who opened this screen because a domain was being
     * hijacked recognises the real records, and "解析成功" would tell them nothing.
     */
    @Test
    fun `测试解析 shows the addresses it got back`() {
        composeRule.setContent {
            Screen(
                DohSettingsUiState(
                    enabled = true,
                    resolution = DnsResolution("www.nodeseek.com", listOf("104.21.32.1", "172.67.140.1"), 86),
                ),
            )
        }

        composeRule
            .onNodeWithText("www.nodeseek.com → 104.21.32.1、172.67.140.1（86 ms）")
            .performScrollTo()
            .assertIsDisplayed()
    }

    /**
     * Two of the switches are questions only a resolver the app owns can be asked. Where the system
     * owns it, the row is absent rather than disabled — a switch that stored a value nothing reads is
     * worse than no switch.
     */
    @Test
    fun `the record-type and fallback switches are absent where the platform owns the resolver`() {
        composeRule.setContent {
            Screen(
                DohSettingsUiState(
                    enabled = true,
                    capabilities = DohCapabilities(canChooseRecordTypes = false, canFallBackToSystem = false),
                ),
            )
        }

        composeRule.onNodeWithText("同时查 IPv6 地址").assertDoesNotExist()
        composeRule.onNodeWithText("解析失败时回退系统 DNS").assertDoesNotExist()
        // And what replaces them is the sentence that says why: there is no falling back there.
        composeRule
            .onNodeWithText("这台设备上，开着的时候解析不出来就是解析不出来，不会退回明文 DNS；系统里已经配了加密 DNS 的话，以系统那份为准。")
            .performScrollTo()
            .assertIsDisplayed()
    }

    /** DoH answers a question about names, and the screen has to say so before anyone types a server. */
    @Test
    fun `the screen says what changing the resolver cannot fix`() {
        composeRule.setContent { Screen(DohSettingsUiState()) }

        composeRule.onNodeWithText("它只管 DNS 这一层").performScrollTo().assertIsDisplayed()
    }
}
