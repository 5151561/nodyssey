package io.github.nodyssey.ui.messages

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * A conversation with nothing in it yet — which is every conversation opened from someone's space.
 *
 * The one thing worth a test here is that the line is actually in the middle of the screen. It used
 * to be centred inside a box the width of the line itself, which put it against the left edge and
 * looks like a layout accident rather than an empty state.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class MessageThreadEmptyStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setScreen() {
        composeRule.setContent {
            PlazaTheme {
                MessageThreadScreen(
                    state = MessageThreadUiState(uid = 4471, userName = "iwil"),
                    draftState = TextFieldState(),
                    onBack = {},
                    onSignIn = {},
                    onVerify = {},
                    onOpenBrowser = {},
                    onOpenSpace = {},
                    onRetryLoad = {},
                    onToggleMarkdown = {},
                    onSend = {},
                    onRetrySend = {},
                    onQuote = {},
                    onPickImages = {},
                    onRemoveAttachment = {},
                    onRetryAttachment = {},
                    onToolbarChange = {},
                    onToolbarReset = {},
                )
            }
        }
    }

    @Test
    fun `the empty line sits in the middle of the screen, not against its left edge`() {
        setScreen()

        val text = composeRule.onNodeWithText("还没有消息，说点什么吧")
        text.assertIsDisplayed()

        val bounds = text.getUnclippedBoundsInRoot()
        val screenWidth = composeRule.onRoot().getUnclippedBoundsInRoot().width
        val leftGap = bounds.left
        val rightGap = screenWidth - bounds.right

        assertTrue(
            "expected equal margins, got ${leftGap.value}dp left and ${rightGap.value}dp right",
            abs((leftGap - rightGap).value) <= 1f,
        )
        // And a real margin on both sides rather than a line that happens to fill the width.
        assertTrue("expected the line to be narrower than the screen", leftGap > 0.dp)
    }
}
