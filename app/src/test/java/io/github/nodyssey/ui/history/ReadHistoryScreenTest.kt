package io.github.nodyssey.ui.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.nodyssey.data.ReadHistoryEntry
import io.github.nodyssey.ui.theme.NodysseyTheme
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

    private val now = 1_000_000L

    private fun setScreen(
        state: ReadHistoryUiState,
        onPostClick: (Long) -> Unit = {},
        onRemove: (Long) -> Unit = {},
        onClearAll: () -> Unit = {},
    ) {
        composeRule.setContent {
            NodysseyTheme {
                ReadHistoryScreen(
                    state = state,
                    onBack = {},
                    onPostClick = onPostClick,
                    onRemove = onRemove,
                    onClearAll = onClearAll,
                    nowMillis = now,
                )
            }
        }
    }

    private fun entry(
        postId: Long,
        title: String? = "绿云抢鸡竞赛",
        author: String? = "ipv4",
        category: String? = "日常",
        readAt: Long = now,
    ) = ReadHistoryEntry(
        postId = postId,
        title = title,
        authorName = author,
        authorUid = 1,
        categoryTitle = category,
        commentCount = 10,
        lastReadAtMillis = readAt,
    )

    @Test
    fun `lists what the snapshot captured`() {
        setScreen(ReadHistoryUiState(isLoading = false, entries = listOf(entry(7))))

        composeRule.onNodeWithText("绿云抢鸡竞赛").assertIsDisplayed()
        composeRule.onNodeWithText("日常 · ipv4 · 刚刚").assertIsDisplayed()
    }

    /**
     * A row written before the snapshot columns existed. The id is not much, but it is true and it
     * still opens the thread — which beats an empty line the reader cannot act on.
     */
    @Test
    fun `a row with no captured title falls back to the post id`() {
        setScreen(
            ReadHistoryUiState(
                isLoading = false,
                entries = listOf(entry(7, title = null, author = null, category = null)),
            ),
        )

        composeRule.onNodeWithText("帖子 #7").assertIsDisplayed()
        composeRule.onNodeWithText("刚刚").assertIsDisplayed()
    }

    @Test
    fun `tapping a row opens the thread`() {
        var opened: Long? = null
        setScreen(ReadHistoryUiState(isLoading = false, entries = listOf(entry(7))), onPostClick = { opened = it })

        composeRule.onNodeWithText("绿云抢鸡竞赛").performClick()

        assertEquals(7L, opened)
    }

    @Test
    fun `the row's close button removes just that entry`() {
        var removed: Long? = null
        setScreen(ReadHistoryUiState(isLoading = false, entries = listOf(entry(7))), onRemove = { removed = it })

        composeRule.onNodeWithContentDescription("从历史中移除 绿云抢鸡竞赛").performClick()

        assertEquals(7L, removed)
    }

    /**
     * The confirmation has to say that clearing also resets the unread baselines — the reader would
     * otherwise find out by noticing the feed had gone un-greyed.
     */
    @Test
    fun `clearing asks first and says what else it clears`() {
        var cleared = 0
        setScreen(ReadHistoryUiState(isLoading = false, entries = listOf(entry(7))), onClearAll = { cleared++ })

        composeRule.onNodeWithText("全部清除").performClick()

        composeRule.onNodeWithText("清除浏览历史？").assertIsDisplayed()
        composeRule.onNodeWithText("这些记录同时也是「几条新回复」的已读基准。清除之后，列表里已读变灰的帖子会重新显示为未读。")
            .assertIsDisplayed()
        assertEquals(0, cleared)

        composeRule.onNodeWithText("清除").performClick()
        assertEquals(1, cleared)
    }

    @Test
    fun `an empty history says so and offers nothing to clear`() {
        setScreen(ReadHistoryUiState(isLoading = false, entries = emptyList()))

        composeRule.onNodeWithText("还没有看过帖子").assertIsDisplayed()
        composeRule.onNodeWithText("全部清除").assertDoesNotExist()
    }
}
