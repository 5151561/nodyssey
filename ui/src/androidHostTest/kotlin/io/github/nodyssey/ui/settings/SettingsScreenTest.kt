package io.github.nodyssey.ui.settings

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import io.github.nodyssey.data.settings.UserSettings
import io.github.plaza.designsys.theme.PlazaTheme
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
    fun `font size label follows drag state without applying it`() {
        var appliedScale: Float? = null
        composeRule.setContent {
            PlazaTheme {
                SettingsScreen(
                    state = SettingsUiState(UserSettings(fontScale = 1f)),
                    onBack = {},
                    onOpenTheme = {},
                    onThemeModeChange = {},
                    onOneHandModeChange = {},
                    onFontScaleChange = { appliedScale = it },
                    onStickerUniformSizeChange = {},
                    onStickerSizeChange = {},
                    onImagesOnWifiOnlyChange = {},
                    onReportFormatChange = {},
                    onHomePageBarChange = {},
                    onUpdateCheckOnLaunchChange = {},
                    onUpdateDevChannelChange = {},
                    onClearCache = {},
                    appLinkHandlingEnabled = null,
                    onOpenAppLinkSettings = {},
                )
            }
        }

        val slider = composeRule.onNodeWithTag(BODY_FONT_SIZE_SLIDER_TAG)
        composeRule.onNodeWithText("16sp", useUnmergedTree = true).assertTextEquals("16sp")
        slider.performTouchInput {
            down(percentOffset(0.2f, 0.5f))
            moveTo(center, delayMillis = 100)
        }

        composeRule.onNodeWithText("16sp", useUnmergedTree = true).assertDoesNotExist()
        assertTrue(appliedScale == null)

        slider.performTouchInput { up() }
    }

    @Test
    fun `font size applies only after the drag is released`() {
        var appliedScale: Float? = null
        composeRule.setContent {
            PlazaTheme {
                SettingsScreen(
                    state = SettingsUiState(UserSettings(fontScale = 1f)),
                    onBack = {},
                    onOpenTheme = {},
                    onThemeModeChange = {},
                    onOneHandModeChange = {},
                    onFontScaleChange = { appliedScale = it },
                    onStickerUniformSizeChange = {},
                    onStickerSizeChange = {},
                    onImagesOnWifiOnlyChange = {},
                    onReportFormatChange = {},
                    onHomePageBarChange = {},
                    onUpdateCheckOnLaunchChange = {},
                    onUpdateDevChannelChange = {},
                    onClearCache = {},
                    appLinkHandlingEnabled = null,
                    onOpenAppLinkSettings = {},
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
