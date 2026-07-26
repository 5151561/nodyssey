package io.github.nsreader.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import io.github.nsreader.data.settings.ThemeMode
import io.github.nsreader.data.settings.UserSettings
import io.github.nsreader.ui.theme.NodeSeekTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `theme choice exposes the new selected state`() {
        composeRule.setContent {
            var mode by remember { mutableStateOf(ThemeMode.SYSTEM) }
            NodeSeekTheme {
                SettingsScreen(
                    state = SettingsUiState(UserSettings(themeMode = mode)),
                    onBack = {},
                    onThemeModeChange = { mode = it },
                    onFontScaleChange = {},
                    onImagesOnWifiOnlyChange = {},
                    onClearCache = {},
                )
            }
        }

        composeRule.onNodeWithText("跟随系统").assertIsSelected()
        composeRule.onNodeWithText("深色").performClick()
        composeRule.onNodeWithText("深色").assertIsSelected()
    }

    @Test
    fun `font size label follows drag state without applying it`() {
        var appliedScale: Float? = null
        composeRule.setContent {
            NodeSeekTheme {
                SettingsScreen(
                    state = SettingsUiState(UserSettings(fontScale = 1f)),
                    onBack = {},
                    onThemeModeChange = {},
                    onFontScaleChange = { appliedScale = it },
                    onImagesOnWifiOnlyChange = {},
                    onClearCache = {},
                )
            }
        }

        val slider = composeRule.onNodeWithTag(BODY_FONT_SIZE_SLIDER_TAG)
        composeRule.onNodeWithText("16sp").assertTextEquals("16sp")
        slider.performTouchInput {
            down(percentOffset(0.2f, 0.5f))
            moveTo(center, delayMillis = 100)
        }

        composeRule.onNodeWithText("16sp").assertDoesNotExist()
        assertTrue(appliedScale == null)

        slider.performTouchInput { up() }
    }

    @Test
    fun `font size applies only after the drag is released`() {
        var appliedScale: Float? = null
        composeRule.setContent {
            NodeSeekTheme {
                SettingsScreen(
                    state = SettingsUiState(UserSettings(fontScale = 1f)),
                    onBack = {},
                    onThemeModeChange = {},
                    onFontScaleChange = { appliedScale = it },
                    onImagesOnWifiOnlyChange = {},
                    onClearCache = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(BODY_FONT_SIZE_SLIDER_TAG)
            .performTouchInput { swipe(start = center, end = centerRight) }

        composeRule.waitForIdle()
        assertTrue(appliedScale != null)
    }
}
