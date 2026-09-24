package io.github.nodyssey.ui.bookmarks

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.nodyssey.data.OfflineFailure
import io.github.nodyssey.data.OfflineState
import io.github.nodyssey.data.OfflineUsage
import io.github.plaza.core.net.SiteError
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 收藏 at the size the board was drawn at: a failed refresh keeps the stored rows, and in
 * multi-select a tap ticks a row rather than opening the thread.
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

    private fun state(selection: Set<Long>? = null) =
        BookmarksUiState(
            entries = entries,
            isSyncing = false,
            selection = selection,
            offlineAvailable = true,
            usage = OfflineUsage(posts = 2, textBytes = 1_000_000, imageBytes = 12_000_000, freeBytes = 3_435_973_836),
        )

    private fun setScreen(
        state: BookmarksUiState,
        onPostClick: (Long) -> Unit = {},
        onToggleSelection: (Long) -> Unit = {},
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
                    onStartSelection = {},
                    onEnterSelection = {},
                    onToggleSelection = onToggleSelection,
                    onToggleSelectAll = {},
                    onClearSelection = {},
                    onRemoveSelected = {},
                    onRestore = {},
                    onDownloadSelected = {},
                    onDownloadPending = {},
                    onRowOfflineAction = {},
                    onOfflineSettings = {},
                    onClearOffline = {},
                )
            }
        }
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
}
