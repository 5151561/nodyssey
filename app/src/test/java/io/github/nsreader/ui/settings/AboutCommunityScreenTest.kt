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
                    statsState = CommunityStatsUiState.Content(70_123),
                    onBack = {},
                    onOpenAboutSite = { openedAbout = true },
                    onOpenPrivacy = { openedPrivacy = true },
                    onOpenUri = openedUris::add,
                    onCopyRss = { copiedRss = true },
                    onRetryStats = {},
                )
            }
        }
        composeRule.onNodeWithText("关于本站").performScrollTo().performClick()
        composeRule.onNodeWithText("隐私协议和服务条款").performScrollTo().performClick()
        composeRule.onNodeWithText("电报频道").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("复制 RSS 地址").performScrollTo().performClick()
        composeRule.onNodeWithText("70,123 位 seeker").assertIsDisplayed()

        assertTrue(openedAbout)
        assertTrue(openedPrivacy)
        assertTrue(copiedRss)
        assertEquals(listOf(CommunityLinks.TELEGRAM_CHANNEL), openedUris)
    }

    @Test
    fun `friend sites are chips and telegram support is absent`() {
        composeRule.setContent {
            NodeSeekTheme {
                AboutCommunityScreen(
                    statsState = CommunityStatsUiState.Content(70_123),
                    onBack = {},
                    onOpenAboutSite = {},
                    onOpenPrivacy = {},
                    onOpenUri = {},
                    onCopyRss = {},
                    onRetryStats = {},
                )
            }
        }

        composeRule.onNodeWithText("LowEndTalk").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("ServerHunter").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Telegram 客服").assertDoesNotExist()
    }

    @Test
    fun `app information is absent from the community page`() {
        composeRule.setContent {
            NodeSeekTheme {
                AboutCommunityScreen(
                    statsState = CommunityStatsUiState.Loading,
                    onBack = {},
                    onOpenAboutSite = {},
                    onOpenPrivacy = {},
                    onOpenUri = {},
                    onCopyRss = {},
                    onRetryStats = {},
                )
            }
        }

        composeRule.onNodeWithText("关于 · 社区").assertIsDisplayed()
        composeRule.onNodeWithText("NodeSeek 客户端").assertDoesNotExist()
        composeRule.onNodeWithText("项目主页").assertDoesNotExist()
        composeRule.onNodeWithText("开源许可").assertDoesNotExist()
    }

    @Test
    fun `failed stats expose retry without a stale count`() {
        var retried = false
        composeRule.setContent {
            NodeSeekTheme {
                AboutCommunityScreen(
                    statsState = CommunityStatsUiState.Error,
                    onBack = {},
                    onOpenAboutSite = {},
                    onOpenPrivacy = {},
                    onOpenUri = {},
                    onCopyRss = {},
                    onRetryStats = { retried = true },
                )
            }
        }

        composeRule.onNodeWithText("论坛人数加载失败").assertIsDisplayed()
        composeRule.onNodeWithText("64,902 位 seeker").assertDoesNotExist()
        composeRule.onNodeWithText("重试").performClick()

        assertTrue(retried)
    }
}
