package io.github.plaza.designsys.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The wrench panel: it never empties the strip, and a drag survives its own reordering. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class ToolbarCustomizeSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var enabled: List<EditorAction>

    private fun setSheet(start: List<EditorAction>) {
        enabled = start
        composeRule.setContent {
            var keys by remember { mutableStateOf(start) }
            PlazaTheme {
                ToolbarCustomizeSheet(
                    layout = ToolbarLayout(
                        enabled = keys,
                        available = EditorAction.entries.filterNot { it in keys },
                    ),
                    onChange = {
                        keys = it
                        enabled = it
                    },
                    onReset = {},
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun `the last key cannot be removed`() {
        setSheet(listOf(EditorAction.BOLD))

        composeRule.onNodeWithContentDescription("移出工具栏").performClick()

        // An empty arrangement reads back as "never customised" and would restore the defaults.
        assertEquals(listOf(EditorAction.BOLD), enabled)
    }

    @Test
    fun `a drag keeps going after the swap that rewrites the list`() {
        setSheet(listOf(EditorAction.BOLD, EditorAction.CODE, EditorAction.QUOTE, EditorAction.LINK))

        // Two rows down in one gesture. The second step is the one that used to be lost: the first
        // swap replaces the list the handler was given, and a handler bound to that list would
        // either be cancelled or reorder the stale copy.
        composeRule.onAllNodesWithContentDescription("拖动排序")[0].performTouchInput {
            down(center)
            moveBy(Offset(0f, ROW_SLOT_PX))
            moveBy(Offset(0f, ROW_SLOT_PX))
            up()
        }

        assertEquals(
            listOf(EditorAction.CODE, EditorAction.QUOTE, EditorAction.BOLD, EditorAction.LINK),
            enabled,
        )
    }
}

/** One slot — row plus gap — at the test's default density of 1, matching `ToolbarCustomizeSheet`. */
private const val ROW_SLOT_PX = 64f
