package io.github.plaza.designsys.editor

import android.view.View
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
 * and gives way to the emoji panel rather than stacking with it — and to the keyboard likewise.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class ComposerEditorBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val bodyState = TextFieldState()

    private val keyboard =
        object : SoftwareKeyboardController {
            var hides = 0

            override fun show() = Unit

            override fun hide() {
                hides++
            }
        }

    private lateinit var view: View

    private fun setBar() {
        composeRule.setContent {
            view = LocalView.current
            PlazaTheme {
                CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
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
    }

    /** What the window hands the view when a keyboard [height] tall comes up, or goes away at 0. */
    private fun keyboardInsets(height: Int) =
        WindowInsetsCompat
            .Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, height))
            .setVisible(WindowInsetsCompat.Type.ime(), height > 0)
            .build()

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

    @Test
    fun `opening the card puts the keyboard away`() {
        setBar()

        composeRule.onNodeWithText("格式").performClick()

        assertEquals(1, keyboard.hides)
    }

    /** The writer tapped back into the text: the keyboard takes its place again rather than stacking. */
    @Test
    fun `the keyboard coming back puts the card away`() {
        setBar()
        composeRule.onNodeWithText("格式").performClick()
        composeRule.onNodeWithContentDescription("加粗").assertIsDisplayed()

        composeRule.runOnIdle { ViewCompat.dispatchApplyWindowInsets(view, keyboardInsets(800)) }

        composeRule.onNodeWithContentDescription("加粗").assertDoesNotExist()
        composeRule.onNodeWithText("格式").assertIsDisplayed()
    }
}
