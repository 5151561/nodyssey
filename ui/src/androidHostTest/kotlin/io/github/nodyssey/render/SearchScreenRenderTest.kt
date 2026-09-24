package io.github.nodyssey.render

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.nodyssey.data.Board
import io.github.nodyssey.data.FeedPost
import io.github.nodyssey.data.UserSearchResult
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.PostSummary
import io.github.nodyssey.model.SearchHistoryEntry
import io.github.nodyssey.model.SearchTarget
import io.github.nodyssey.ui.search.SearchLoadState
import io.github.nodyssey.ui.search.SearchScreen
import io.github.nodyssey.ui.search.SearchUiState
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 搜索 in its three states — before a search, post results, user results — plus 高级搜索 and the
 * sort menu open over the results, in both themes.
 *
 * Avatars are `null` for the reason [PostListScreenRenderTest] gives: the JVM has no network, and
 * the monogram fallback renders deterministically.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class SearchScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun Screen(
        state: SearchUiState,
        query: String,
        darkTheme: Boolean,
        posts: List<FeedPost> = POSTS,
    ) {
        PlazaTheme(darkTheme = darkTheme) {
            val results =
                remember {
                    flowOf(
                        PagingData.from(
                            data = posts,
                            sourceLoadStates =
                            LoadStates(
                                refresh = LoadState.NotLoading(false),
                                prepend = LoadState.NotLoading(true),
                                append = LoadState.NotLoading(true),
                            ),
                        ),
                    )
                }
            SearchScreen(
                state = state,
                postResults = results.collectAsLazyPagingItems(),
                queryState = rememberTextFieldState(query),
                onSearch = {},
                onTargetChange = {},
                onHistoryClick = {},
                onRemoveHistory = {},
                onClearHistory = {},
                onPostClick = {},
                onUserClick = {},
                onRetry = {},
                onSignIn = {},
                onVerify = {},
                onBack = {},
            )
        }
    }

    private fun render(
        name: String,
        state: SearchUiState,
        query: String,
    ) {
        composeRule.setContent { Screen(state, query, darkTheme = name.endsWith("dark")) }
        composeRule.onRoot().captureRender(name)
    }

    @Test
    fun `before a search in light`() = render("search-setup-light", SETUP, "")

    @Test
    fun `before a search in dark`() = render("search-setup-dark", SETUP, "")

    @Test
    fun `post results in light`() = render("search-posts-light", POST_RESULTS, "NAS")

    @Test
    fun `post results in dark`() = render("search-posts-dark", POST_RESULTS, "NAS")

    /** A result the reader has opened before and that has had replies since, as the feed marks it. */
    @Test
    fun `post results with new replies in light`() {
        val posts = POSTS.map { if (it.summary.postId == 4L) it.copy(newCommentCount = 3) else it }
        composeRule.setContent { Screen(POST_RESULTS, "NAS", darkTheme = false, posts = posts) }
        composeRule.onRoot().captureRender("search-posts-new-replies-light")
    }

    @Test
    fun `user results in light`() = render("search-users-light", USER_RESULTS, "NAS")

    @Test
    fun `user results in dark`() = render("search-users-dark", USER_RESULTS, "NAS")

    /**
     * 高级搜索 and the sort menu open in windows of their own, which `onRoot()` does not see; the
     * screen capture takes every window, the way the device would show it.
     */
    private fun renderOpened(
        name: String,
        open: () -> Unit,
    ) {
        composeRule.setContent { Screen(POST_RESULTS, "NAS", darkTheme = name.endsWith("dark")) }
        open()
        composeRule.waitForIdle()
        captureScreenRoboImage("build/outputs/renders/$name.png")
    }

    @Test
    fun `advanced search in light`() =
        renderOpened("search-advanced-light") { composeRule.onNodeWithContentDescription("高级搜索").performClick() }

    @Test
    fun `advanced search in dark`() =
        renderOpened("search-advanced-dark") { composeRule.onNodeWithContentDescription("高级搜索").performClick() }

    @Test
    fun `sort menu in light`() = renderOpened("search-sort-light") { composeRule.onNodeWithText("按回复时间").performClick() }

    @Test
    fun `sort menu in dark`() = renderOpened("search-sort-dark") { composeRule.onNodeWithText("按回复时间").performClick() }

    private companion object {
        val BOARDS =
            listOf(
                Board("daily", "日常", null),
                Board("tech", "技术", null),
                Board("info", "情报", null),
                Board("review", "测评", null),
                Board("trade", "交易", null),
            )

        val SETUP =
            SearchUiState(
                boards = BOARDS,
                searchHistory =
                listOf(
                    SearchHistoryEntry("NAS 电源", SearchTarget.POSTS),
                    SearchHistoryEntry("黑五 机场", SearchTarget.POSTS, categorySlug = "info"),
                    SearchHistoryEntry("腾讯云轻量", SearchTarget.POSTS, categorySlug = "trade"),
                    SearchHistoryEntry("homelab_er", SearchTarget.USERS),
                ),
            )

        val POST_RESULTS =
            SearchUiState(
                submittedQuery = "NAS",
                boards = BOARDS,
                selectedBoard = "tech",
                sort = FeedSort.LAST_REPLY,
                recentBoards = listOf("tech", "info"),
            )

        val USER_RESULTS =
            POST_RESULTS.copy(
                target = SearchTarget.USERS,
                userLoadState = SearchLoadState.Success,
                userResults =
                listOf(
                    user(30412, "nas_daily", 2, 12, 486, "2025年1月"),
                    user(21736, "homelab_nas", 1, 8, 203, "2024年3月"),
                    user(27790, "小NAS盒子", 3, 41, 1120, "2024年9月"),
                    user(33105, "NAS_kid", 1, 0, 17, "2026年6月"),
                    user(15208, "群晖NAS玩家", 4, 96, 2317, "2023年5月"),
                    user(19944, "thenasguy", 2, 23, 655, "2023年12月"),
                ),
            )

        private fun user(
            uid: Long,
            name: String,
            level: Int,
            topics: Int,
            comments: Int,
            joined: String,
        ) = UserSearchResult(
            uid = uid,
            name = name,
            avatarUrl = null,
            level = level,
            bio = null,
            topicCount = topics,
            commentCount = comments,
            joinedText = joined,
        )

        private fun post(
            id: Long,
            title: String,
            author: String,
            comments: Int,
            lastActive: String,
            read: Boolean = false,
        ) = FeedPost(
            summary =
            PostSummary(
                postId = id,
                title = title,
                authorName = author,
                authorUid = id,
                avatarUrl = null,
                categoryTitle = "技术",
                categorySlug = "tech",
                viewCount = 1000,
                commentCount = comments,
                lastActiveText = lastActive,
                lastActiveTitle = null,
                isPinned = false,
                isLocked = false,
            ),
            isRead = read,
            newCommentCount = 0,
        )

        val POSTS =
            listOf(
                post(1, "自建 NAS 一年，聊聊我踩过的那些坑和真香时刻", "homelab_er", 42, "刚刚"),
                post(2, "四盘位 NAS 选 ZFS 还是 Btrfs？跑了一周的对比", "codemonkey", 88, "2 天前"),
                post(3, "NAS 硬盘休眠后唤醒太慢，有没有折中的方案", "kvm_fan", 27, "3 天前"),
                post(4, "低功耗 NAS 主板推荐，N100 还值得买吗", "省钱达人", 19, "5 天前", read = true),
                post(5, "异地备份：两台 NAS 之间用什么同步最省心", "轻舟", 34, "1 周前", read = true),
            )
    }
}
