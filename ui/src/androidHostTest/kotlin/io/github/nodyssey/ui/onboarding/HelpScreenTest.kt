package io.github.nodyssey.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 使用帮助 — that all three groups are reachable, and that the one control on the page works.
 *
 * The individual paragraphs are not asserted one by one: they are prose, they will be edited, and a
 * test that pins every sentence would be a test that fails on every wording change without ever
 * catching a defect. What is worth pinning is that each group is drawn at all, and that 再看一次引导
 * reports back — that last one being the only way anybody sees the guide twice.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class HelpScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `draws all three groups`() {
        composeRule.setContent {
            PlazaTheme { HelpScreen(onBack = {}, onReplayGuide = {}) }
        }

        composeRule.onNodeWithText("看起来像 bug 的").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("首页与列表").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("发帖与账号").performScrollTo().assertIsDisplayed()
    }

    /** The 单手模式 entry is the reason this page exists; it must survive a regrouping. */
    @Test
    fun `explains the blank band under a title`() {
        composeRule.setContent {
            PlazaTheme { HelpScreen(onBack = {}, onReplayGuide = {}) }
        }

        composeRule.onNodeWithText("标题栏下面空了一大块").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `replaying the guide reports back`() {
        var replays = 0
        composeRule.setContent {
            PlazaTheme { HelpScreen(onBack = {}, onReplayGuide = { replays++ }) }
        }

        composeRule.onNodeWithText("再看一次新手引导").performScrollTo().performClick()
        assertEquals(1, replays)
    }

    /**
     * The 站内链接 entry carries the jump into the system settings, but only while there is a switch
     * to throw: already-on says so instead, and a platform with no such notion offers neither.
     */
    @Test
    fun `站内链接 entry offers the jump only while the switch is off`() {
        var launches = 0
        composeRule.setContent {
            PlazaTheme {
                HelpScreen(
                    onBack = {},
                    onReplayGuide = {},
                    appLinksEnabled = false,
                    onOpenAppLinkSettings = { launches++ },
                )
            }
        }

        composeRule.onNodeWithText("去开启").performScrollTo().performClick()
        assertEquals(1, launches)
    }

    @Test
    fun `站内链接 entry has no button where the platform has no switch`() {
        composeRule.setContent {
            PlazaTheme { HelpScreen(onBack = {}, onReplayGuide = {}, appLinksEnabled = null) }
        }

        composeRule.onNodeWithText("站内链接被浏览器抢走").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("去开启").assertDoesNotExist()
    }
}
