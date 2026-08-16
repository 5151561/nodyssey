package io.github.plaza.designsys.richtext

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 折叠 is only worth having as a node if it actually hides something.
 *
 * The bug it replaced put the summary and everything under it on screen at once, so "the content is
 * not drawn until the summary is tapped" is the whole behaviour, and the slots have to reach inside
 * it for the same reason [RichContentSlotsTest] exists — a fold is exactly where a long report goes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class FoldBlockTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun fold(open: Boolean = false, title: String = "TCP 调优前") =
        RichNode.Fold(
            title = title,
            open = open,
            children =
            listOf(
                RichNode.Paragraph(listOf(InlineNode.Text("折叠里的正文"))),
                RichNode.CodeBlock(code = "uname -a", language = "sh"),
            ),
        )

    private fun setContent(node: RichNode) {
        composeRule.setContent {
            PlazaTheme {
                RichContent(
                    nodes = listOf(node),
                    onLinkClick = {},
                    onImageClick = {},
                    codeBlockContent = { Text("host code ${it.language}") },
                )
            }
        }
    }

    @Test
    fun `a fold shows its summary and hides its contents until it is tapped`() {
        setContent(fold())

        composeRule.onNodeWithText("TCP 调优前").assertIsDisplayed()
        composeRule.onNodeWithText("折叠里的正文").assertDoesNotExist()

        composeRule.onNodeWithText("TCP 调优前").performClick()

        composeRule.onNodeWithText("折叠里的正文").assertIsDisplayed()
        // The host's slot reaches inside the fold, so a code block there is still the app's own.
        composeRule.onNodeWithText("host code sh").assertIsDisplayed()
    }

    /** `<details open>` asks for the block to start open, and nothing else about it changes. */
    @Test
    fun `an open fold starts expanded`() {
        setContent(fold(open = true))

        composeRule.onNodeWithText("折叠里的正文").assertIsDisplayed()
    }

    /** An empty `<summary>` still needs a label, or there is nothing to tap and nothing to read. */
    @Test
    fun `a fold with no summary is labelled anyway`() {
        setContent(fold(title = ""))

        composeRule.onNodeWithText("折叠内容").assertIsDisplayed()
    }
}
