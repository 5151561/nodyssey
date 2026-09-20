package io.github.nodyssey.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.ui.settings.theme.ThemeSettingsScreen
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 主题 in both themes — the M3 colour picker that drives the whole app's palette. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class ThemeSettingsScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun Screen(darkTheme: Boolean) {
        PlazaTheme(darkTheme = darkTheme) {
            ThemeSettingsScreen(
                settings = UserSettings(),
                onBack = {},
                onOpenDynamicColor = {},
                onColorSourceChange = {},
                onPresetSelected = {},
                onCustomSeedSelected = {},
                onPaletteStyleChange = {},
                onSaveTheme = { _, _ -> },
                onDeleteTheme = {},
            )
        }
    }

    @Test
    fun `the theme picker in light`() {
        composeRule.setContent { Screen(darkTheme = false) }

        composeRule.onRoot().captureRender("theme-light")
    }

    @Test
    fun `the theme picker in dark`() {
        composeRule.setContent { Screen(darkTheme = true) }

        composeRule.onRoot().captureRender("theme-dark")
    }
}
