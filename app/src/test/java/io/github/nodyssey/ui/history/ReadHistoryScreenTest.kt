package io.github.nodyssey.ui.history

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.nodyssey.core.TimeFormat
import io.github.nodyssey.data.ReadHistoryEntry
import io.github.nodyssey.data.settings.SettingsRepository
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

    private val now = 1_800_000_000_000L
    private val oneDay = 24 * 60 * 60 * 1000L

    private fun setScreen(
        state: ReadHistoryUiState,
        onPostClick: (Long) -> Unit = {},
        onRemove: (ReadHistoryEntry) -> Unit = {},
        onRestore: (ReadHistoryEntry) -> Unit = {},
        onClearAll: () -> Unit = {},
        onLimitChange: (Int) -> Unit = {},
    ) {
        composeRule.setContent {
            NodysseyTheme {
                ReadHistoryScreen(
                    state = state,
                    onBack = {},
                    onPostClick = onPostClick,
                    onRemove = onRemove,
                    onRestore = onRestore,
                    onClearAll = onClearAll,
                    onLimitChange = onLimitChange,
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

    /** The swipe's twin, and the only one TalkBack — or a test — can reach. */
    private fun SemanticsNodeInteraction.performRemove() {
        val actions = fetchSemanticsNode().config[SemanticsActions.CustomActions]
        composeRule.runOnUiThread {
            actions.single { it.label.startsWith("从历史中移除") }.action()
        }
    }

    private fun openMenu() {
        composeRule.onNodeWithContentDescription("更多").performClick()
    }

    @Test
    fun `lists what the snapshot captured`() {
        setScreen(ReadHistoryUiState(isLoading = false, entries = listOf(entry(7))))

        composeRule.onNodeWithText("绿云抢鸡竞赛").assertIsDisplayed()
        // The meta line is separate pieces now, so the board can be the same coloured tag the feed uses.
        composeRule.onNodeWithText("日常").assertIsDisplayed()
        composeRule.onNodeWithText("ipv4").assertIsDisplayed()
        // Under a 今天 heading the row's slot is better spent on the clock than on repeating the day.
        composeRule.onNodeWithText(TimeFormat.clock(now)).assertIsDisplayed()
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
        composeRule.onNodeWithText(TimeFormat.clock(now)).assertIsDisplayed()
    }

    /** Day headings are the point of the screen: "when did I read this" is why anyone opens it. */
    @Test
    fun `rows are grouped under the day they were read`() {
        setScreen(
            ReadHistoryUiState(
                isLoading = false,
                entries =
                listOf(
                    entry(1, title = "今天读的"),
                    entry(2, title = "昨天读的", readAt = now - oneDay),
                    entry(3, title = "上周读的", readAt = now - 9 * oneDay),
                ),
            ),
        )

        composeRule.onNodeWithText("今天").assertIsDisplayed()
        composeRule.onNodeWithText("昨天").assertIsDisplayed()
        composeRule.onNodeWithText("更早").assertIsDisplayed()
    }

    @Test
    fun `tapping a row opens the thread`() {
        var opened: Long? = null
        setScreen(ReadHistoryUiState(isLoading = false, entries = listOf(entry(7))), onPostClick = { opened = it })

        composeRule.onNodeWithText("绿云抢鸡竞赛").performClick()

        assertEquals(7L, opened)
    }

    @Test
    fun `removing a row takes just that entry`() {
        var removed: ReadHistoryEntry? = null
        setScreen(ReadHistoryUiState(isLoading = false, entries = listOf(entry(7))), onRemove = { removed = it })

        composeRule.onNodeWithText("绿云抢鸡竞赛").performRemove()

        assertEquals(7L, removed?.postId)
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
        composeRule.onNodeWithText("这些记录同时也是「几条新回复」的已读基准。清除之后，列表里已读变灰的帖子会重新显示为未读。")
            .assertIsDisplayed()
        assertEquals(0, cleared)

        composeRule.onNodeWithText("清除").performClick()
        assertEquals(1, cleared)
    }

    /** How much is kept is a question this screen raises, so it answers it without a menu. */
    @Test
    fun `the bar says how much is stored and how much is kept`() {
        setScreen(ReadHistoryUiState(isLoading = false, entries = listOf(entry(7), entry(8))))

        composeRule.onNodeWithText("共 2 条 · 保留 300 条").assertIsDisplayed()
    }

    @Test
    fun `the limit picker states the cost and reports the choice`() {
        var chosen: Int? = null
        setScreen(
            ReadHistoryUiState(isLoading = false, entries = listOf(entry(7))),
            onLimitChange = { chosen = it },
        )

        openMenu()
        composeRule.onNodeWithText("保留条数").performClick()

        composeRule.onNodeWithText("300 条（默认）").assertIsDisplayed()
        composeRule.onNodeWithText("无上限").performClick()

        assertEquals(SettingsRepository.READ_HISTORY_UNLIMITED, chosen)
    }

    @Test
    fun `an empty history says so and offers nothing to clear`() {
        setScreen(ReadHistoryUiState(isLoading = false, entries = emptyList()))

        composeRule.onNodeWithText("还没有看过帖子").assertIsDisplayed()
        openMenu()
        composeRule.onNodeWithText("全部清除").assertDoesNotExist()
    }
}
