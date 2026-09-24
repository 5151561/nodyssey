package io.github.nodyssey.ui.postlist

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.nodyssey.data.Board
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The two things 1j's editor does with a tap and a drag that have a consequence past the screen.
 *
 * A tap on a pill takes that board off the strip while editing — and must not also open it, which is
 * what the same tap does outside the editor. And the parked boards live in a block of their own below
 * the pills the drag works on, so a drag has to hand back the strip's half *and* the parked half: a
 * reorder that wrote only the half it could see would quietly put every parked board back.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class BoardEditorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var opened = mutableListOf<String?>()
    private var lastOrder: List<String>? = null
    private var lastParked: Set<String>? = null

    private fun longPress(title: String) {
        composeRule.onNodeWithText(title).performTouchInput { longClick() }
        composeRule.waitForIdle()
    }

    private fun openEditor() {
        composeRule.setContent {
            PlazaTheme {
                BoardStrip(
                    boards = BOARDS,
                    parkedBoards = emptyList(),
                    selectedSlug = null,
                    onBoardClick = { opened += it },
                    onArrangementChange = { order, parked ->
                        lastOrder = order
                        lastParked = parked
                    },
                )
            }
        }
        composeRule.onNodeWithContentDescription("全部版块").performClick()
        composeRule.waitForIdle()
        longPress("日常")
    }

    @Test
    fun `a tap in the editor takes a board off the strip and does not open it`() {
        openEditor()

        composeRule.onNodeWithText("技术").performClick()
        composeRule.waitForIdle()

        assertEquals(setOf("tech"), lastParked)
        assertEquals(emptyList<String?>(), opened)

        // Under 未加入首页 now, a tap from coming back.
        composeRule.onNodeWithContentDescription("技术, 已移出首页").performClick()
        composeRule.waitForIdle()
        assertEquals(emptySet<String>(), lastParked)
    }

    /**
     * The long press that opens the editor ends with the finger lifting off a pill — and in the
     * editor, a pill's tap parks it. The press has to end with the long press, not carry on into a
     * tap the editor then acts on.
     */
    @Test
    fun `opening the editor again parks nothing`() {
        openEditor()
        composeRule.onNodeWithText("完成").performClick()
        composeRule.waitForIdle()

        longPress("日常")

        assertEquals(null, lastParked)
        composeRule.onNodeWithText("完成").assertExists()
    }

    /**
     * Parking reshapes the strip under a drag detector that is already running; the drag has to see
     * the strip as it is now, or it lands the pill by where the parked board used to be.
     */
    @Test
    fun `a drag after parking keeps the parked board parked`() {
        openEditor()
        composeRule.onNodeWithText("技术").performClick()
        composeRule.waitForIdle()

        val from = composeRule.onNodeWithText("日常").fetchSemanticsNode().boundsInRoot.center
        val onto = composeRule.onNodeWithText("交易").fetchSemanticsNode().boundsInRoot.center
        lastParked = null
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

        assertEquals(listOf("trade", "daily", "tech"), lastOrder)
        assertEquals(setOf("tech"), lastParked)
    }

    private companion object {
        val BOARDS =
            listOf(
                Board(null, "综合", null),
                Board("daily", "日常", null),
                Board("tech", "技术", null),
                Board("trade", "交易", null),
            )
    }
}
