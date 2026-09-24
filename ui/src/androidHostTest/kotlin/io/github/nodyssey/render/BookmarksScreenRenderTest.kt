package io.github.nodyssey.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.nodyssey.data.OfflineFailure
import io.github.nodyssey.data.OfflineState
import io.github.nodyssey.data.OfflineUsage
import io.github.nodyssey.ui.bookmarks.BookmarkEntry
import io.github.nodyssey.ui.bookmarks.BookmarksScreen
import io.github.nodyssey.ui.bookmarks.BookmarksUiState
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Board 8e — 收藏 with every download state on one screen, in both themes, plus the multi-select
 * form. The states are hand-built because the shipped offline library reports none of them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class BookmarksScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun Screen(
        darkTheme: Boolean,
        selection: Set<Long>? = null,
    ) {
        PlazaTheme(darkTheme = darkTheme) {
            BookmarksScreen(
                state =
                BookmarksUiState(
                    entries = ENTRIES,
                    isSyncing = false,
                    selection = selection,
                    selectionEstimateBytes = selection?.let { 4_823_449L },
                    offlineAvailable = true,
                    usage = OfflineUsage(posts = 18, textBytes = 12_000_000, imageBytes = 36_000_000, freeBytes = 3_435_973_836),
                ),
                onBack = {},
                onPostClick = {},
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
                onToggleSelection = {},
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

    @Test
    fun `bookmarks in light`() {
        composeRule.setContent { Screen(darkTheme = false) }

        composeRule.onRoot().captureRender("bookmarks-light")
    }

    @Test
    fun `bookmarks in dark`() {
        composeRule.setContent { Screen(darkTheme = true) }

        composeRule.onRoot().captureRender("bookmarks-dark")
    }

    @Test
    fun `bookmarks in multi-select`() {
        composeRule.setContent { Screen(darkTheme = false, selection = setOf(1L, 3L)) }

        composeRule.onRoot().captureRender("bookmarks-selection-light")
    }

    /** 离线管理 (i1), a sheet in a window of its own — hence the screen capture. */
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun `the offline sheet in light`() {
        composeRule.setContent { Screen(darkTheme = false) }
        composeRule.onNodeWithContentDescription("管理").performClick()
        composeRule.waitForIdle()

        captureScreenRoboImage(filePath = "build/outputs/renders/bookmarks-offline-sheet-light.png")
    }

    private companion object {
        val ENTRIES =
            listOf(
                BookmarkEntry(1, "四盘位 NAS 选 ZFS 还是 Btrfs？跑了一周的对比", "技术", "tech", "codemonkey", null, 88, "昨天", OfflineState.Stale(behindReplies = 6, bytes = 2_936_012)),
                BookmarkEntry(2, "搬瓦工 DC9 CN2 GIA 年付测评", "测评", "review", "轻舟", null, 214, "2天前", OfflineState.Downloading(progress = 0.42f)),
                BookmarkEntry(3, "出一台 HK 2C4G 年付，可小刀", "交易", "trade", "省钱达人", null, 17, "3天前", OfflineState.Downloading(progress = null)),
                BookmarkEntry(4, "Caddy 反代多站点配置备忘", "Dev", "dev", "kvm_fan", null, 9, "上周", OfflineState.NotDownloaded),
                BookmarkEntry(5, "周末去爬了趟梧桐山", "日常", "daily", "homelab_er", null, 31, "上周", OfflineState.Failed(OfflineFailure.Network)),
                BookmarkEntry(6, "低功耗 NAS 主板推荐，N100 还值得买吗", "技术", "tech", "省钱达人", null, 52, "上周", OfflineState.Downloaded(bytes = 1_782_579)),
            )
    }
}
