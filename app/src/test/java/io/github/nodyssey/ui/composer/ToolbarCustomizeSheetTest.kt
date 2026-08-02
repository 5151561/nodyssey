package io.github.nodyssey.ui.composer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.nodyssey.ui.theme.NodysseyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The wrench panel: what it offers, and that a drag survives its own reordering. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class ToolbarCustomizeSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var enabled: List<EditorAction>
    private var resets = 0

    private fun setSheet(start: List<EditorAction>) {
        enabled = start
        composeRule.setContent {
            var keys by remember { mutableStateOf(start) }
            NodysseyTheme {
                ToolbarCustomizeSheet(
                    layout = ToolbarLayout(
                        enabled = keys,
                        available = EditorAction.entries.filterNot { it in keys },
                    ),
                    onChange = {
                        keys = it
                        enabled = it
                    },
                    onReset = { resets++ },
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun `the pool holds everything the strip does not`() {
        setSheet(listOf(EditorAction.BOLD, EditorAction.CODE))

        composeRule.onNodeWithText("工具栏上的按键").assertIsDisplayed()
        composeRule.onNodeWithText("可添加").assertIsDisplayed()
        // 斜体 is in neither composer's defaults, so the pool is where it can be found at all.
        composeRule.onNodeWithText("斜体").assertIsDisplayed()
    }

    @Test
    fun `adding a key puts it at the end of the strip`() {
        setSheet(listOf(EditorAction.BOLD, EditorAction.CODE))

        composeRule.onAllNodesWithContentDescription("加入工具栏")[0].performClick()

        assertEquals(3, enabled.size)
        assertEquals(listOf(EditorAction.BOLD, EditorAction.CODE), enabled.take(2))
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
