package io.github.nodyssey.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.nodyssey.data.diagnostics.AppIdentity
import io.github.nodyssey.data.diagnostics.DeviceIdentity
import io.github.nodyssey.data.diagnostics.NetworkEnvironment
import io.github.nodyssey.data.diagnostics.NetworkTransport
import io.github.nodyssey.data.diagnostics.ProbeResult
import io.github.nodyssey.data.diagnostics.ProbeTiming
import io.github.nodyssey.data.diagnostics.SessionSummary
import io.github.nodyssey.ui.settings.NetworkCheckScreen
import io.github.nodyssey.ui.settings.NetworkCheckUiState
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The worked example for [captureRender] — 网络自检 in both themes.
 *
 * This screen is the example because it is the one whose *look* is the feature: it exists to be
 * screenshotted into a forum thread, so a picture of it is worth more than any assertion. Copy the
 * shape of this file for whatever screen a change touches.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1800dp")
class NetworkCheckScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun Screen(darkTheme: Boolean) {
        PlazaTheme(darkTheme = darkTheme) {
            NetworkCheckScreen(
                state = NetworkCheckUiState(environment = ENVIRONMENT, forum = ANSWERED),
                onBack = {},
                onRerun = {},
            )
        }
    }

    @Test
    fun `the finished report in light`() {
        composeRule.setContent { Screen(darkTheme = false) }

        composeRule.onRoot().captureRender("network-check-light")
    }

    @Test
    fun `the finished report in dark`() {
        composeRule.setContent { Screen(darkTheme = true) }

        composeRule.onRoot().captureRender("network-check-dark")
    }

    private companion object {
        val ENVIRONMENT =
            NetworkEnvironment(
                device = DeviceIdentity("Xiaomi 14", "Android 15 (API 35)"),
                appVersion = "1.2.21",
                transport = NetworkTransport.WIFI,
                vpnActive = true,
                metered = false,
                proxy = null,
                dohProvider = null,
                customTabsProvider = AppIdentity("Chrome", "com.android.chrome"),
                defaultBrowser = AppIdentity("夸克浏览器", "com.quark.browser"),
                session = SessionSummary(
                    signedIn = true,
                    hasClearance = true,
                    cookieNames = listOf("session", "cf_clearance"),
                    webView = AppIdentity("Android System WebView 131.0.6778.39", "com.google.android.webview"),
                ),
            )

        val ANSWERED =
            ProbeResult.Answered(
                statusCode = 200,
                timing = ProbeTiming(
                    dnsMillis = 14,
                    connectMillis = 96,
                    tlsMillis = 120,
                    firstByteMillis = 310,
                    totalMillis = 640,
                    bytes = 69_800,
                ),
            )
    }
}
