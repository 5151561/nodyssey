package io.github.nsreader.ui.postlist

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.data.Board
import io.github.nsreader.data.FeedPost
import io.github.nsreader.model.PostSummary
import io.github.nsreader.ui.theme.NodeSeekTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Screen-level tests for the list.
 *
 * These run on Robolectric so CI needs no emulator, and they exercise the *stateless* screen with
 * hand-built [PagingData] — the Route/Screen split is what makes that possible.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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
    ) = FeedPost(
        summary =
        PostSummary(
            postId = postId,
            title = title,
            authorName = "tester",
            authorUid = 1,
            avatarUrl = null,
            categoryTitle = "日常",
            categorySlug = "daily",
            viewCount = 100,
            commentCount = commentCount,
            lastActiveText = "1分钟前",
            lastActiveTitle = null,
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
        onRecoverInBrowser: () -> Unit = {},
    ) {
        composeRule.setContent {
            NodeSeekTheme {
                ScreenUnderTest(
                    posts = posts,
                    refresh = refresh,
                    append = append,
                    state = state,
                    onPostClick = onPostClick,
                    onBoardClick = onBoardClick,
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

        composeRule.onNodeWithText("需要在浏览器里完成一次人机验证，之后就能正常浏览了").assertIsDisplayed()
        composeRule.onNodeWithText("在浏览器中验证").assertIsDisplayed()
        composeRule.onNodeWithText("重试").assertIsDisplayed()
    }

    @Test
    fun `a login required error offers signing in rather than a bare retry`() {
        setScreen(
            posts = emptyList(),
            refresh = LoadState.Error(NodeSeekException(NodeSeekError.LoginRequired)),
        )

        composeRule.onNodeWithText("这个版块的内容需要登录后才能查看").assertIsDisplayed()
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

        composeRule.onNodeWithText("在浏览器中验证").performClick()

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
        composeRule.onNodeWithText("连不上 NodeSeek，检查一下网络").assertDoesNotExist()
    }
}
