package io.github.nodyssey.ui.messages

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.nodyssey.data.composer.ImageAttachment
import io.github.nodyssey.data.composer.UploadStatus
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The message bar's half of the shared editor (7f, redrawn as 3d/3e with the keys in a grid behind +). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class MessageComposerTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setScreen(draft: String = "") {
        composeRule.setContent {
            val draftState = remember { TextFieldState(draft) }
            PlazaTheme {
                MessageThreadScreen(
                    state =
                    MessageThreadUiState(
                        uid = 4471,
                        userName = "iwil",
                        hasDraftText = draft.isNotBlank(),
                        messages =
                        listOf(
                            MessageBubble(
                                id = "1",
                                isMine = false,
                                content = "在的",
                                isMarkdown = true,
                                sentAtMillis = 1_785_000_000_000L,
                                sentAtText = null,
                                status = SendStatus.SENT,
                            ),
                        ),
                    ),
                    draftState = draftState,
                    onBack = {},
                    onSignIn = {},
                    onVerify = {},
                    onOpenBrowser = {},
                    onOpenSpace = {},
                    onRetryLoad = {},
                    onSend = {},
                    onRetrySend = {},
                    onQuote = {},
                    onRemoveQuote = {},
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
    fun `an upload still in flight holds the send key`() {
        setScreen(draft = "看这个")
        composeRule.waitForIdle()

        // Sending now would send `![](…)` with a URL the upload has not produced yet.
        assertFalse(
            MessageThreadUiState(
                uid = 1,
                userName = "iwil",
                hasDraftText = true,
                attachments = listOf(
                    ImageAttachment("1", "content://a", "a.png", UploadStatus.UPLOADING),
                ),
            ).canSend,
        )
    }
}
