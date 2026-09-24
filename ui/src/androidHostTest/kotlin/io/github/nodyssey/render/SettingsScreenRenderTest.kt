package io.github.nodyssey.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.nodyssey.data.dns.DefaultDohServers
import io.github.nodyssey.data.imagehost.ImageHostProvider
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.ui.settings.SettingsScreen
import io.github.nodyssey.ui.settings.SettingsUiState
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 设置 in both themes — the top of the settings list. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class SettingsScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun Screen(darkTheme: Boolean) {
        PlazaTheme(darkTheme = darkTheme) {
            SettingsScreen(
                state = STATE,
                appLinkHandlingEnabled = true,
                onOpenAppLinkSettings = {},
                onBack = {},
                onOpenTheme = {},
                onThemeModeChange = {},
                onOneHandModeChange = {},
                onFontScaleChange = {},
                onStickerUniformSizeChange = {},
                onStickerSizeChange = {},
                onImagesOnWifiOnlyChange = {},
                onReportFormatChange = {},
                onHomePageBarChange = {},
                onUpdateCheckOnLaunchChange = {},
                onUpdateDevChannelChange = {},
                onClearCache = {},
            )
        }
    }

    @Test
    fun `the settings list in light`() {
        composeRule.setContent { Screen(darkTheme = false) }

        composeRule.onRoot().captureRender("settings-light")
    }

    @Test
    fun `the settings list in dark`() {
        composeRule.setContent { Screen(darkTheme = true) }

        composeRule.onRoot().captureRender("settings-dark")
    }

    /** The whole list at once, so every group can be compared with 6a / 6b without scrolling. */
    @Test
    @Config(qualifiers = "w360dp-h2000dp")
    fun `the whole settings list in light`() {
        composeRule.setContent { Screen(darkTheme = false) }

        composeRule.onRoot().captureRender("settings-full-light")
    }

    @Test
    @Config(qualifiers = "w360dp-h2000dp")
    fun `the whole settings list in dark`() {
        composeRule.setContent { Screen(darkTheme = true) }

        composeRule.onRoot().captureRender("settings-full-dark")
    }

    private companion object {
        val STATE =
            SettingsUiState(
                settings = UserSettings(),
                cacheSizeBytes = 34_500_000,
                versionName = "1.2.21",
                imageHostProvider = ImageHostProvider.SMMS,
                imageHostConnected = true,
                dohChain = DefaultDohServers.filter { it.checked },
                hasNetworkCheck = true,
            )
    }
}
