package io.github.plaza.designsys.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
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
 * The 格式 card's contract (boards 1e / 2c): it opens in the bar's place, formats without closing,
 * and gives way to the emoji panel rather than stacking with it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class ComposerEditorBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val bodyState = TextFieldState()

    private fun setBar() {
        composeRule.setContent {
            PlazaTheme {
                ComposerEditorBar(
                    actions = listOf(EditorAction.IMAGE, EditorAction.EMOJI, EditorAction.MENTION),
                    bodyState = bodyState,
                    editorState = rememberMarkdownEditorState(),
                    onCustomize = {},
                    emojiPanel = { Text("面板") },
                )
            }
        }
    }

    @Test
    fun `a key on the card formats and the card stays open for the next one`() {
        setBar()

        composeRule.onNodeWithText("格式").performClick()
        composeRule.onNodeWithContentDescription("加粗").performClick()

        assertEquals("**加粗文字**", bodyState.text.toString())
        composeRule.onNodeWithContentDescription("删除线").assertIsDisplayed()
    }

    @Test
    fun `收起 puts the bar back`() {
        setBar()

        composeRule.onNodeWithText("格式").performClick()
        composeRule.onNodeWithText("收起").performClick()

        composeRule.onNodeWithContentDescription("加粗").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("表情").assertIsDisplayed()
    }

    @Test
    fun `opening the card puts the emoji panel away`() {
        setBar()

        composeRule.onNodeWithContentDescription("表情").performClick()
        composeRule.onNodeWithText("面板").assertIsDisplayed()
        composeRule.onNodeWithText("格式").performClick()

        composeRule.onNodeWithText("面板").assertDoesNotExist()
    }
}
