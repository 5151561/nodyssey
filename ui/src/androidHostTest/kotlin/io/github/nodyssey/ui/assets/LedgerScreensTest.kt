package io.github.nodyssey.ui.assets

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.paging.PagingData
import io.github.nodyssey.data.StardustEntry
import io.github.nodyssey.data.StardustType
import io.github.plaza.core.net.SiteError
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Board 8e, at the width it was drawn for.
 *
 * A regression guard with history — the screen shipped once with "点赞 · 评论被点赞" hardcoded into
 * every row, so a spend read as a gain — and the error state that has to win over Paging's initial
 * Loading.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class LedgerScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** The bug this screen shipped with once: every row read as a +1 from a liked comment. */
    @Test
    fun `an outgoing stardust transfer is not reported as a gain`() {
        setStardustContent()

        composeRule.onNodeWithText("−2").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("+2").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("评论被点赞", substring = true).fetchSemanticsNodes().size)
    }

    /**
     * Without a uid the ledger was never requested, so Paging is still in its initial Loading state.
     * The profile error has to win, or the screen spins forever on a sign-in problem.
     */
    @Test
    fun `a sign-in failure is shown rather than an endless spinner`() {
        setStardustContent(
            state = StardustUiState(isLoadingBalance = false, error = SiteError.LoginRequired),
            entries = emptyList(),
        )

        composeRule.onNodeWithText("需要登录后查看").assertIsDisplayed()
        // And it is the error state, not a spinner sitting behind it.
        assertEquals(0, composeRule.onAllNodesWithText("余额 ", substring = true).fetchSemanticsNodes().size)
    }

    private fun setStardustContent(
        state: StardustUiState = StardustUiState(isLoadingBalance = false, uid = 52_425, balance = 6),
        entries: List<StardustEntry> = stardustEntries,
    ) {
        composeRule.setContent {
            PlazaTheme {
                StardustScreen(
                    state = state,
                    entries = flowOf(PagingData.from(entries)),
                    snackbarHostState = remember { SnackbarHostState() },
                    amountState = TextFieldState(),
                    recipientState = TextFieldState(),
                    refState = TextFieldState(),
                    onBack = {},
                    onRetry = {},
                    onOpenBrowser = {},
                    onSignIn = {},
                    onVerify = {},
                    onOpenTransfer = {},
                    onDismissTransfer = {},
                    onRequestConfirm = {},
                    onDismissConfirm = {},
                    onConfirmTransfer = {},
                )
            }
        }
    }

    private val stardustEntries =
        listOf(
            StardustEntry(187_103, StardustType.UPVOTE, "upvote", 1, 6, 9_667, 11_491_930, 10, 1_785_064_901_000),
            StardustEntry(186_400, StardustType.TRANSFER, "transfer", -2, 3, 4_471, null, 108, 1_784_957_339_000),
            StardustEntry(186_100, StardustType.BUY_CODE, "buyCode", -1, 4, null, null, 10, 1_784_804_442_000),
            StardustEntry(157_160, StardustType.SYSTEM, "system", 3, 5, null, null, 10, 1_781_135_992_000),
            StardustEntry(157_149, StardustType.ADMIN, "admin", 1, 2, null, null, 10, 1_781_135_592_000),
        )
}
