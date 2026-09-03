package io.github.nodyssey.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 新手引导 — the four screens, and the two ways out of them.
 *
 * The screen this is really about is 单手模式: the guide exists because that band of blank keeps being
 * reported as a bug, so a test that lets it silently stop being reachable would let the bug reports
 * come back.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class OnboardingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `walks through four screens and finishes on the last`() {
        var finished = false
        composeRule.setContent {
            PlazaTheme {
                OnboardingScreen(onFinish = { finished = true }, appLinksEnabled = false)
            }
        }

        composeRule.onNodeWithText("欢迎使用 Nodyssey").assertIsDisplayed()

        composeRule.onNodeWithText("下一步").performClick()
        composeRule.onNodeWithText("单手模式").assertIsDisplayed()

        composeRule.onNodeWithText("下一步").performClick()
        composeRule.onNodeWithText("首页操作").assertIsDisplayed()

        composeRule.onNodeWithText("下一步").performClick()
        composeRule.onNodeWithText("编辑器工具栏").assertIsDisplayed()

        composeRule.onNodeWithText("下一步").performClick()
        composeRule.onNodeWithText("站内链接").assertIsDisplayed()

        // The button becomes the way out only on the last screen; up to here it has been paging.
        assertFalse(finished)
        composeRule.onNodeWithText("开始使用").performClick()
        assertTrue(finished)
    }

    /** 跳过 is on every screen, and means the same thing on all of them. */
    @Test
    fun `skipping from the first screen finishes the guide`() {
        var finished = false
        composeRule.setContent {
            PlazaTheme {
                OnboardingScreen(onFinish = { finished = true }, appLinksEnabled = false)
            }
        }

        composeRule.onNodeWithText("跳过").performClick()
        assertTrue(finished)
    }

    /**
     * Nothing to ask of a reader who has already thrown the switch — nor on a platform that has no
     * such switch, which is what null means. Both drop the screen, leaving 首页 as the last one.
     */
    @Test
    fun `站内链接 screen is dropped once the switch is already on`() {
        composeRule.setContent {
            PlazaTheme {
                OnboardingScreen(onFinish = {}, appLinksEnabled = true)
            }
        }

        composeRule.onNodeWithText("下一步").performClick()
        composeRule.onNodeWithText("下一步").performClick()
        composeRule.onNodeWithText("下一步").performClick()
        composeRule.onNodeWithText("编辑器工具栏").assertIsDisplayed()
        // Four screens, so this one is the last and the button has already changed.
        composeRule.onNodeWithText("开始使用").assertIsDisplayed()
    }

    @Test
    fun `站内链接 screen is dropped where the platform has no such notion`() {
        composeRule.setContent {
            PlazaTheme {
                OnboardingScreen(onFinish = {}, appLinksEnabled = null)
            }
        }

        composeRule.onNodeWithText("下一步").performClick()
        composeRule.onNodeWithText("下一步").performClick()
        composeRule.onNodeWithText("下一步").performClick()
        composeRule.onNodeWithText("开始使用").assertIsDisplayed()
    }

    /**
     * The button on the 站内链接 screen leads out to the system settings, and what comes back is a
     * changed `appLinksEnabled` — which the screen answers with the outcome in place of the button.
     */
    @Test
    fun `throwing the switch replaces the button with its outcome`() {
        var launches = 0
        composeRule.setContent {
            var enabled by remember { mutableStateOf(false) }
            PlazaTheme {
                OnboardingScreen(
                    onFinish = {},
                    appLinksEnabled = enabled,
                    onOpenAppLinkSettings = {
                        launches++
                        enabled = true
                    },
                )
            }
        }

        repeat(4) { composeRule.onNodeWithText("下一步").performClick() }

        composeRule.onNodeWithText("去开启").performClick()
        assertEquals(1, launches)
        composeRule.onNodeWithText("已开启").assertIsDisplayed()
    }
}
