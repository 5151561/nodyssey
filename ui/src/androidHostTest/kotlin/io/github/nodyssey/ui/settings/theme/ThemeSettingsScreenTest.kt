package io.github.nodyssey.ui.settings.theme

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.nodyssey.data.settings.ColorSource
import io.github.nodyssey.data.settings.PaletteStyle
import io.github.nodyssey.data.settings.SavedTheme
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.settings.UserSettings
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class ThemeSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    /**
     * 色彩风格 steers the generator, and a hand-written preset never reaches it.
     *
     * The five chips stay on screen — they are still the answer under 自定义 and 动态取色 — but they
     * stop being tappable, which is the only honest thing for a control that would do nothing.
     */
    @Test
    fun `色彩风格 goes flat under a hand-written preset`() {
        var style: PaletteStyle? = null
        setScreen(
            settings = UserSettings(colorSource = ColorSource.PRESET, presetId = "miku"),
            onPaletteStyleChange = { style = it },
        )

        // The row is inert, so the menu never opens and there is no 单色 to tap.
        composeRule.onNodeWithText("色彩风格").performScrollTo().performClick()
        composeRule.onNodeWithText("单色").assertDoesNotExist()
        assertNull(style)
    }

    /** 石墨青 is a seed like every other source, so its chips still work. */
    @Test
    fun `色彩风格 still works under 石墨青`() {
        var style: PaletteStyle? = null
        setScreen(
            settings = UserSettings(colorSource = ColorSource.PRESET),
            onPaletteStyleChange = { style = it },
        )

        composeRule.onNodeWithText("色彩风格").performScrollTo().performClick()
        composeRule.onNodeWithText("单色").performClick()
        assertEquals(PaletteStyle.MONOCHROME, style)
    }

    /**
     * 新建 starts a theme from nothing. It opened with the name of the theme in use, and saving a new
     * colour under it made a second theme of the same name.
     */
    @Test
    fun `新建 opens the colour sheet without the current theme's name`() {
        val sea = 0xFF2F6D8C.toInt()
        setScreen(
            settings =
            UserSettings(
                colorSource = ColorSource.CUSTOM,
                seedColor = sea,
                savedThemes = listOf(SavedTheme("海雾", sea)),
            ),
        )

        composeRule.onNodeWithText("新建").performScrollTo().performClick()

        composeRule.onAllNodes(hasSetTextAction() and hasText("海雾")).assertCountEquals(0)
    }

    private fun setScreen(
        settings: UserSettings = UserSettings(seedColor = SettingsRepository.DEFAULT_SEED_COLOR),
        onPaletteStyleChange: (PaletteStyle) -> Unit = {},
    ) {
        composeRule.setContent {
            PlazaTheme {
                ThemeSettingsScreen(
                    settings = settings,
                    onBack = {},
                    onColorSourceChange = {},
                    onPresetSelected = {},
                    onCustomSeedSelected = {},
                    onPaletteStyleChange = onPaletteStyleChange,
                    onSaveTheme = { _, _ -> },
                    onDeleteTheme = {},
                )
            }
        }
    }
}
