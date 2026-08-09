package io.github.nodyssey.ui.messages

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.nodyssey.data.composer.ImageAttachment
import io.github.nodyssey.data.composer.UploadStatus
import io.github.nodyssey.ui.composer.EditorActions
import io.github.plaza.designsys.editor.EditorAction
import io.github.plaza.designsys.editor.toolbarLayout
import io.github.plaza.designsys.theme.NodysseyTheme
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
 * The message bar's half of the shared editor (7f).
 *
 * The MD toggle is the whole contract here: with it off the server takes the text verbatim, so a
 * formatting key would insert syntax that arrives as literal asterisks. The strip is therefore absent
 * rather than disabled, and these tests are what keep it that way.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class MessageComposerTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var draftState: TextFieldState

    private fun setScreen(
        draft: String = "",
        markdown: Boolean = true,
    ) {
        composeRule.setContent {
            var isMarkdown by remember { mutableStateOf(markdown) }
            draftState = remember { TextFieldState(draft) }
            NodysseyTheme {
                MessageThreadScreen(
                    state =
                    MessageThreadUiState(
                        uid = 4471,
                        userName = "iwil",
                        isMarkdown = isMarkdown,
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
                    onRetryLoad = {},
                    onToggleMarkdown = { isMarkdown = !isMarkdown },
                    onSend = {},
                    onRetrySend = {},
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
    fun `the formatting strip is the same one the post editor uses`() {
        setScreen()

        composeRule.onNodeWithContentDescription("加粗").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("行内代码").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("链接").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("表情").assertIsDisplayed()
    }

    @Test
    fun `four keys at the full 48dp, and no preview`() {
        setScreen()

        composeRule.onNodeWithContentDescription("加粗").assertWidthIsEqualTo(48.dp)
        // A message renders into a bubble the moment it is sent; getting it wrong costs a second
        // message, not a deleted topic. The thread's top bar belongs to the conversation, not to
        // this draft, so there is nowhere for a preview to live and nothing much for it to do.
        composeRule.onNodeWithContentDescription("预览").assertDoesNotExist()
    }

    @Test
    fun `the strip is arrangeable here too`() {
        setScreen()

        composeRule.onNodeWithContentDescription("自定义工具栏").assertIsDisplayed()
    }

    @Test
    fun `the image key is on offer, because private messages carry images`() {
        setScreen()

        // `message/send` takes `content` as Markdown and the thread renders images out of it, so an
        // image here is the same NodeImage upload spliced into the same kind of string as a topic's.
        assertTrue(EditorAction.IMAGE in defaultLayout().available)
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

    private fun defaultLayout() = toolbarLayout(emptyList(), EditorActions.Message)

    @Test
    fun `turning MD off takes the strip away rather than disabling it`() {
        setScreen()

        composeRule.onNodeWithContentDescription("Markdown 开关").performClick()

        composeRule.onNodeWithContentDescription("加粗").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("表情").assertDoesNotExist()
    }

    @Test
    fun `a formatting key wraps the draft and selects what it wrapped`() {
        setScreen()

        composeRule.onNodeWithContentDescription("加粗").performClick()

        assertEquals("**加粗文字**", draftState.text.toString())
        // Selected, not merely inserted: the next keystroke has to replace the placeholder.
        assertEquals("加粗文字", draftState.text.substring(draftState.selection.min, draftState.selection.max))
    }

    @Test
    fun `the emoji panel opens on the message bar too`() {
        setScreen()

        composeRule.onNodeWithContentDescription("表情").performClick()

        composeRule.onNodeWithText("AC娘").assertIsDisplayed()
        composeRule.onNodeWithText("最近使用").assertIsDisplayed()
    }
}
