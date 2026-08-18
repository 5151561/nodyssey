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
import io.github.plaza.designsys.theme.PlazaTheme
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
            PlazaTheme {
                SettingsScreen(
                    state = SettingsUiState(UserSettings(themeMode = mode)),
                    onBack = {},
                    onThemeModeChange = { mode = it },
                    onFontScaleChange = {},
                    onStickerUniformSizeChange = {},
                    onStickerSizeChange = {},
                    onImagesOnWifiOnlyChange = {},
                    onExternalLinkTargetChange = {},
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

        composeRule.onNodeWithText("跟随系统").assertIsSelected()
        composeRule.onNodeWithText("深色").performClick()
        composeRule.onNodeWithText("深色").assertIsSelected()
    }

    @Test
    fun `theme choices fill the row with equal segments`() {
        composeRule.setContent {
            PlazaTheme {
                SettingsScreen(
                    state = SettingsUiState(UserSettings()),
                    onBack = {},
                    onThemeModeChange = {},
                    onFontScaleChange = {},
                    onStickerUniformSizeChange = {},
                    onStickerSizeChange = {},
                    onImagesOnWifiOnlyChange = {},
                    onExternalLinkTargetChange = {},
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

        val bounds =
            listOf("跟随系统", "浅色", "深色").map { label ->
                composeRule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
            }
        val rootWidth = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.width
        val groupWidth = bounds.last().right - bounds.first().left

        assertTrue(groupWidth > rootWidth * 0.7f)
        assertTrue(bounds.zipWithNext().all { (left, right) -> abs(left.width - right.width) <= 2f })
    }

    /**
     * 表情统一缩限 is what the slider sizes, so switching it off takes the slider with it — a size
     * control still sitting there under a switch that says "each sticker at its own size" would be
     * claiming to do something it no longer does.
     */
    @Test
    fun `the sticker size slider follows the uniform switch`() {
        composeRule.setContent {
            var uniform by remember { mutableStateOf(UserSettings().stickerUniformSize) }
            PlazaTheme {
                SettingsScreen(
                    state = SettingsUiState(UserSettings(stickerUniformSize = uniform)),
                    onBack = {},
                    onThemeModeChange = {},
                    onFontScaleChange = {},
                    onStickerUniformSizeChange = { uniform = it },
                    onStickerSizeChange = {},
                    onImagesOnWifiOnlyChange = {},
                    onExternalLinkTargetChange = {},
                    onReportFormatChange = {},
                    onHomePageBarChange = {},
                    onUpdateCheckOnLaunchChange = {},
                    onUpdateDevChannelChange = {},
                    onClearCache = {},
                )
            }
        }

        composeRule.onNodeWithTag(STICKER_SIZE_SLIDER_TAG).assertExists()
        composeRule.onNodeWithText("表情统一缩限").performScrollTo().assertIsOn().performClick()
        composeRule.onNodeWithTag(STICKER_SIZE_SLIDER_TAG).assertDoesNotExist()
    }

    /** Default is the 20sp box every build before the setting had, stated on the row itself. */
    @Test
    fun `the sticker size row states the stored size`() {
        composeRule.setContent {
            PlazaTheme {
                SettingsScreen(
                    state = SettingsUiState(UserSettings()),
                    onBack = {},
                    onThemeModeChange = {},
                    onFontScaleChange = {},
                    onStickerUniformSizeChange = {},
                    onStickerSizeChange = {},
                    onImagesOnWifiOnlyChange = {},
                    onExternalLinkTargetChange = {},
                    onReportFormatChange = {},
                    onHomePageBarChange = {},
                    onUpdateCheckOnLaunchChange = {},
                    onUpdateDevChannelChange = {},
                    onClearCache = {},
                )
            }
        }

        composeRule.onNodeWithText("20sp").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `wifi image setting toggles from the whole row`() {
        composeRule.setContent {
            var wifiOnly by remember { mutableStateOf(false) }
            PlazaTheme {
                SettingsScreen(
                    state = SettingsUiState(UserSettings(imagesOnWifiOnly = wifiOnly)),
                    onBack = {},
                    onThemeModeChange = {},
                    onFontScaleChange = {},
                    onStickerUniformSizeChange = {},
                    onStickerSizeChange = {},
                    onImagesOnWifiOnlyChange = { wifiOnly = it },
                    onExternalLinkTargetChange = {},
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
            .onNodeWithText("仅 Wi-Fi 下加载图片")
            .performScrollTo()
            .assertIsOff()
            .performClick()
            .assertIsOn()
    }

    @Test
    fun `external link target defaults to the in-app tab and can be switched`() {
        composeRule.setContent {
            var target by remember { mutableStateOf(UserSettings().externalLinkTarget) }
            PlazaTheme {
                SettingsScreen(
                    state = SettingsUiState(UserSettings(externalLinkTarget = target)),
                    onBack = {},
                    onThemeModeChange = {},
                    onFontScaleChange = {},
                    onStickerUniformSizeChange = {},
                    onStickerSizeChange = {},
                    onImagesOnWifiOnlyChange = {},
                    onExternalLinkTargetChange = { target = it },
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

        // 内容 sits below the fold on a 800dp screen now that 外观 carries the sticker controls,
        // and a tap aimed off the viewport never lands.
        composeRule.onNodeWithText("应用内浏览").performScrollTo().assertIsSelected()
        composeRule.onNodeWithText("系统浏览器").performClick()
        composeRule.onNodeWithText("系统浏览器").assertIsSelected()
    }

    @Test
    fun `report format defaults to the adapted card and can be switched to the source`() {
        composeRule.setContent {
            var format by remember { mutableStateOf(UserSettings().reportFormat) }
            PlazaTheme {
                SettingsScreen(
                    state = SettingsUiState(UserSettings(reportFormat = format)),
                    onBack = {},
                    onThemeModeChange = {},
                    onFontScaleChange = {},
                    onStickerUniformSizeChange = {},
                    onStickerSizeChange = {},
                    onImagesOnWifiOnlyChange = {},
                    onExternalLinkTargetChange = {},
                    onReportFormatChange = { format = it },
                    onHomePageBarChange = {},
                    onUpdateCheckOnLaunchChange = {},
                    onUpdateDevChannelChange = {},
                    onClearCache = {},
                    appLinkHandlingEnabled = null,
                    onOpenAppLinkSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("适配格式").performScrollTo().assertIsSelected()
        composeRule.onNodeWithText("显示原文").performClick()
        composeRule.onNodeWithText("显示原文").assertIsSelected()
    }

    /** On is the default: 首页 is read by page number in the browser, and the bar says which one. */
    @Test
    fun `the home page bar starts on and toggles from the whole row`() {
        composeRule.setContent {
            var enabled by remember { mutableStateOf(UserSettings().homePageBar) }
            PlazaTheme {
                SettingsScreen(
                    state = SettingsUiState(UserSettings(homePageBar = enabled)),
                    onBack = {},
                    onThemeModeChange = {},
                    onFontScaleChange = {},
                    onStickerUniformSizeChange = {},
                    onStickerSizeChange = {},
                    onImagesOnWifiOnlyChange = {},
                    onExternalLinkTargetChange = {},
                    onReportFormatChange = {},
                    onHomePageBarChange = { enabled = it },
                    onUpdateCheckOnLaunchChange = {},
                    onUpdateDevChannelChange = {},
                    onClearCache = {},
                    appLinkHandlingEnabled = null,
                    onOpenAppLinkSettings = {},
                )
            }
        }

        composeRule
            .onNodeWithText("首页翻页栏")
            .performScrollTo()
            .assertIsOn()
            .performClick()
            .assertIsOff()
    }

    @Test
    fun `font size label follows drag state without applying it`() {
        var appliedScale: Float? = null
        composeRule.setContent {
            PlazaTheme {
                SettingsScreen(
                    state = SettingsUiState(UserSettings(fontScale = 1f)),
                    onBack = {},
                    onThemeModeChange = {},
                    onFontScaleChange = { appliedScale = it },
                    onStickerUniformSizeChange = {},
                    onStickerSizeChange = {},
                    onImagesOnWifiOnlyChange = {},
                    onExternalLinkTargetChange = {},
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
            PlazaTheme {
                SettingsScreen(
                    state = SettingsUiState(UserSettings(fontScale = 1f)),
                    onBack = {},
                    onThemeModeChange = {},
                    onFontScaleChange = { appliedScale = it },
                    onStickerUniformSizeChange = {},
                    onStickerSizeChange = {},
                    onImagesOnWifiOnlyChange = {},
                    onExternalLinkTargetChange = {},
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

    @Test
    fun `the launch update check is on by default and can be switched off`() {
        composeRule.setContent {
            var settings by remember { mutableStateOf(UserSettings()) }
            PlazaTheme {
                SettingsScreen(
                    state = SettingsUiState(settings),
                    onBack = {},
                    onThemeModeChange = {},
                    onFontScaleChange = {},
                    onStickerUniformSizeChange = {},
                    onStickerSizeChange = {},
                    onImagesOnWifiOnlyChange = {},
                    onExternalLinkTargetChange = {},
                    onReportFormatChange = {},
                    onHomePageBarChange = {},
                    onUpdateCheckOnLaunchChange = {
                        settings = settings.copy(updateCheckOnLaunch = it)
                    },
                    onUpdateDevChannelChange = {},
                    onClearCache = {},
                    appLinkHandlingEnabled = null,
                    onOpenAppLinkSettings = {},
                )
            }
        }

        composeRule
            .onNodeWithText("启动时检查更新")
            .performScrollTo()
            .assertIsOn()
            .performClick()
            .assertIsOff()
    }

    @Test
    fun `about row states the installed version`() {
        composeRule.setContent {
            PlazaTheme {
                SettingsScreen(
                    state = SettingsUiState(versionName = "9.9.9"),
                    onBack = {},
                    onThemeModeChange = {},
                    onFontScaleChange = {},
                    onStickerUniformSizeChange = {},
                    onStickerSizeChange = {},
                    onImagesOnWifiOnlyChange = {},
                    onExternalLinkTargetChange = {},
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

        composeRule.onNodeWithText("v9.9.9").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `about row gives up the version line to the update offer`() {
        composeRule.setContent {
            PlazaTheme {
                SettingsScreen(
                    state = SettingsUiState(versionName = "9.9.9", updateVersionName = "10.0.0"),
                    onBack = {},
                    onThemeModeChange = {},
                    onFontScaleChange = {},
                    onStickerUniformSizeChange = {},
                    onStickerSizeChange = {},
                    onImagesOnWifiOnlyChange = {},
                    onExternalLinkTargetChange = {},
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

        composeRule.onNodeWithText("发现新版本 10.0.0").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("v9.9.9").assertDoesNotExist()
    }
}
