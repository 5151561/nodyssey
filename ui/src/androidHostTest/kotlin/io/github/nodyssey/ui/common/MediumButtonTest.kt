package io.github.nodyssey.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The medium button's two promises: one label size for every screen's main action, and a busy state
 * that waits in the button's leading slot instead of each screen drawing its own spinner.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class MediumButtonTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val progress = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    @Test
    fun `a busy button shows a named spinner beside its label and stays a button`() {
        composeRule.setContent {
            PlazaTheme {
                MediumButton(onClick = {}, busy = true) { Text("登录中") }
            }
        }

        composeRule.onAllNodes(progress, useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("加载中", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNodeWithText("登录中").assertHasClickAction()
    }

    @Test
    fun `an idle button has no spinner`() {
        composeRule.setContent {
            PlazaTheme {
                MediumButton(onClick = {}) { Text("登录") }
            }
        }

        composeRule.onAllNodes(progress, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `every style labels in titleMedium`() {
        var expected = 0f
        composeRule.setContent {
            PlazaTheme {
                expected = MaterialTheme.typography.titleMedium.fontSize.value
                MediumButton(onClick = {}) { Text("填充") }
                MediumButton(onClick = {}, style = MediumButtonStyle.Tonal) { Text("色调") }
                MediumButton(onClick = {}, style = MediumButtonStyle.Outlined, busy = true) { Text("描边") }
            }
        }

        listOf("填充", "色调", "描边").forEach { assertEquals(it, expected, fontSizeOf(it), 0.01f) }
    }

    private fun fontSizeOf(text: String): Float {
        val node = composeRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.first().layoutInput.style.fontSize.value
    }
}
