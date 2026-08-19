package io.github.plaza.designsys.richtext

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The two slots have to reach nested nodes, not just top-level ones.
 *
 * They did not, and the way they failed is the reason this exists: the private recursion carried
 * default arguments, so a call site that forgot to pass the caller's slot got the *default* instead
 * of a compile error. A vote quoted inside another post rendered as the inert placeholder, which
 * looks like a design decision rather than like a bug.
 *
 * The defaults are gone from the private helpers now, so a future missed hand-off is a compile
 * error. These cover the outcome anyway: the compiler cannot say whether the value being threaded is
 * the caller's or a freshly made one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class RichContentSlotsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val vote = RichNode.VotePlaceholder(voteId = 2871)
    private val code = RichNode.CodeBlock(code = "uname -a", language = "sh")

    private fun setContent(nodes: List<RichNode>) {
        composeRule.setContent {
            PlazaTheme {
                RichContent(
                    nodes = nodes,
                    onLinkClick = {},
                    onImageClick = {},
                    voteContent = { Text("live vote $it") },
                    codeBlockContent = { Text("host code ${it.language}") },
                )
            }
        }
    }

    @Test
    fun `a vote at the top level uses the caller's slot`() {
        setContent(listOf(vote))

        composeRule.onNodeWithText("live vote 2871").assertIsDisplayed()
    }

    @Test
    fun `a vote inside a quote uses the caller's slot`() {
        setContent(listOf(RichNode.Quote(children = listOf(vote))))

        composeRule.onNodeWithText("live vote 2871").assertIsDisplayed()
    }

    @Test
    fun `a vote inside a list item uses the caller's slot`() {
        setContent(listOf(RichNode.ListBlock(ordered = false, items = listOf(listOf(vote)))))

        composeRule.onNodeWithText("live vote 2871").assertIsDisplayed()
    }

    @Test
    fun `a vote inside a tab uses the caller's slot`() {
        setContent(
            listOf(RichNode.Tabs(tabs = listOf(RichNode.Tabs.Tab(title = "一", children = listOf(vote))))),
        )

        composeRule.onNodeWithText("live vote 2871").assertIsDisplayed()
    }

    /** A vote two levels down: the quote's own recursion has to keep handing the slot on. */
    @Test
    fun `a vote quoted inside a quoted list uses the caller's slot`() {
        setContent(
            listOf(
                RichNode.Quote(
                    children = listOf(RichNode.ListBlock(ordered = true, items = listOf(listOf(vote)))),
                ),
            ),
        )

        composeRule.onNodeWithText("live vote 2871").assertIsDisplayed()
    }

    /**
     * The same for code blocks, which is what a tab group exists to hold: a benchmark report filed
     * behind a tab has to reach the host that knows how to redraw it.
     */
    @Test
    fun `a code block inside a tab uses the caller's slot`() {
        setContent(
            listOf(RichNode.Tabs(tabs = listOf(RichNode.Tabs.Tab(title = "一", children = listOf(code))))),
        )

        composeRule.onNodeWithText("host code sh").assertIsDisplayed()
    }

    @Test
    fun `a code block inside a quote uses the caller's slot`() {
        setContent(listOf(RichNode.Quote(children = listOf(code))))

        composeRule.onNodeWithText("host code sh").assertIsDisplayed()
    }
}
