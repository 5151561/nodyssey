package io.github.nodyssey.ui.settings.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.nodyssey.data.settings.ColorSource
import io.github.nodyssey.data.settings.PaletteStyle
import io.github.nodyssey.data.settings.SavedTheme
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.settings.ThemeMode
import io.github.nodyssey.data.settings.UserSettings
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

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
     * The tile carries the value it would restore, which is what makes the line under it true.
     *
     * Under 自定义 that value is the stored seed rather than the preset's — the whole point of three
     * separate fields — so a build that collapsed them back into one would fail here.
     */
    @Test
    fun `each source tile names the value it would put back`() {
        setScreen(
            settings =
            UserSettings(
                colorSource = ColorSource.PRESET,
                presetId = "tianyi",
                seedColor = 0xFF2F6D8C.toInt(),
            ),
        )

        // 预设 names the section as well as the tile, so the tile is the node that carries both it
        // and the preset's own name.
        composeRule.onNode(hasText("预设").and(hasText("蓝×白×粉"))).assertIsSelected()
        composeRule.onNode(hasText("自定义").and(hasText("#2F6D8C"))).assertExists()
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

        composeRule.onNodeWithText("单色").performScrollTo().performClick()
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

        composeRule.onNodeWithText("单色").performScrollTo().performClick()
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

        // 青×灰×粉 exists only in the grid; 石墨青 is also the 预设 tile's own subtitle, which stays.
        composeRule.onNodeWithText("青×灰×粉").assertDoesNotExist()
        composeRule.onNode(hasText("预设").and(hasText("石墨青"))).assertExists()
    }

    @Test
    fun `动态取色 collapses the preset grid`() {
        setScreen(settings = UserSettings(colorSource = ColorSource.WALLPAPER))

        composeRule.onNodeWithText("青×灰×粉").assertDoesNotExist()
        composeRule.onNode(hasText("预设").and(hasText("石墨青"))).assertExists()
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

    @Test
    fun `the palette style row offers all five and reports the tap`() {
        var style = UserSettings().paletteStyle
        setScreen(onPaletteStyleChange = { style = it })

        listOf("柔和", "鲜艳", "表现力", "中性", "单色").forEach {
            composeRule.onNodeWithText(it).performScrollTo().assertExists()
        }
        composeRule.onNodeWithText("单色").performScrollTo().performClick()
        assertEquals(PaletteStyle.MONOCHROME, style)
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
                    onOpenDynamicColor = {},
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
