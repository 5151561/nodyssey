package io.github.plaza.designsys.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * The emoji key's contract with whatever panel a host supplies.
 *
 * The strip used to name one particular app's panel outright. Now it takes a slot, and a slot that
 * is never invoked fails silently — the key lights up and nothing appears, which is exactly the
 * regression these cover. The insert and backspace wiring is tested here too, because it is the part
 * the host cannot get right on its own: it has no reference to the buffer being edited.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class MarkdownEditorBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val bodyState = TextFieldState()

    private fun setBar(panel: @Composable (EmojiPanelScope) -> Unit) {
        composeRule.setContent {
            PlazaTheme {
                MarkdownEditorBar(
                    actions = listOf(EditorAction.BOLD, EditorAction.EMOJI),
                    bodyState = bodyState,
                    editorState = rememberMarkdownEditorState(),
                    emojiPanel = panel,
                )
            }
        }
    }

    @Test
    fun `the emoji key shows whatever panel the host supplied`() {
        setBar { Text("面板") }

        composeRule.onNodeWithContentDescription("表情").performClick()

        composeRule.onNodeWithText("面板").assertIsDisplayed()
    }

    @Test
    fun `tapping the emoji key again puts the panel away`() {
        setBar { Text("面板") }

        composeRule.onNodeWithContentDescription("表情").performClick()
        composeRule.onNodeWithContentDescription("表情").performClick()

        composeRule.onNodeWithText("面板").assertDoesNotExist()
    }

    @Test
    fun `the panel inserts at the caret rather than at the end`() {
        bodyState.edit { insertText("ab") }
        setBar { scope -> Text("面板", modifier = Modifier.clickable { scope.onInsert(" :x: ") }) }

        composeRule.onNodeWithContentDescription("表情").performClick()
        composeRule.onNodeWithText("面板").performClick()

        assertEquals("ab :x: ", bodyState.text.toString())
    }

    @Test
    fun `a formatting key closes the panel before it rewrites the text`() {
        setBar { Text("面板") }

        composeRule.onNodeWithContentDescription("表情").performClick()
        composeRule.onNodeWithContentDescription("加粗").performClick()

        composeRule.onNodeWithText("面板").assertDoesNotExist()
        assertEquals("**加粗文字**", bodyState.text.toString())
    }
}
