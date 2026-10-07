package io.github.plaza.designsys.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import io.github.plaza.designsys.component.EditorTextField
import io.github.plaza.designsys.theme.CommentBody
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * A sticker drawn in the text is one character to the person editing it: one backspace takes all of
 * its Markdown, from the keyboard and from the emoji panel alike. Rests on how the text field maps an
 * edit to a run its output transformation replaced, which is the platform's and not ours.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InlinePicturesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val sticker = "![doge](https://cdn.example/doge.gif)"
    private val pictures = InlinePictures(
        find = { text ->
            val at = text.indexOf(sticker)
            if (at < 0) emptyList() else listOf(InlinePicture(at, at + sticker.length, "u"))
        },
        image = { _, _ -> },
    )

    @Test
    fun `the keyboard's backspace deletes the whole sticker`() {
        val state = TextFieldState("好 $sticker").apply { edit { placeCursorAtEnd() } }
        composeRule.setContent {
            PlazaTheme {
                EditorTextField(
                    state = state,
                    hint = "",
                    textStyle = CommentBody,
                    inlinePictures = pictures,
                    modifier = Modifier.testTag("field"),
                )
            }
        }
        composeRule.onNodeWithTag("field").performClick()
        composeRule.runOnIdle { state.edit { placeCursorAtEnd() } }

        composeRule.onNodeWithTag("field").performKeyInput { pressKey(Key.Backspace) }

        composeRule.runOnIdle { assertEquals("好 ", state.text.toString()) }
    }

    @Test
    fun `the panel's backspace deletes the whole sticker and then single characters`() {
        val state = TextFieldState("好 $sticker").apply { edit { placeCursorAtEnd() } }
        val scope = MarkdownEditorState().emojiPanelScope(state, pictures)

        scope.onBackspace()
        assertEquals("好 ", state.text.toString())
        scope.onBackspace()
        assertEquals("好", state.text.toString())
    }

    @Test
    fun `each placeholder sits where the text before it has shrunk to`() {
        val pictures = listOf(InlinePicture(2, 10, "a"), InlinePicture(12, 20, "b"))

        // The first eight characters become one, so the second run's twelve becomes five.
        assertEquals(listOf(2, 5), displayedOffsets(pictures))
    }
}
