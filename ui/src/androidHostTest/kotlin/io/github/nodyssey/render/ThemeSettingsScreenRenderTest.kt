package io.github.nodyssey.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.nodyssey.data.settings.ColorSource
import io.github.nodyssey.data.settings.SavedTheme
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
    private fun Screen(
        darkTheme: Boolean,
        settings: UserSettings = UserSettings(savedThemes = SAVED),
    ) {
        PlazaTheme(darkTheme = darkTheme) {
            ThemeSettingsScreen(
                settings = settings,
                onBack = {},
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

    /** 6c3's card: 自定义 in force, named after the saved theme it matches. */
    @Test
    @Config(qualifiers = "w360dp-h1100dp")
    fun `the custom seed in light`() {
        composeRule.setContent {
            Screen(
                darkTheme = false,
                settings =
                UserSettings(colorSource = ColorSource.CUSTOM, seedColor = SAVED.first().color, savedThemes = SAVED),
            )
        }

        composeRule.onRoot().captureRender("theme-custom-light")
    }

    /** 色彩风格's menu, open over the page — the current style is the selected entry. */
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    @Config(qualifiers = "w360dp-h1100dp")
    fun `the palette style menu in light`() {
        composeRule.setContent { Screen(darkTheme = false) }
        composeRule.onNodeWithText("色彩风格").performScrollTo().performClick()
        composeRule.waitForIdle()

        captureScreenRoboImage(filePath = "build/outputs/renders/theme-palette-menu-light.png")
    }

    /** 新建's colour sheet (6c2), in a window of its own. */
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    @Config(qualifiers = "w360dp-h1100dp")
    fun `the seed colour sheet in light`() {
        composeRule.setContent { Screen(darkTheme = false) }
        composeRule.onNodeWithText("新建").performScrollTo().performClick()
        composeRule.waitForIdle()

        captureScreenRoboImage(filePath = "build/outputs/renders/theme-seed-sheet-light.png")
    }

    /** The whole page, 6c's preview card included. */
    @Test
    @Config(qualifiers = "w360dp-h1300dp")
    fun `the whole theme page in dark`() {
        composeRule.setContent { Screen(darkTheme = true) }

        composeRule.onRoot().captureRender("theme-full-dark")
    }

    @Test
    @Config(qualifiers = "w360dp-h1300dp")
    fun `the whole theme page in light`() {
        composeRule.setContent { Screen(darkTheme = false) }

        composeRule.onRoot().captureRender("theme-full-light")
    }

    private companion object {
        val SAVED = listOf(SavedTheme("夜读青", 0xFF35606E.toInt()), SavedTheme("琥珀", 0xFF8A5A00.toInt()))
    }
}
