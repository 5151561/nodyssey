package io.github.nodyssey.ui.postlist

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.nodyssey.data.Board
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 编辑板块 still reorders when every animation has been snapped away.
 *
 * The drag reads both of its specs off `MaterialTheme.motionScheme`, and 墨水屏模式 — like the OS's
 * 移除动画 before it — replaces that scheme with one whose every spec is `snap()`. A gesture that
 * quietly depended on an animation having a duration would keep working in every other
 * configuration and stop working in exactly this one, so the same drag is asserted twice.
 *
 * Neither pill here is 综合 or parked: those two are deliberately undraggable — see the `takeIf` in
 * `ExpandedBoards`, and `BoardReorderTest` for the rule itself — and a drag test that picked one
 * would be asserting the opposite of what it looks like.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class BoardDragEinkTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a pill can be dragged onto its neighbour`() {
        assertTrue("reorder never fired with motion on", dragFirstOntoSecond(eink = false))
    }

    @Test
    fun `a pill can be dragged onto its neighbour on electronic paper`() {
        assertTrue("reorder never fired under 墨水屏模式", dragFirstOntoSecond(eink = true))
    }

    @OptIn(ExperimentalTestApi::class)
    private fun dragFirstOntoSecond(eink: Boolean): Boolean {
        var reordered = false
        composeRule.setContent {
            PlazaTheme(einkMode = eink) {
                BoardStrip(
                    boards = BOARDS,
                    parkedBoards = emptyList(),
                    selectedSlug = null,
                    onBoardClick = {},
                    onArrangementChange = { _, _ -> reordered = true },
                )
            }
        }

        // 全部版块 first: the collapsed strip is one scrolling row with no editor behind it.
        composeRule.onNodeWithContentDescription("全部版块").performClick()
        composeRule.waitForIdle()

        // A long press anywhere on the strip is what opens 编辑板块.
        composeRule.onNodeWithText("日常").performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("拖动排序，点角标移出或加回").assertIsDisplayed()

        val from = composeRule.onNodeWithText("日常").fetchSemanticsNode().boundsInRoot.center
        val onto = composeRule.onNodeWithText("技术").fetchSemanticsNode().boundsInRoot.center

        // Driven on the root in root coordinates, and in steps: `detectDragGestures` only starts
        // once touch slop is crossed, and a single jump from down to up gives it one event to
        // decide on.
        composeRule.onRoot().performTouchInput {
            down(from)
            advanceEventTime(8)
            repeat(8) { step ->
                moveTo(from + (onto - from) * ((step + 1) / 8f))
                advanceEventTime(8)
            }
            up()
        }
        composeRule.waitForIdle()
        return reordered
    }

    private companion object {
        val BOARDS =
            listOf(
                Board(null, "综合", null),
                Board("daily", "日常", null),
                Board("tech", "技术", null),
            )
    }
}
