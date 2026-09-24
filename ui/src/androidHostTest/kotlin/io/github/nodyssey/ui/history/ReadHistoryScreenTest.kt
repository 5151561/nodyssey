package io.github.nodyssey.ui.history

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import io.github.nodyssey.data.ReadHistoryEntry
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class ReadHistoryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val now = 1_800_000_000_000L

    private fun setScreen(
        state: ReadHistoryUiState,
        onClearAll: () -> Unit = {},
    ) {
        composeRule.setContent {
            PlazaTheme {
                ReadHistoryScreen(
                    state = state,
                    onBack = {},
                    onPostClick = {},
                    onRemove = {},
                    onRestore = {},
                    onClearAll = onClearAll,
                    onLimitChange = {},
                    nowMillis = now,
                )
            }
        }
    }

    private fun entry(
        postId: Long,
        title: String = "绿云抢鸡竞赛",
    ) = ReadHistoryEntry(
        postId = postId,
        title = title,
        authorName = "ipv4",
        authorUid = 1,
        categoryTitle = "日常",
        commentCount = 10,
        lastReadAtMillis = now,
    )

    private fun openMenu() {
        composeRule.onNodeWithContentDescription("更多").performClick()
    }

    /**
     * The confirmation has to say that clearing also resets the unread baselines — the reader would
     * otherwise find out by noticing the feed had gone un-greyed.
     */
    @Test
    fun `clearing asks first and says what else it clears`() {
        var cleared = 0
        setScreen(ReadHistoryUiState(isLoading = false, entries = listOf(entry(7))), onClearAll = { cleared++ })

        openMenu()
        composeRule.onNodeWithText("全部清除").performClick()

        composeRule.onNodeWithText("清除浏览历史？").assertIsDisplayed()
        composeRule.onNodeWithText("这些记录同时也是「几条新回复」的已读基准。清除之后，列表里已读变灰的帖子会重新显示为未读，帖子里记下的「上次阅读」位置也会一并清掉。")
            .assertIsDisplayed()
        assertEquals(0, cleared)

        composeRule.onNodeWithText("清除").performClick()
        assertEquals(1, cleared)
    }

    /**
     * 撤销 puts the row back, and putting it back must not read as another swipe.
     *
     * This one has to be a real gesture: the row's accessibility action never touches the swipe state
     * and so cannot see the bug. A `LazyColumn` stores each item's
     * saveable state under its key and hands it back when a row with that key returns, so a dismiss
     * state that survives the row is handed to the restored row — which then dismisses itself before
     * anyone can see it, and 撤销 looks like it did nothing.
     *
     * The other rows are not decoration: a list that empties itself down to nothing prunes the saved
     * state on the way out, and the bug goes with it. A history worth undoing in has neighbours.
     */
    @Test
    fun `a row put back after a swipe is not swiped away again`() {
        var removals = 0
        val entries =
            mutableStateListOf(
                entry(7),
                entry(8, title = "第二条"),
                entry(9, title = "第三条"),
                entry(10, title = "第四条"),
            )
        composeRule.setContent {
            PlazaTheme {
                ReadHistoryScreen(
                    state = ReadHistoryUiState(isLoading = false, entries = entries.toList()),
                    onBack = {},
                    onPostClick = {},
                    onRemove = { removals++ },
                    onRestore = { entries.add(it) },
                    onClearAll = {},
                    onLimitChange = {},
                    nowMillis = now,
                )
            }
        }

        composeRule.onNodeWithText("绿云抢鸡竞赛").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertEquals(1, removals)

        // The row leaves only once the deletion has been through Room, which is a frame or two after
        // the swipe settles — long enough for the dismissed state to be saved under the row's key.
        entries.removeAll { it.postId == 7L }
        composeRule.waitForIdle()

        // What 撤销 does, without depending on the snackbar's timing.
        entries.add(entry(7))
        composeRule.waitForIdle()

        assertEquals(1, removals)
        composeRule.onNodeWithText("绿云抢鸡竞赛").assertIsDisplayed()
    }
}
