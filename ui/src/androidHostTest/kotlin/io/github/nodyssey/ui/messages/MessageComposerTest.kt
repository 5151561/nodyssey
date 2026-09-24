package io.github.nodyssey.ui.messages

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.width
import io.github.nodyssey.data.composer.ImageAttachment
import io.github.nodyssey.data.composer.UploadStatus
import io.github.nodyssey.ui.assertEveryTouchTargetAtLeast48dp
import io.github.nodyssey.ui.composer.EditorActions
import io.github.plaza.designsys.editor.EditorAction
import io.github.plaza.designsys.editor.toolbarLayout
import io.github.plaza.designsys.theme.PlazaTheme
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
 * The message bar's half of the shared editor (7f, redrawn as 3d/3e with the keys in a grid behind +).
 *
 * The MD toggle is the whole contract here: with it off the server takes the text verbatim, so a
 * formatting key would insert syntax that arrives as literal asterisks. The keys are therefore absent
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
        fontScale: Float = 1f,
    ) {
        composeRule.setContent {
            var isMarkdown by remember { mutableStateOf(markdown) }
            draftState = remember { TextFieldState(draft) }
            val density = Density(LocalDensity.current.density, fontScale = fontScale)
            CompositionLocalProvider(LocalDensity provides density) {
                PlazaTheme {
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
                        onOpenSpace = {},
                        onRetryLoad = {},
                        onToggleMarkdown = { isMarkdown = !isMarkdown },
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
    }

    private fun openTools() {
        composeRule.onNodeWithContentDescription("附件与格式").performClick()
    }

    /**
     * The whole conversation screen, not one toolbar key: this was the repository's single 48dp
     * assertion, widened into the sweep the review asked for. The second run is the app's own
     * maximum font setting — bigger glyphs must grow the targets or wrap the layout, never shrink
     * what a finger can hit.
     */
    @Test
    fun `every touch target on the conversation screen holds 48dp`() {
        setScreen(draft = "写到一半")
        composeRule.assertEveryTouchTargetAtLeast48dp()
    }

    @Test
    fun `the largest font setting does not shrink any touch target below 48dp`() {
        setScreen(draft = "写到一半", fontScale = 1.5f)
        composeRule.assertEveryTouchTargetAtLeast48dp()
    }

    /**
     * At twice the text size a caption wraps inside its own quarter of the row. It used to be laid
     * out unbounded, and 「Markdown · 开」 ran over the captions either side of it.
     */
    @Test
    fun `a tool caption stays inside its own tile at 2x text`() {
        setScreen(fontScale = 2f)
        openTools()

        val caption = composeRule.onNodeWithText("Markdown · 开", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val screen = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertTrue("caption at $caption", caption.width <= screen.width / 4)
    }

    /** The grid's tiles are targets too, and they are only on screen once the + key is on. */
    @Test
    fun `every tile in the tool grid holds 48dp`() {
        setScreen()
        openTools()

        composeRule.assertEveryTouchTargetAtLeast48dp()
    }

    /**
     * 3e folds the strip behind the + key. The keys are still the arranged ones the post editor's
     * strip draws — the grid is a different drawing of the same toolbar, not a second list of keys.
     */
    @Test
    fun `the tool grid carries the same keys the post editor uses`() {
        setScreen()
        composeRule.onNodeWithText("加粗").assertDoesNotExist()

        openTools()

        composeRule.onNodeWithText("加粗").assertIsDisplayed()
        composeRule.onNodeWithText("行内代码").assertIsDisplayed()
        composeRule.onNodeWithText("链接").assertIsDisplayed()
        composeRule.onNodeWithText("表情").assertIsDisplayed()
    }

    @Test
    fun `the + key closes the grid again`() {
        setScreen()
        openTools()

        composeRule.onNodeWithContentDescription("附件与格式").performClick()

        composeRule.onNodeWithText("加粗").assertDoesNotExist()
    }

    @Test
    fun `no preview`() {
        setScreen()
        openTools()

        // A message renders into a bubble the moment it is sent; getting it wrong costs a second
        // message, not a deleted topic. The thread's top bar belongs to the conversation, not to
        // this draft, so there is nowhere for a preview to live and nothing much for it to do.
        composeRule.onNodeWithContentDescription("预览").assertDoesNotExist()
    }

    @Test
    fun `the keys are arrangeable here too`() {
        setScreen()
        openTools()

        composeRule.onNodeWithText("自定义").assertIsDisplayed()
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
    fun `turning MD off takes the formatting keys away rather than disabling them`() {
        setScreen()
        openTools()

        composeRule.onNodeWithContentDescription("Markdown 开关").performClick()

        composeRule.onNodeWithText("加粗").assertDoesNotExist()
        composeRule.onNodeWithText("表情").assertDoesNotExist()
        // The switch itself stays, or there would be no way to turn it back on.
        composeRule.onNodeWithText("Markdown · 关").assertIsDisplayed()
    }

    @Test
    fun `a formatting key wraps the draft and selects what it wrapped`() {
        setScreen()
        openTools()

        composeRule.onNodeWithText("加粗").performClick()

        assertEquals("**加粗文字**", draftState.text.toString())
        // Selected, not merely inserted: the next keystroke has to replace the placeholder.
        assertEquals("加粗文字", draftState.text.substring(draftState.selection.min, draftState.selection.max))
        // And the grid gives the keyboard its place back, for that keystroke.
        composeRule.onNodeWithText("行内代码").assertDoesNotExist()
    }

    @Test
    fun `the emoji panel opens on the message bar too`() {
        setScreen()
        openTools()

        composeRule.onNodeWithText("表情").performClick()

        composeRule.onNodeWithText("AC娘").assertIsDisplayed()
        composeRule.onNodeWithText("最近使用").assertIsDisplayed()
    }
}
