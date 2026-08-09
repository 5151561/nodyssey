package io.github.nodyssey.ui.tools

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.plaza.designsys.theme.NodysseyTheme
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
class CommunityToolsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `community about replaces app about entry`() {
        var opened = false
        composeRule.setContent {
            NodysseyTheme {
                CommunityToolsScreen(
                    onBack = {},
                    onAward = {},
                    onProviders = {},
                    onFriends = {},
                    onLucky = {},
                    onInvite = {},
                    onRuling = {},
                    onAboutCommunity = { opened = true },
                )
            }
        }

        composeRule.onNodeWithText("关于 · 社区").performScrollTo().performClick()

        assertTrue(opened)
        composeRule.onNodeWithText("关于 Nodyssey").assertDoesNotExist()
    }
}
