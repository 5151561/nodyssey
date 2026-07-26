package io.github.nsreader.ui.richtext

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import io.github.nsreader.model.RichNode
import io.github.nsreader.ui.theme.NodeSeekTheme
import io.github.nsreader.ui.theme.Sizes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rendering tests for post bodies.
 *
 * The parser has its own tests; these cover what only exists once the nodes are on screen — above
 * all the controls the body draws itself, which get none of the padding a Material component would
 * have applied for them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class RichContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(nodes: List<RichNode>) {
        composeRule.setContent {
            NodeSeekTheme {
                RichContent(nodes = nodes, onLinkClick = {}, onImageClick = {})
            }
        }
    }

    /**
     * A hand-rolled `Row` around a 15dp glyph came to 23dp, which is half of Material's minimum and
     * of the number `Sizes.minTouchTarget` calls the brief's hard requirement. Nothing enforces that
     * for a control the app lays out itself, so this test does.
     */
    @Test
    fun `the code block copy control clears the minimum touch target`() {
        setContent(listOf(RichNode.CodeBlock(code = "val x = 1", language = "kotlin")))

        composeRule
            .onNodeWithText("复制")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(Sizes.minTouchTarget)
    }

    @Test
    fun `a code block shows its language`() {
        setContent(listOf(RichNode.CodeBlock(code = "val x = 1", language = "kotlin")))

        composeRule.onNodeWithText("kotlin").assertIsDisplayed()
    }

    /** Regression guard for the assertion above: 48dp is the number, not "whatever it renders". */
    @Test
    fun `the minimum touch target is the Material minimum`() {
        assert(Sizes.minTouchTarget == 48.dp) { "expected 48dp, was ${Sizes.minTouchTarget}" }
    }
}
