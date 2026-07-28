package io.github.nodyssey.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.nodyssey.ui.theme.NodysseyTheme
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
class AboutAppScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `app page exposes version and license action`() {
        var openedLicenses = false
        composeRule.setContent {
            NodysseyTheme {
                AboutAppScreen(
                    versionName = "9.9",
                    versionCode = 99,
                    updateStatus = AppUpdateStatus.Available("10.0"),
                    onBack = {},
                    onCheckUpdates = {},
                    onOpenChangelog = {},
                    onOpenLicenses = { openedLicenses = true },
                    onOpenUri = {},
                )
            }
        }

        composeRule.onNodeWithText("版本 9.9 (99)").assertIsDisplayed()
        composeRule.onNodeWithText("开源许可").performScrollTo().performClick()

        assertTrue(openedLicenses)
    }

    @Test
    fun `community information is absent from the app page`() {
        composeRule.setContent {
            NodysseyTheme {
                AboutAppScreen(
                    versionName = "1.0",
                    versionCode = 1,
                    updateStatus = AppUpdateStatus.Latest,
                    onBack = {},
                    onCheckUpdates = {},
                    onOpenChangelog = {},
                    onOpenLicenses = {},
                    onOpenUri = {},
                )
            }
        }

        composeRule.onNodeWithText("关于 Nodyssey").assertIsDisplayed()
        composeRule.onNodeWithText("关于本站").assertDoesNotExist()
        composeRule.onNodeWithText("RSS 订阅").assertDoesNotExist()
        composeRule.onNodeWithText("电报频道").assertDoesNotExist()
    }
}
