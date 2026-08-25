package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import io.github.nodyssey.data.diagnostics.AppIdentity
import io.github.nodyssey.data.diagnostics.DeviceIdentity
import io.github.nodyssey.data.diagnostics.NetworkEnvironment
import io.github.nodyssey.data.diagnostics.NetworkTransport
import io.github.nodyssey.data.diagnostics.ProbeResult
import io.github.nodyssey.data.diagnostics.ProbeTiming
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What a screenshot of 网络自检 has to carry.
 *
 * These are assertions about a *report*, not about a layout: each one names a value somebody reading
 * the screenshot in a forum thread has to be able to find. The device and the two browser packages
 * are the ones that took a week of back-and-forth to establish by asking, which is why the screen
 * exists at all.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class NetworkCheckScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Composable
    private fun Screen(state: NetworkCheckUiState) {
        PlazaTheme {
            NetworkCheckScreen(state = state, onBack = {}, onRerun = {})
        }
    }

    @Test
    fun `the report names the phone the numbers were measured on`() {
        composeRule.setContent { Screen(NetworkCheckUiState(environment = ENVIRONMENT)) }

        composeRule.onNodeWithText("Xiaomi 14").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Android 15 (API 35)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("1.2.12").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `both browser packages are on screen so the reader can tell them apart`() {
        composeRule.setContent { Screen(NetworkCheckUiState(environment = ENVIRONMENT)) }

        // A Custom Tab's traffic belongs to the first of these, not to this app — which is the whole
        // reason the second is printed beside it.
        composeRule.onNodeWithText("com.android.chrome", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("com.quark.browser", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a slow transfer is reported as a rate and as the size it was measured over`() {
        composeRule.setContent {
            Screen(NetworkCheckUiState(environment = ENVIRONMENT, forum = SLOW))
        }

        composeRule.onNodeWithText("5.2 KB/s").performScrollTo().assertIsDisplayed()
        // The size and the elapsed time stay beside the rate: a rate off a small sample is noise,
        // and only these two say how big the sample was.
        composeRule.onNodeWithText("68.2 KB / 13.0 s").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a half finished report cannot be copied`() {
        composeRule.setContent {
            Screen(NetworkCheckUiState(running = true, environment = ENVIRONMENT))
        }

        composeRule.onNodeWithText("复制结果").performScrollTo().assertIsNotEnabled()
    }

    private companion object {
        val ENVIRONMENT =
            NetworkEnvironment(
                device = DeviceIdentity("Xiaomi 14", "Android 15 (API 35)"),
                appVersion = "1.2.12",
                transport = NetworkTransport.WIFI,
                vpnActive = true,
                metered = false,
                proxy = null,
                dohProvider = null,
                customTabsProvider = AppIdentity("Chrome", "com.android.chrome"),
                defaultBrowser = AppIdentity("夸克浏览器", "com.quark.browser"),
            )

        val SLOW =
            ProbeResult.Answered(
                statusCode = 200,
                timing = ProbeTiming(
                    dnsMillis = 14,
                    connectMillis = 210,
                    tlsMillis = 180,
                    firstByteMillis = 600,
                    totalMillis = 13_600,
                    bytes = 69_800,
                ),
            )
    }
}
