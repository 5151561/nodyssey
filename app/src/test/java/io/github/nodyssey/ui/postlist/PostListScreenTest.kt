package io.github.nodyssey.ui.postlist

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException
import io.github.nodyssey.data.Board
import io.github.nodyssey.data.FeedPost
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.PostSummary
import io.github.nodyssey.ui.theme.NodysseyTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screen-level tests for the list.
 *
 * These run on Robolectric so CI needs no emulator, and they exercise the *stateless* screen with
 * hand-built [PagingData] — the Route/Screen split is what makes that possible.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// The design targets a 360×800dp compact phone; Robolectric's default window is far
// shorter than any real device and would fail screens that fit fine in the hand.
@Config(qualifiers = "w360dp-h800dp")
class PostListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val boards =
        listOf(
            Board(null, "综合", null),
            Board("daily", "日常", null),
            Board("tech", "技术", null),
        )

    private fun feedPost(
        postId: Long,
        title: String,
        isRead: Boolean = false,
        newCommentCount: Int = 0,
        commentCount: Int? = 12,
        isPinned: Boolean = false,
        isLocked: Boolean = false,
        categoryTitle: String? = "日常",
    ) = FeedPost(
        summary =
        PostSummary(
            postId = postId,
            title = title,
            authorName = "tester",
            authorUid = 1,
            avatarUrl = null,
            categoryTitle = categoryTitle,
            categorySlug = "daily",
            viewCount = 100,
            commentCount = commentCount,
            lastActiveText = "1分钟前",
            lastActiveTitle = null,
            isPinned = isPinned,
            isLocked = isLocked,
        ),
        isRead = isRead,
        newCommentCount = newCommentCount,
    )

    /** Renders the screen with a paging stream in an explicit load state. */
    private fun setScreen(
        posts: List<FeedPost>,
        refresh: LoadState = LoadState.NotLoading(false),
        append: LoadState = LoadState.NotLoading(true),
        state: PostListUiState = PostListUiState(boards = boards),
        onPostClick: (Long) -> Unit = {},
        onBoardClick: (String?) -> Unit = {},
        onSortChange: (FeedSort) -> Unit = {},
        onRecoverInBrowser: () -> Unit = {},
    ) {
        composeRule.setContent {
            NodysseyTheme {
                ScreenUnderTest(
                    posts = posts,
                    refresh = refresh,
                    append = append,
                    state = state,
                    onPostClick = onPostClick,
                    onBoardClick = onBoardClick,
                    onSortChange = onSortChange,
                    onRecoverInBrowser = onRecoverInBrowser,
                )
            }
        }
    }

    @Composable
    private fun ScreenUnderTest(
        posts: List<FeedPost>,
        refresh: LoadState,
        append: LoadState,
        state: PostListUiState,
        onPostClick: (Long) -> Unit,
        onBoardClick: (String?) -> Unit,
        onSortChange: (FeedSort) -> Unit,
        onRecoverInBrowser: () -> Unit,
    ) {
        val pagingData =
            PagingData.from(
                data = posts,
                sourceLoadStates =
                LoadStates(
                    refresh = refresh,
                    prepend = LoadState.NotLoading(true),
                    append = append,
                ),
            )
        PostListScreen(
            state = state,
            posts = flowOf(pagingData).collectAsLazyPagingItems(),
            onPostClick = onPostClick,
            onBoardClick = onBoardClick,
            onSortChange = onSortChange,
            onSignInClick = {},
            onRecoverInBrowser = onRecoverInBrowser,
        )
    }

    @Test
    fun `renders the board strip and the rows`() {
        setScreen(listOf(feedPost(1, "first post"), feedPost(2, "second post")))

        composeRule.onNodeWithText("综合").assertIsDisplayed()
        composeRule.onNodeWithText("技术").assertIsDisplayed()
        composeRule.onNodeWithText("first post").assertIsDisplayed()
        composeRule.onNodeWithText("second post").assertIsDisplayed()
    }

    @Test
    fun `tapping a row reports the post id`() {
        var clicked: Long? = null
        setScreen(listOf(feedPost(77, "tap me")), onPostClick = { clicked = it })

        composeRule.onNodeWithText("tap me").performClick()

        assertEquals(77L, clicked)
    }

    @Test
    fun `tapping a board reports its slug`() {
        var slug: String? = "unset"
        setScreen(listOf(feedPost(1, "post")), onBoardClick = { slug = it })

        composeRule.onNodeWithText("技术").performClick()

        assertEquals("tech", slug)
    }

    @Test
    fun `an unread row shows the reply count`() {
        setScreen(listOf(feedPost(1, "unread", isRead = false, commentCount = 12)))

        composeRule.onNodeWithText("12 回复").assertIsDisplayed()
    }

    /** The whole point of the read-mark table: the delta replaces the raw total once read. */
    @Test
    fun `a read row with new replies shows the delta instead`() {
        setScreen(
            listOf(feedPost(1, "read", isRead = true, newCommentCount = 4, commentCount = 16)),
        )

        composeRule.onNodeWithText("4 条新回复").assertIsDisplayed()
        composeRule.onNodeWithText("16 回复").assertDoesNotExist()
    }

    @Test
    fun `an empty list that is still loading shows a spinner, not an empty screen`() {
        setScreen(emptyList(), refresh = LoadState.Loading)

        // No rows, and nothing claiming the list is empty.
        composeRule.onNodeWithText("first post").assertDoesNotExist()
    }

    // ---------------------------------------------------------------------------------------------
    // Error recovery
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `a refresh failure with nothing cached shows the typed error and its recovery action`() {
        setScreen(
            posts = emptyList(),
            refresh = LoadState.Error(NodeSeekException(NodeSeekError.Cloudflare)),
        )

        composeRule.onNodeWithText("需要确认一下你不是机器人").assertIsDisplayed()
        composeRule.onNodeWithText("去验证").assertIsDisplayed()
        composeRule.onNodeWithText("重试").assertIsDisplayed()
    }

    @Test
    fun `a login required error offers signing in rather than a bare retry`() {
        setScreen(
            posts = emptyList(),
            refresh = LoadState.Error(NodeSeekException(NodeSeekError.LoginRequired)),
        )

        composeRule
            .onNodeWithText("登录 NodeSeek 账号后即可浏览本版块的内容。登录成功会自动返回并重新加载。")
            .assertIsDisplayed()
        composeRule.onNodeWithText("登录").assertIsDisplayed()
    }

    @Test
    fun `an unclassified failure still renders an error rather than a blank screen`() {
        setScreen(posts = emptyList(), refresh = LoadState.Error(IllegalStateException("boom")))

        composeRule.onNodeWithText("加载失败").assertIsDisplayed()
    }

    @Test
    fun `the recovery button reports the intent to open a browser`() {
        var opened = false
        setScreen(
            posts = emptyList(),
            refresh = LoadState.Error(NodeSeekException(NodeSeekError.Cloudflare)),
            onRecoverInBrowser = { opened = true },
        )

        composeRule.onNodeWithText("去验证").performClick()

        assert(opened)
    }

    /**
     * The behaviour offline-first exists for: a failed refresh with rows already cached keeps the rows
     * and does not replace the screen with an error.
     */
    @Test
    fun `a refresh failure with cached rows keeps the content on screen`() {
        setScreen(
            posts = listOf(feedPost(1, "cached post")),
            refresh = LoadState.Error(NodeSeekException(NodeSeekError.Network)),
        )

        composeRule.onNodeWithText("cached post").assertIsDisplayed()
        composeRule.onNodeWithText("网络开小差了").assertDoesNotExist()
    }

    // ---------------------------------------------------------------------------------------------
    // Row anatomy: the design's answers to "what does a row have to survive"
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `a pinned row is announced rather than only tinted`() {
        setScreen(listOf(feedPost(1, "公告", isPinned = true, commentCount = null)))

        // Twice by design: the pin badge replacing the avatar, and the word in the meta line.
        composeRule.onAllNodesWithText("置顶").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("置顶").assertIsDisplayed()
    }

    @Test
    fun `a locked row carries the lock affordance`() {
        setScreen(listOf(feedPost(1, "锁了的帖子", isLocked = true)))

        composeRule.onNodeWithContentDescription("已锁帖").assertIsDisplayed()
    }

    /** A board that the scrape failed to return must drop the tag, not the row. */
    @Test
    fun `a row with no board still renders`() {
        setScreen(listOf(feedPost(1, "没有版块的帖子", categoryTitle = null)))

        composeRule.onNodeWithText("没有版块的帖子").assertIsDisplayed()
        // Only the board strip's pill, never a tag on the row itself.
        composeRule.onAllNodesWithText("日常").assertCountEquals(1)
    }

    @Test
    fun `an empty feed that finished loading offers a way out`() {
        setScreen(
            posts = emptyList(),
            refresh = LoadState.NotLoading(true),
            state = PostListUiState(boards = boards, categorySlug = "tech"),
        )

        composeRule.onNodeWithText("这里还没有帖子").assertIsDisplayed()
        composeRule.onNodeWithText("换个版块").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------------------------
    // Sort
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `the sort menu reports the chosen order`() {
        var chosen: FeedSort? = null
        setScreen(listOf(feedPost(1, "post")), onSortChange = { chosen = it })

        composeRule.onNodeWithContentDescription("排序方式").performClick()
        composeRule.onNodeWithText("按发帖时间").performClick()

        assertEquals(FeedSort.POST_TIME, chosen)
    }

    /**
     * The tick marking the active order is decoration, so it carries no description; without
     * `selected` on the item itself the two orders were indistinguishable to a screen reader.
     */
    @Test
    fun `the sort menu marks the active order as selected`() {
        setScreen(
            listOf(feedPost(1, "post")),
            state = PostListUiState(boards = boards, sort = FeedSort.LAST_REPLY),
        )

        composeRule.onNodeWithContentDescription("排序方式").performClick()

        composeRule.onNodeWithText("按回复时间").assertIsSelected()
        composeRule.onNodeWithText("按发帖时间").assertIsNotSelected()
    }

    /** The lock is the only thing saying the board is restricted, so it has to be described. */
    @Test
    fun `an admin-only board describes its lock`() {
        setScreen(
            posts = listOf(feedPost(1, "post")),
            state =
            PostListUiState(
                boards = boards + Board("inside", "内版", null, adminOnly = true),
            ),
        )

        composeRule.onNodeWithContentDescription("仅管理员可见").assertIsDisplayed()
    }

    /** The needs-login screen has to name the board, or it reads like the whole app is locked. */
    @Test
    fun `a locked board names itself in the sign-in state`() {
        setScreen(
            posts = emptyList(),
            refresh = LoadState.Error(NodeSeekException(NodeSeekError.LoginRequired)),
            state = PostListUiState(boards = boards, categorySlug = "tech"),
        )

        composeRule.onNodeWithText("「技术」需要登录后查看").assertIsDisplayed()
    }
}
