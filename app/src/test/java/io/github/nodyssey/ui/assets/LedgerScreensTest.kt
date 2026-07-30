package io.github.nodyssey.ui.assets

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.paging.PagingData
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.data.CreditEntry
import io.github.nodyssey.data.StardustEntry
import io.github.nodyssey.data.StardustType
import io.github.nodyssey.ui.theme.NodysseyTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Boards d3 and 8e, at the width they were drawn for.
 *
 * The assertions people would otherwise have to re-check by eye every time these rows are touched:
 * that a spend renders with a sign instead of as a bare magnitude, and that a stardust row names
 * *which* of the five kinds it is. The second one is a regression guard with history — the screen
 * shipped once with "点赞 · 评论被点赞" hardcoded into every row.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class LedgerScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `chicken row shows the amount, the site's own reason, the total and the time`() {
        setCreditContent(CreditUiState(level = 1, chickenCount = 384, nextLevelChicken = 400))

        composeRule.onNodeWithText("+1").assertIsDisplayed()
        composeRule.onNodeWithText("回帖奖励").assertIsDisplayed()
        composeRule.onNodeWithText("总计 384", substring = true).assertIsDisplayed()
    }

    /** U+2212, not a hyphen, and not an unsigned 1 in a red pill. */
    @Test
    fun `a spend keeps its minus sign`() {
        setCreditContent(CreditUiState(chickenCount = 384))

        composeRule.onNodeWithText("−1").assertIsDisplayed()
        composeRule.onNodeWithText("投喂鸡腿").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("-1").fetchSemanticsNodes().size)
    }

    /** Levelling *is* the chicken count, so the header states the progress rather than a second number. */
    @Test
    fun `the header doubles as Lv1 progress`() {
        setCreditContent(CreditUiState(level = 1, chickenCount = 384, nextLevelChicken = 400))

        composeRule.onNodeWithText("当前鸡腿").assertIsDisplayed()
        composeRule.onNodeWithText("Lv1 进度 384 / 400").assertIsDisplayed()
    }

    /** Above Lv1 no threshold has ever been published, so the progress half disappears. */
    @Test
    fun `above Lv1 the header shows the level without inventing a threshold`() {
        setCreditContent(CreditUiState(level = 2, chickenCount = 1_240))

        composeRule.onNodeWithText("Lv2").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("/ 400", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `stardust rows name all five kinds the site has`() {
        setStardustContent()

        composeRule.onNodeWithText("点赞").assertIsDisplayed()
        composeRule.onNodeWithText("转账").assertIsDisplayed()
        composeRule.onNodeWithText("购买邀请码").assertIsDisplayed()
        composeRule.onNodeWithText("系统").assertIsDisplayed()
        composeRule.onNodeWithText("管理").assertIsDisplayed()
    }

    /** The bug this screen shipped with once: every row read as a +1 from a liked comment. */
    @Test
    fun `an outgoing stardust transfer is not reported as a gain`() {
        setStardustContent()

        composeRule.onNodeWithText("−2").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("+2").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("评论被点赞", substring = true).fetchSemanticsNodes().size)
    }

    /** The comment id belongs on an upvote row; on a transfer the other party and Ref ID do. */
    @Test
    fun `each kind carries only the identifier that means something for it`() {
        setStardustContent()

        composeRule.onNodeWithText("评论 11491930", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Ref 108", substring = true).assertIsDisplayed()
        // ref_id is a constant 10 on upvote rows, so it is deliberately absent there.
        assertEquals(0, composeRule.onAllNodesWithText("Ref 10 ", substring = true).fetchSemanticsNodes().size)
    }

    /**
     * Without a uid the ledger was never requested, so Paging is still in its initial Loading state.
     * The profile error has to win, or the screen spins forever on a sign-in problem.
     */
    @Test
    fun `a sign-in failure is shown rather than an endless spinner`() {
        setStardustContent(
            state = StardustUiState(isLoadingBalance = false, error = NodeSeekError.LoginRequired),
            entries = emptyList(),
        )

        composeRule.onNodeWithText("需要登录后查看").assertIsDisplayed()
        // And it is the error state, not a spinner sitting behind it.
        assertEquals(0, composeRule.onAllNodesWithText("余额 ", substring = true).fetchSemanticsNodes().size)
    }

    private fun setCreditContent(state: CreditUiState) {
        composeRule.setContent {
            NodysseyTheme {
                CreditScreen(
                    state = state,
                    entries = flowOf(PagingData.from(creditEntries)),
                    onBack = {},
                    onRetry = {},
                    onOpenBrowser = {},
                    onSignIn = {},
                )
            }
        }
    }

    private fun setStardustContent(
        state: StardustUiState = StardustUiState(isLoadingBalance = false, uid = 52_425, balance = 6),
        entries: List<StardustEntry> = stardustEntries,
    ) {
        composeRule.setContent {
            NodysseyTheme {
                StardustScreen(
                    state = state,
                    entries = flowOf(PagingData.from(entries)),
                    amountState = TextFieldState(),
                    recipientState = TextFieldState(),
                    refState = TextFieldState(),
                    onBack = {},
                    onRetry = {},
                    onOpenBrowser = {},
                    onSignIn = {},
                    onOpenTransfer = {},
                    onDismissTransfer = {},
                    onRequestConfirm = {},
                    onDismissConfirm = {},
                    onConfirmTransfer = {},
                )
            }
        }
    }

    private val creditEntries =
        listOf(
            CreditEntry(1, 384, "回帖奖励", 1_785_414_491_000),
            CreditEntry(-1, 350, "投喂鸡腿", 1_785_063_220_000),
        )

    private val stardustEntries =
        listOf(
            StardustEntry(187_103, StardustType.UPVOTE, "upvote", 1, 6, 9_667, 11_491_930, 10, 1_785_064_901_000),
            StardustEntry(186_400, StardustType.TRANSFER, "transfer", -2, 3, 4_471, null, 108, 1_784_957_339_000),
            StardustEntry(186_100, StardustType.BUY_CODE, "buyCode", -1, 4, null, null, 10, 1_784_804_442_000),
            StardustEntry(157_160, StardustType.SYSTEM, "system", 3, 5, null, null, 10, 1_781_135_992_000),
            StardustEntry(157_149, StardustType.ADMIN, "admin", 1, 2, null, null, 10, 1_781_135_592_000),
        )
}
