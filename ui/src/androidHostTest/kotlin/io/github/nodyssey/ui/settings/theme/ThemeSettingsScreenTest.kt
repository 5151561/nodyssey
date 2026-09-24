package io.github.nodyssey.ui.settings.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
     * 明暗 belongs to 设置, not here.
     *
     * j1 opens this screen with it, and it is the one part of the board deliberately left behind —
     * it is flipped far more often than anything below it. `SettingsScreenTest` owns its two tests;
     * this one guards the other direction, that it did not end up on both screens.
     */
    @Test
    fun `明暗 is not offered again on 主题`() {
        setScreen()

        composeRule.onNodeWithText("跟随系统").assertDoesNotExist()
        composeRule.onNodeWithText("浅色").assertDoesNotExist()
        composeRule.onNodeWithText("深色").assertDoesNotExist()
    }

    /**
     * Each source keeps its own stored value, and the card shows the one in force.
     *
     * Under 自定义 that value is the stored seed rather than the preset's — the whole point of three
     * separate fields — so a build that collapsed them back into one would fail here.
     */
    @Test
    fun `each source shows the value it would put back`() {
        setScreen(
            settings =
            UserSettings(
                colorSource = ColorSource.CUSTOM,
                presetId = "tianyi",
                seedColor = 0xFF2F6D8C.toInt(),
            ),
        )

        composeRule.onNodeWithText("自定义").assertIsSelected()
        composeRule.onNodeWithText("#2F6D8C").assertExists()
        composeRule.onNodeWithText("蓝×白×粉").assertDoesNotExist()
    }

    @Test
    fun `预设 selects the stored preset in the grid`() {
        setScreen(settings = UserSettings(colorSource = ColorSource.PRESET, presetId = "tianyi"))

        composeRule.onNodeWithText("预设").assertIsSelected()
        composeRule.onNode(hasText("蓝×白×粉")).assertIsSelected()
    }

    @Test
    fun `picking a preset reports the id behind it`() {
        var picked: String? = null
        setScreen(
            settings = UserSettings(colorSource = ColorSource.PRESET),
            onPresetSelected = { picked = it },
        )

        composeRule.onNodeWithText("黑×金×紫").performScrollTo().performClick()
        assertEquals("marisa", picked)
    }

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
     * The grid is two rows of 56dp swatches — most of the page — and under the other two sources
     * none of them is the colour in force. The 预设 tile still names the preset it would restore, so
     * what collapses is the control, not the answer.
     */
    @Test
    fun `自定义 collapses the preset grid`() {
        setScreen(settings = UserSettings(colorSource = ColorSource.CUSTOM))

        composeRule.onNodeWithText("青×灰×粉").assertDoesNotExist()
        composeRule.onNodeWithText("预设").assertExists()
    }

    @Test
    fun `动态取色 collapses the preset grid`() {
        setScreen(settings = UserSettings(colorSource = ColorSource.WALLPAPER))

        composeRule.onNodeWithText("青×灰×粉").assertDoesNotExist()
        composeRule.onNodeWithText("预设").assertExists()
    }

    /** 我的主题 selects a saved seed; the same tap must not also save it again. */
    @Test
    fun `a saved theme chip applies its colour without resaving it`() {
        var applied: Int? = null
        var saved: Pair<String, Int>? = null
        setScreen(
            settings =
            UserSettings(savedThemes = listOf(SavedTheme("海雾", 0xFF2F6D8C.toInt()))),
            onCustomSeedSelected = { applied = it },
            onSaveTheme = { name, argb -> saved = name to argb },
        )

        composeRule.onNodeWithText("海雾").performScrollTo().performClick()
        assertEquals(0xFF2F6D8C.toInt(), applied)
        assertNull(saved)
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

    @Test
    fun `the palette style row offers all five and reports the tap`() {
        var style = UserSettings().paletteStyle
        setScreen(onPaletteStyleChange = { style = it })

        composeRule.onNodeWithText("色彩风格").performScrollTo().performClick()
        // 柔和 is the row's own answer as well as a menu entry, hence the count rather than a lookup.
        listOf("鲜艳", "表现力", "中性", "单色").forEach {
            composeRule.onNodeWithText(it).assertExists()
        }
        composeRule.onAllNodesWithText("柔和").assertCountEquals(2)
        composeRule.onNodeWithText("单色").performClick()
        assertEquals(PaletteStyle.MONOCHROME, style)
    }

    /** The style in force is the menu's selected entry, which is what a screen reader says of it. */
    @Test
    fun `the palette style menu marks the current style as selected`() {
        setScreen()

        composeRule.onNodeWithText("色彩风格").performScrollTo().performClick()

        composeRule.onNode(hasText("柔和") and isSelectable()).assertIsSelected()
        composeRule.onNode(hasText("单色") and isSelectable()).assertIsNotSelected()
    }

    private fun setScreen(
        settings: UserSettings = UserSettings(seedColor = SettingsRepository.DEFAULT_SEED_COLOR),
        onPresetSelected: (String) -> Unit = {},
        onCustomSeedSelected: (Int) -> Unit = {},
        onPaletteStyleChange: (PaletteStyle) -> Unit = {},
        onSaveTheme: (String, Int) -> Unit = { _, _ -> },
    ) {
        composeRule.setContent {
            PlazaTheme {
                ThemeSettingsScreen(
                    settings = settings,
                    onBack = {},
                    onColorSourceChange = {},
                    onPresetSelected = onPresetSelected,
                    onCustomSeedSelected = onCustomSeedSelected,
                    onPaletteStyleChange = onPaletteStyleChange,
                    onSaveTheme = onSaveTheme,
                    onDeleteTheme = {},
                )
            }
        }
    }
}
