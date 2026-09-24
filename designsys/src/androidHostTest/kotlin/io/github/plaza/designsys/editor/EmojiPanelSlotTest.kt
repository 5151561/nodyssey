package io.github.plaza.designsys.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/** The emoji panel gives way to a formatting key rather than staying open over the rewritten text. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class EmojiPanelSlotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val bodyState = TextFieldState()

    private fun setBar(panel: @Composable (EmojiPanelScope) -> Unit) {
        composeRule.setContent {
            PlazaTheme {
                ComposerEditorBar(
                    actions = listOf(EditorAction.BOLD, EditorAction.EMOJI),
                    bodyState = bodyState,
                    editorState = rememberMarkdownEditorState(),
                    emojiPanel = panel,
                )
            }
        }
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
