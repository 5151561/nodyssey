package io.github.nsreader.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.nsreader.ui.theme.NodeSeekTheme
import org.junit.Assert.assertEquals
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
class AboutCommunityScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `community and legal rows expose their actions`() {
        var openedAbout = false
        var openedPrivacy = false
        var copiedRss = false
        val openedUris = mutableListOf<String>()
        composeRule.setContent {
            NodeSeekTheme {
                AboutCommunityScreen(
                    versionName = "9.9",
                    versionCode = 99,
                    updateStatus = AppUpdateStatus.Latest,
                    onBack = {},
                    onCheckUpdates = {},
                    onOpenAboutSite = { openedAbout = true },
                    onOpenPrivacy = { openedPrivacy = true },
                    onOpenChangelog = {},
                    onOpenLicenses = {},
                    onOpenUri = openedUris::add,
                    onCopyRss = { copiedRss = true },
                )
            }
        }

        composeRule.onNodeWithText("版本 9.9 (99)").assertIsDisplayed()
        composeRule.onNodeWithText("关于本站").performScrollTo().performClick()
        composeRule.onNodeWithText("隐私协议和服务条款").performScrollTo().performClick()
        composeRule.onNodeWithText("电报频道").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("复制 RSS 地址").performScrollTo().performClick()

        assertTrue(openedAbout)
        assertTrue(openedPrivacy)
        assertTrue(copiedRss)
        assertEquals(listOf(CommunityLinks.TELEGRAM_CHANNEL), openedUris)
    }

    @Test
    fun `licenses row opens the license screen`() {
        var opened = false
        composeRule.setContent {
            NodeSeekTheme {
                AboutCommunityScreen(
                    versionName = "1.0",
                    versionCode = 1,
                    updateStatus = AppUpdateStatus.Available("1.1.0"),
                    onBack = {},
                    onCheckUpdates = {},
                    onOpenAboutSite = {},
                    onOpenPrivacy = {},
                    onOpenChangelog = {},
                    onOpenLicenses = { opened = true },
                    onOpenUri = {},
                    onCopyRss = {},
                )
            }
        }

        composeRule.onNodeWithText("开源许可").performScrollTo().performClick()

        assertTrue(opened)
    }

    @Test
    fun `friend sites are chips and telegram support is absent`() {
        composeRule.setContent {
            NodeSeekTheme {
                AboutCommunityScreen(
                    versionName = "1.0",
                    versionCode = 1,
                    updateStatus = AppUpdateStatus.Latest,
                    onBack = {},
                    onCheckUpdates = {},
                    onOpenAboutSite = {},
                    onOpenPrivacy = {},
                    onOpenChangelog = {},
                    onOpenLicenses = {},
                    onOpenUri = {},
                    onCopyRss = {},
                )
            }
        }

        composeRule.onNodeWithText("LowEndTalk").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("ServerHunter").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Telegram 客服").assertDoesNotExist()
    }
}
