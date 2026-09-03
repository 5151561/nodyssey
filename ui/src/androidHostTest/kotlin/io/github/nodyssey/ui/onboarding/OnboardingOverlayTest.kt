package io.github.nodyssey.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The guide is drawn *over* a live app — see `NodysseyRoot` — and this is the test for that fact.
 *
 * `OnboardingScreenTest` next door renders the guide on its own, which is the arrangement in which
 * every one of its buttons works no matter what the guide does with pointer events. On a device
 * there is a whole navigation host underneath, and the first shipped attempt at keeping taps from
 * reaching it consumed every event on the way past — the buttons stopped responding and so did the
 * swipe, while the tests stayed green. Hence a second file: same screen, realistic stacking.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h880dp")
class OnboardingOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** The screen underneath, standing in for the feed: one big target that records being hit. */
    private var tapsUnderneath = 0

    @Test
    fun `the buttons work while the guide is drawn over another screen`() {
        var finished = false
        composeRule.setContent {
            PlazaTheme {
                Box {
                    Underneath()
                    OnboardingScreen(onFinish = { finished = true }, appLinksEnabled = false)
                }
            }
        }

        composeRule.onNodeWithText("下一步").performClick()
        composeRule.onNodeWithText("单手模式").assertIsDisplayed()

        composeRule.onNodeWithText("下一步").performClick()
        composeRule.onNodeWithText("首页操作").assertIsDisplayed()

        composeRule.onNodeWithText("跳过").performClick()
        assertEquals(true, finished)
    }

    /**
     * Nothing reaches the screen underneath — the reason the guide has to touch pointer handling at
     * all. A tap on the empty half of a guide screen would otherwise open whatever feed row it
     * happens to land on, and the reader would be somewhere else with the guide still on top.
     */
    @Test
    fun `taps do not reach the screen underneath`() {
        composeRule.setContent {
            PlazaTheme {
                Box {
                    Underneath()
                    OnboardingScreen(onFinish = {}, appLinksEnabled = false)
                }
            }
        }

        // The middle of the first screen: the figure's own area, which carries no control of its own
        // and is exactly where a stray tap would fall through.
        composeRule.onNodeWithText("欢迎使用 Nodyssey").performClick()

        assertEquals(0, tapsUnderneath)
    }

    /**
     * The swipe, which is what a reader actually does with a pager and the half of the report that a
     * plain `performClick` cannot reach: a click is one down and one up, while a drag is a stream of
     * moves — and a handler that eats those leaves the buttons looking fine and the pager dead.
     */
    @Test
    fun `swiping pages the guide while it is drawn over another screen`() {
        composeRule.setContent {
            PlazaTheme {
                Box {
                    Underneath()
                    OnboardingScreen(onFinish = {}, appLinksEnabled = false)
                }
            }
        }

        composeRule.onNodeWithText("欢迎使用 Nodyssey").assertIsDisplayed()

        composeRule.onRoot().performTouchInput {
            swipeLeft(startX = right - 1f, endX = left + 1f)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("单手模式").assertIsDisplayed()
    }

    /** A tap with a little travel in it, which is what a finger actually delivers. */
    @Test
    fun `a tap that moves slightly still presses the button`() {
        var finished = false
        composeRule.setContent {
            PlazaTheme {
                Box {
                    Underneath()
                    OnboardingScreen(onFinish = { finished = true }, appLinksEnabled = false)
                }
            }
        }

        composeRule.onNodeWithText("跳过").performTouchInput {
            down(center)
            moveBy(Offset(2f, 2f))
            up()
        }

        assertEquals(true, finished)
    }

    @androidx.compose.runtime.Composable
    private fun Underneath() {
        Box(
            Modifier
                .fillMaxSize()
                .clickable { tapsUnderneath++ },
        ) {
            Text("底下的屏幕")
        }
    }
}
