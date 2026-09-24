package io.github.nodyssey.render

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.nodyssey.ui.settings.AboutCommunityScreen
import io.github.nodyssey.ui.settings.CommunityStatsUiState
import io.github.nodyssey.ui.tools.CommunityToolsScreen
import io.github.nodyssey.ui.tools.InviteScreen
import io.github.nodyssey.ui.tools.LuckyScreen
import io.github.nodyssey.ui.tools.LuckyUiState
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 工具 in both themes — the community-tools grid — and three of the pages it opens: 幸运抽奖, 邀请好友 and 关于社区. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class CommunityToolsScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun Screen(darkTheme: Boolean) {
        PlazaTheme(darkTheme = darkTheme) {
            CommunityToolsScreen(
                onBack = {},
                onAward = {},
                onProviders = {},
                onFriends = {},
                onLucky = {},
                onInvite = {},
                onRuling = {},
                onAboutCommunity = {},
            )
        }
    }

    @Test
    fun `the tools grid in light`() {
        composeRule.setContent { Screen(darkTheme = false) }

        composeRule.onRoot().captureRender("tools-light")
    }

    @Test
    fun `the tools grid in dark`() {
        composeRule.setContent { Screen(darkTheme = true) }

        composeRule.onRoot().captureRender("tools-dark")
    }

    /** 幸运抽奖 with a link generated, so both of its buttons are on screen. */
    @Test
    @Config(qualifiers = "w360dp-h1100dp")
    fun `lucky draw in light`() {
        composeRule.setContent {
            PlazaTheme(darkTheme = false) {
                LuckyScreen(
                    state =
                    LuckyUiState(
                        drawAtMillis = 1_785_000_000_000L,
                        generatedLink = "https://www.nodeseek.com/lucky?post=123456&count=1&start=1",
                        canGenerate = true,
                    ),
                    postIdState = rememberTextFieldState("123456"),
                    prizeCountState = rememberTextFieldState("1"),
                    startFloorState = rememberTextFieldState("1"),
                    onBack = {},
                    onDrawAtChange = {},
                    onDedupeChange = {},
                    onGenerate = {},
                    onOpenBrowser = {},
                )
            }
        }

        composeRule.onRoot().captureRender("lucky-light")
    }

    @Test
    fun `invite in light`() {
        composeRule.setContent {
            PlazaTheme(darkTheme = false) {
                InviteScreen(chickenCount = 1_240, onBack = {}, onBuy = {})
            }
        }

        composeRule.onRoot().captureRender("invite-light")
    }

    @Test
    @Config(qualifiers = "w360dp-h1000dp")
    fun `about the community in light`() = aboutCommunity(darkTheme = false)

    @Test
    @Config(qualifiers = "w360dp-h1000dp")
    fun `about the community in dark`() = aboutCommunity(darkTheme = true)

    private fun aboutCommunity(darkTheme: Boolean) {
        composeRule.setContent {
            PlazaTheme(darkTheme = darkTheme) {
                AboutCommunityScreen(
                    onBack = {},
                    onOpenAboutSite = {},
                    onOpenPrivacy = {},
                    onOpenUri = {},
                    onCopyRss = {},
                    statsState = CommunityStatsUiState.Content(memberCount = 81_234),
                )
            }
        }

        composeRule.onRoot().captureRender("about-community-${if (darkTheme) "dark" else "light"}")
    }
}
