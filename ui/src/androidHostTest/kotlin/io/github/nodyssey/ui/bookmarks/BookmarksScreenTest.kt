package io.github.nodyssey.ui.bookmarks

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.nodyssey.data.OfflineFailure
import io.github.nodyssey.data.OfflineState
import io.github.nodyssey.data.OfflineUsage
import io.github.plaza.core.net.SiteError
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 收藏 at the size the board was drawn at.
 *
 * The five download states and the two modes are what this screen *is*, so those are what is
 * asserted: that each state draws its own label rather than a shared one, that the offline chrome is
 * absent when the library says it has nothing to say, and that long-press changes the screen's mode
 * rather than opening the thread.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class BookmarksScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun entry(
        postId: Long,
        title: String,
        offline: OfflineState = OfflineState.NotDownloaded,
    ) = BookmarkEntry(
        postId = postId,
        title = title,
        categoryTitle = "技术",
        categorySlug = "tech",
        authorName = "nssk",
        commentCount = 41,
        createdAtText = "上周",
        offline = offline,
    )

    private val entries =
        listOf(
            entry(1, "已经离线的帖子", OfflineState.Downloaded(bytes = 2_936_012)),
            entry(2, "正在下载的帖子", OfflineState.Downloading(progress = 0.62f)),
            entry(3, "落后回复的帖子", OfflineState.Stale(behindReplies = 3, bytes = 1_782_579)),
            entry(4, "还没下载的帖子", OfflineState.NotDownloaded),
            entry(5, "下载失败的帖子", OfflineState.Failed(OfflineFailure.OutOfSpace)),
        )

    private fun state(
        selection: Set<Long>? = null,
        offlineAvailable: Boolean = true,
        usage: OfflineUsage =
            OfflineUsage(posts = 2, textBytes = 1_000_000, imageBytes = 12_000_000, freeBytes = 3_435_973_836),
    ) = BookmarksUiState(
        entries = entries,
        isSyncing = false,
        selection = selection,
        offlineAvailable = offlineAvailable,
        usage = usage,
    )

    private fun setScreen(
        state: BookmarksUiState,
        onPostClick: (Long) -> Unit = {},
        onStartSelection: (Long) -> Unit = {},
        onToggleSelection: (Long) -> Unit = {},
        onRowOfflineAction: (BookmarkEntry) -> Unit = {},
        onDownloadPending: () -> Unit = {},
        onOfflineSettings: (io.github.nodyssey.data.OfflineSettings) -> Unit = {},
    ) {
        composeRule.setContent {
            PlazaTheme {
                BookmarksScreen(
                    state = state,
                    onBack = {},
                    onPostClick = onPostClick,
                    onOpenBrowser = {},
                    onRetry = {},
                    onSignIn = {},
                    onVerify = {},
                    onFilter = {},
                    onSort = {},
                    onSearching = {},
                    onQuery = {},
                    onStartSelection = onStartSelection,
                    onToggleSelection = onToggleSelection,
                    onToggleSelectAll = {},
                    onClearSelection = {},
                    onRemoveSelected = {},
                    onRestore = {},
                    onDownloadSelected = {},
                    onDownloadPending = onDownloadPending,
                    onRowOfflineAction = onRowOfflineAction,
                    onOfflineSettings = onOfflineSettings,
                    onClearOffline = {},
                )
            }
        }
    }

    /**
     * The download column clears its children's semantics so a screen reader hears one thing rather
     * than a glyph and a loose number — so the five states are asserted by what that one thing says,
     * which is also the only place the in-flight percentage is reachable.
     */
    @Test
    fun `each download state announces itself`() {
        setScreen(state())

        composeRule.onNodeWithContentDescription("已离线").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("下载中 62%，点按停止").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("同步").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("下载").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("重试").assertIsDisplayed()
    }

    /** The two states that owe an explanation get their own line under the meta, not a bare icon. */
    @Test
    fun `stale and failed rows say why`() {
        setScreen(state())

        composeRule.onNodeWithText("离线版落后 3 条回复").assertIsDisplayed()
        composeRule.onNodeWithText("下载失败 · 图片超出剩余空间").assertIsDisplayed()
    }

    /** What is stored is a standing fact about the screen, so it rides in the bar's subtitle. */
    @Test
    fun `the subtitle and the download-all pill count what is actually there`() {
        setScreen(state())

        composeRule.onAllNodesWithText("已离线 2 篇 · 占用 12.4 MB").onFirst().assertIsDisplayed()
        // 未下载 + 失败 = 2. Already-offline and in-flight rows are not things left to download.
        composeRule.onNodeWithText("全部下载 · 2 篇").assertIsDisplayed()
    }

    /** Nothing downloaded is not a fact worth a line; the bar drops it rather than printing zeroes. */
    @Test
    fun `an empty library leaves the subtitle off entirely`() {
        setScreen(state(usage = OfflineUsage()))

        composeRule.onNodeWithText("已离线 0 篇 · 占用 0 B").assertDoesNotExist()
    }

    /**
     * The whole point of the stored list: a failed refresh must not take the rows away.
     *
     * The strip says so instead — with the site's own reason, since 需要登录后查看 and 网络开小差了
     * send the reader to do quite different things.
     */
    @Test
    fun `a failed refresh keeps the rows and admits the list is a snapshot`() {
        setScreen(state().copy(error = SiteError.Network))

        composeRule.onNodeWithText("网络开小差了，这是上次同步的列表").assertIsDisplayed()
        composeRule.onNodeWithText("已经离线的帖子").assertIsDisplayed()
        composeRule.onNodeWithText("重试").assertIsDisplayed()
    }

    /** With nothing stored there is nothing to keep, and the error page is the honest screen. */
    @Test
    fun `a failed load with nothing stored is still the error page`() {
        setScreen(state().copy(entries = emptyList(), error = SiteError.Network))

        composeRule.onNodeWithText("网络开小差了，这是上次同步的列表").assertDoesNotExist()
        composeRule.onAllNodesWithText("网络开小差了").onFirst().assertIsDisplayed()
    }

    /**
     * 离线管理 was one item behind a ⋮ once 排序 moved out to its own control beside the chips.
     */
    @Test
    fun `offline management is one tap, not a menu of one`() {
        var managed = false
        setScreen(state(), onOfflineSettings = { managed = true })

        composeRule.onNodeWithContentDescription("更多").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("管理").performClick()
        composeRule.onNodeWithText("离线阅读").assertIsDisplayed()
        assertFalse(managed)
    }

    /**
     * Without a library there is nothing true to say about offline, so none of it is drawn — and in
     * particular no 「已下载 0」 chip, which reads as a broken feature rather than an absent one.
     */
    @Test
    fun `no offline library means no offline chrome`() {
        setScreen(state(offlineAvailable = false))

        composeRule.onNodeWithText("全部 5").assertIsDisplayed()
        composeRule.onNodeWithText("已下载 0").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("已离线").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("管理").assertDoesNotExist()
        composeRule.onAllNodesWithText("已离线 2 篇 · 占用 12.4 MB").assertCountEquals(0)
    }

    @Test
    fun `long press starts multi-select instead of opening the thread`() {
        var opened: Long? = null
        var startedOn: Long? = null
        setScreen(state(), onPostClick = { opened = it }, onStartSelection = { startedOn = it })

        composeRule.onNodeWithText("还没下载的帖子").performTouchInput { longClick() }

        assertEquals(null, opened)
        assertEquals(4L, startedOn)
    }

    /** In multi-select a tap ticks the row; nothing navigates until the mode is left. */
    @Test
    fun `tapping a row in multi-select toggles it`() {
        var opened: Long? = null
        var toggled: Long? = null
        setScreen(
            state(selection = setOf(1L)),
            onPostClick = { opened = it },
            onToggleSelection = { toggled = it },
        )

        composeRule.onNodeWithText("落后回复的帖子").performClick()

        assertEquals(null, opened)
        assertEquals(3L, toggled)
    }

    /**
     * The download column is gone in multi-select, so what it was saying moves onto the meta line —
     * otherwise ticking rows is also the moment you lose sight of which ones you already have.
     */
    @Test
    fun `multi-select moves the offline state onto the meta line`() {
        setScreen(state(selection = setOf(1L)))

        composeRule.onNodeWithText("已选 1 项").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("已离线").assertDoesNotExist()
        composeRule.onNodeWithText("已离线 2.8 MB").assertIsDisplayed()
        composeRule.onNodeWithText("下载中 62%").assertIsDisplayed()
    }

    @Test
    fun `the download column acts on the row it belongs to`() {
        var acted: BookmarkEntry? = null
        setScreen(state(), onRowOfflineAction = { acted = it })

        composeRule.onNodeWithContentDescription("重试").performClick()

        assertEquals(5L, acted?.postId)
    }

    @Test
    fun `download-all is offered only while something is left to download`() {
        setScreen(
            state().copy(entries = entries.map { it.copy(offline = OfflineState.Downloaded(bytes = 1)) }),
        )

        composeRule.onNodeWithText("全部下载 · 0 篇").assertDoesNotExist()
    }
}
