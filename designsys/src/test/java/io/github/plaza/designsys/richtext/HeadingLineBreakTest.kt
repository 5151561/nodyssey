package io.github.plaza.designsys.richtext

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A heading breaks the way the body around it does.
 *
 * `RichBlock` used to force [LineBreak.Heading] on headings, whose Balanced strategy evens the
 * wrapped lines out. Even lines in Chinese means a short first line: post-584268's
 * `GitHub项目地址（欢迎Star关注）： <url>` filled 0.69 of a 328dp column with `关注）：` pushed
 * down and 305px sitting empty beside it. That override outlived the switch to greedy body text
 * and was why the fix appeared to do nothing on the one screen it was reported from.
 *
 * Geometry is the reporter's device: SM-S9310, 360dp wide, 16dp gutters, system font scale 1.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class HeadingLineBreakTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun layoutOfHeading(): Pair<TextLayoutResult, Int> {
        val heading =
            RichNode.Heading(
                level = 3,
                inlines = listOf(
                    InlineNode.Text("GitHub项目地址（欢迎Star关注）： "),
                    InlineNode.Link(
                        text = "https://github.com/xykt/HardwareQuality",
                        url = "https://github.com/xykt/HardwareQuality",
                    ),
                ),
            )
        composeRule.setContent {
            PlazaTheme {
                RichContent(
                    nodes = listOf(heading),
                    onLinkClick = {},
                    onImageClick = {},
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        val root = composeRule.onRoot().fetchSemanticsNode()
        val node = generateSequence(listOf(root)) { level ->
            level.flatMap { it.children }.ifEmpty { null }
        }.flatten().first { it.config.contains(SemanticsActions.GetTextLayoutResult) }
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first() to node.size.width
    }

    @Test
    fun `a heading inherits the body's greedy breaker`() {
        val (layout, _) = layoutOfHeading()

        assertEquals(LineBreak.Simple, layout.layoutInput.style.lineBreak)
    }

    @Test
    fun `a heading fills its first line rather than evening the lines out`() {
        val (layout, width) = layoutOfHeading()

        val first = (layout.getLineRight(0) - layout.getLineLeft(0)) / width
        assertTrue("the heading's first line should be near flush, got $first", first > 0.9f)
    }
}
