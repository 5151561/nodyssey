package io.github.nodyssey.ui.common

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.nodyssey.core.LevelProgress
import io.github.nodyssey.core.NodeSeekSite
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The one caption 我的, 账户与成长 and 鸡腿流水 put under a level bar. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class LevelProgressLineTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the caption gives the count, the threshold and what is left to the next level`() {
        composeRule.setContent {
            PlazaTheme { LevelProgressLine(LevelProgress(chicken = 344, span = NodeSeekSite.levelChickenSpan(1))) }
        }

        composeRule.onNodeWithText("344 / 400 · 还差 56 升到 Lv2").assertIsDisplayed()
    }

    /** Above the site's Lv5 clamp the threshold is behind the count; there is nothing left to owe. */
    @Test
    fun `a reached threshold is stated without a remainder`() {
        composeRule.setContent {
            PlazaTheme { LevelProgressLine(LevelProgress(chicken = 4_000, span = NodeSeekSite.levelChickenSpan(6))) }
        }

        composeRule.onNodeWithText("4000 / 3600").assertIsDisplayed()
    }
}
