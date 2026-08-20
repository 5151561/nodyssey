package io.github.nodyssey.ui.messages

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The title block of a conversation is the way to the other side's space.
 *
 * A conversation is often all one knows of a stranger, and the avatar at the top is the only thing
 * on the screen that names them — everywhere else in the app it opens their space, and here it did
 * nothing at all. The test clicks the name rather than the avatar because the avatar draws no text
 * to find it by; both are inside the one clickable, which is the point.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class MessageThreadTitleTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `tapping the title opens the other side's space`() {
        var opened = 0
        composeRule.setContent {
            PlazaTheme {
                MessageThreadScreen(
                    state = MessageThreadUiState(uid = 4471, userName = "iwil"),
                    draftState = TextFieldState(),
                    onBack = {},
                    onSignIn = {},
                    onVerify = {},
                    onOpenBrowser = {},
                    onOpenSpace = { opened++ },
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

        composeRule.onNodeWithText("与 iwil 的对话").performClick()

        assertEquals(1, opened)
    }

    /** The UID line is inside the same target: half the block is not a target at all. */
    @Test
    fun `tapping the uid line opens it too`() {
        var opened = 0
        composeRule.setContent {
            PlazaTheme {
                MessageThreadScreen(
                    state = MessageThreadUiState(uid = 4471, userName = "iwil"),
                    draftState = TextFieldState(),
                    onBack = {},
                    onSignIn = {},
                    onVerify = {},
                    onOpenBrowser = {},
                    onOpenSpace = { opened++ },
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

        composeRule.onNodeWithText("UID 4471").performClick()

        assertEquals(1, opened)
    }
}
