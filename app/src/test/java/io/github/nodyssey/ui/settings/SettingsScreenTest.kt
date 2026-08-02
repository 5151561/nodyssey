package io.github.nodyssey.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import io.github.nodyssey.data.settings.ThemeMode
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.ui.theme.NodysseyTheme
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
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `theme choice exposes the new selected state`() {
        composeRule.setContent {
            var mode by remember { mutableStateOf(ThemeMode.SYSTEM) }
            NodysseyTheme {
                SettingsScreen(
                    state = SettingsUiState(UserSettings(themeMode = mode)),
                    onBack = {},
                    onThemeModeChange = { mode = it },
                    onFontScaleChange = {},
                    onImagesOnWifiOnlyChange = {},
                    onExternalLinkTargetChange = {},
                    onClearCache = {},
                )
            }
        }

        composeRule.onNodeWithText("跟随系统").assertIsSelected()
        composeRule.onNodeWithText("深色").performClick()
        composeRule.onNodeWithText("深色").assertIsSelected()
    }

    @Test
    fun `theme choices fill the row with equal segments`() {
        composeRule.setContent {
            NodysseyTheme {
                SettingsScreen(
                    state = SettingsUiState(UserSettings()),
                    onBack = {},
                    onThemeModeChange = {},
                    onFontScaleChange = {},
                    onImagesOnWifiOnlyChange = {},
                    onExternalLinkTargetChange = {},
                    onClearCache = {},
                )
            }
        }

        val bounds =
            listOf("跟随系统", "浅色", "深色").map { label ->
                composeRule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
            }
        val rootWidth = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.width
        val groupWidth = bounds.last().right - bounds.first().left

        assertTrue(groupWidth > rootWidth * 0.7f)
        assertTrue(bounds.zipWithNext().all { (left, right) -> abs(left.width - right.width) <= 2f })
    }

    @Test
    fun `wifi image setting toggles from the whole row`() {
        composeRule.setContent {
            var wifiOnly by remember { mutableStateOf(false) }
            NodysseyTheme {
                SettingsScreen(
                    state = SettingsUiState(UserSettings(imagesOnWifiOnly = wifiOnly)),
                    onBack = {},
                    onThemeModeChange = {},
                    onFontScaleChange = {},
                    onImagesOnWifiOnlyChange = { wifiOnly = it },
                    onExternalLinkTargetChange = {},
                    onClearCache = {},
                )
            }
        }

        composeRule.onNodeWithText("仅 Wi-Fi 下加载图片").assertIsOff().performClick().assertIsOn()
    }

    @Test
    fun `external link target defaults to the in-app tab and can be switched`() {
        composeRule.setContent {
            var target by remember { mutableStateOf(UserSettings().externalLinkTarget) }
            NodysseyTheme {
                SettingsScreen(
                    state = SettingsUiState(UserSettings(externalLinkTarget = target)),
                    onBack = {},
                    onThemeModeChange = {},
                    onFontScaleChange = {},
                    onImagesOnWifiOnlyChange = {},
                    onExternalLinkTargetChange = { target = it },
                    onClearCache = {},
                )
            }
        }

        composeRule.onNodeWithText("应用内浏览").assertIsSelected()
        composeRule.onNodeWithText("系统浏览器").performClick()
        composeRule.onNodeWithText("系统浏览器").assertIsSelected()
    }

    @Test
    fun `font size label follows drag state without applying it`() {
        var appliedScale: Float? = null
        composeRule.setContent {
            NodysseyTheme {
                SettingsScreen(
                    state = SettingsUiState(UserSettings(fontScale = 1f)),
                    onBack = {},
                    onThemeModeChange = {},
                    onFontScaleChange = { appliedScale = it },
                    onImagesOnWifiOnlyChange = {},
                    onExternalLinkTargetChange = {},
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
            NodysseyTheme {
                SettingsScreen(
                    state = SettingsUiState(UserSettings(fontScale = 1f)),
                    onBack = {},
                    onThemeModeChange = {},
                    onFontScaleChange = { appliedScale = it },
                    onImagesOnWifiOnlyChange = {},
                    onExternalLinkTargetChange = {},
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

    @Test
    fun `about row states the installed version`() {
        composeRule.setContent {
            NodysseyTheme {
                SettingsScreen(
                    state = SettingsUiState(versionName = "9.9.9"),
                    onBack = {},
                    onThemeModeChange = {},
                    onFontScaleChange = {},
                    onImagesOnWifiOnlyChange = {},
                    onExternalLinkTargetChange = {},
                    onClearCache = {},
                )
            }
        }

        composeRule.onNodeWithText("v9.9.9").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `about row gives up the version line to the update offer`() {
        composeRule.setContent {
            NodysseyTheme {
                SettingsScreen(
                    state = SettingsUiState(versionName = "9.9.9", updateVersionName = "10.0.0"),
                    onBack = {},
                    onThemeModeChange = {},
                    onFontScaleChange = {},
                    onImagesOnWifiOnlyChange = {},
                    onExternalLinkTargetChange = {},
                    onClearCache = {},
                )
            }
        }

        composeRule.onNodeWithText("发现新版本 10.0.0").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("v9.9.9").assertDoesNotExist()
    }
}
