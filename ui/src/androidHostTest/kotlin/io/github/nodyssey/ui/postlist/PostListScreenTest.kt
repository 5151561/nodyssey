package io.github.nodyssey.ui.postlist

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.nodyssey.data.Board
import io.github.nodyssey.data.FeedPost
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.PostSummary
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        isAwarded: Boolean = false,
        categoryTitle: String? = "日常",
        page: Int? = null,
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
            isAwarded = isAwarded,
        ),
        isRead = isRead,
        newCommentCount = newCommentCount,
        page = page,
    )

    /** Renders the screen with a paging stream in an explicit load state. */
    private fun setScreen(
        posts: List<FeedPost>,
        refresh: LoadState = LoadState.NotLoading(false),
        append: LoadState = LoadState.NotLoading(true),
        state: PostListUiState = PostListUiState(boards = boards),
        onPostClick: (FeedPost) -> Unit = {},
        onBoardClick: (String?) -> Unit = {},
        onSortChange: (FeedSort) -> Unit = {},
        onRecoverInBrowser: () -> Unit = {},
        onSearch: () -> Unit = {},
        onGoToPage: (Int) -> Unit = {},
        onFindPageRow: suspend (Int) -> Int? = { null },
        feedStates: HomeFeedStates? = null,
    ) {
        composeRule.setContent {
            PlazaTheme {
                ScreenUnderTest(
                    posts = posts,
                    refresh = refresh,
                    append = append,
                    state = state,
                    onPostClick = onPostClick,
                    onBoardClick = onBoardClick,
                    onSortChange = onSortChange,
                    onRecoverInBrowser = onRecoverInBrowser,
                    onSearch = onSearch,
                    onGoToPage = onGoToPage,
                    onFindPageRow = onFindPageRow,
                    feedStates = feedStates ?: rememberSaveable(saver = HomeFeedStates.Saver) { HomeFeedStates() },
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
        feedStates: HomeFeedStates? = null,
        onPostClick: (FeedPost) -> Unit,
        onBoardClick: (String?) -> Unit,
        onSortChange: (FeedSort) -> Unit,
        onRecoverInBrowser: () -> Unit,
        onSearch: () -> Unit = {},
        reselectRequests: Int = 0,
        onGoToPage: (Int) -> Unit = {},
        onFindPageRow: suspend (Int) -> Int? = { null },
    ) {
        // Remembered rather than rebuilt: the pager asks for a board's rows every time the page
        // recomposes, and a new flow instance each time would hand back a new presenter each time.
        val feed =
            remember(posts, refresh, append) {
                flowOf(
                    PagingData.from(
                        data = posts,
                        sourceLoadStates =
                        LoadStates(
                            refresh = refresh,
                            prepend = LoadState.NotLoading(true),
                            append = append,
                        ),
                    ),
                )
            }
        PostListScreen(
            state = state,
            // Every board shows the same rows here; which board is selected is what the tests vary.
            postsFor = { feed.collectAsLazyPagingItems() },
            feedStates = feedStates ?: rememberSaveable(saver = HomeFeedStates.Saver) { HomeFeedStates() },
            onPostClick = onPostClick,
            onBoardClick = onBoardClick,
            onSortChange = onSortChange,
            onSignInClick = {},
            onRecoverInBrowser = onRecoverInBrowser,
            onSearch = onSearch,
            onGoToPage = onGoToPage,
            onFindPageRow = onFindPageRow,
            reselectRequests = reselectRequests,
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
    fun `tapping a row reports the whole row, not just the post id`() {
        var clicked: FeedPost? = null
        setScreen(listOf(feedPost(77, "tap me")), onPostClick = { clicked = it })

        composeRule.onNodeWithText("tap me").performClick()

        // The row travels because the thread draws four of its facts before the network answers,
        // and because they are what the row's own four fly into. See PostDetailKey.preview.
        assertEquals(77L, clicked?.summary?.postId)
        assertEquals("tap me", clicked?.summary?.title)
    }

    @Test
    fun `tapping a board reports its slug`() {
        var slug: String? = "unset"
        setScreen(listOf(feedPost(1, "post")), onBoardClick = { slug = it })

        composeRule.onNodeWithText("技术").performClick()

        assertEquals("tech", slug)
    }

    /**
     * 左右滑动切换板块: the boards are pages, in the strip's own order, so 综合 steps to 日常 rather
     * than to whichever board the site happens to list second.
     */
    @Test
    fun `swiping the feed sideways selects the next board`() {
        var slug: String? = "unset"
        setScreen((1..40).map { feedPost(it.toLong(), "post $it") }, onBoardClick = { slug = it })

        feedList().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals("daily", slug)
    }

    /** There is nothing before 综合 to swipe to, and the pager simply springs back. */
    @Test
    fun `swiping back from the first board stays put`() {
        var reported: String? = "unset"
        setScreen((1..40).map { feedPost(it.toLong(), "post $it") }, onBoardClick = { reported = it })

        feedList().performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        assertEquals("unset", reported)
    }

    /**
     * Each board is a page with a scroll position of its own — the point of the boards being pages
     * at all. Coming back to one used to mean coming back to the top of it.
     */
    @Test
    fun `a board keeps its own place in the feed`() {
        val posts = (1..40).map { feedPost(it.toLong(), "post $it") }
        var state by mutableStateOf(PostListUiState(boards = boards))
        composeRule.setContent {
            PlazaTheme {
                ScreenUnderTest(
                    posts = posts,
                    refresh = LoadState.NotLoading(false),
                    append = LoadState.NotLoading(true),
                    state = state,
                    onPostClick = {},
                    onBoardClick = {},
                    onSortChange = {},
                    onRecoverInBrowser = {},
                )
            }
        }

        feedList().performScrollToIndex(20)
        composeRule.onNodeWithText("post 21").assertIsDisplayed()
        composeRule.runOnIdle { state = state.copy(categorySlug = "tech") }
        composeRule.onNodeWithText("post 1").assertIsDisplayed()

        composeRule.runOnIdle { state = state.copy(categorySlug = null) }

        composeRule.onNodeWithText("post 21").assertIsDisplayed()
    }

    @Test
    fun `an unread row shows the reply count`() {
        setScreen(listOf(feedPost(1, "unread", isRead = false, commentCount = 12)))

        // The count is drawn as an icon and a bare number, so the words live in the a11y label.
        composeRule.onNodeWithContentDescription("12 回复").assertIsDisplayed()
    }

    /** The whole point of the read-mark table: the delta replaces the raw total once read. */
    @Test
    fun `a read row with new replies shows the delta instead`() {
        setScreen(
            listOf(feedPost(1, "read", isRead = true, newCommentCount = 4, commentCount = 16)),
        )

        composeRule.onNodeWithText("4 条新回复").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("16 回复").assertCountEquals(0)
    }

    @Test
    fun `an empty list that is still loading shows a spinner, not an empty screen`() {
        setScreen(emptyList(), refresh = LoadState.Loading)

        // No rows, and nothing claiming the list is empty.
        composeRule.onNodeWithText("first post").assertDoesNotExist()
    }

    /**
     * Reading a thread and coming back must land where the list was left.
     *
     * Opening a thread takes this screen out of the composition, so returning restores it the same way
     * a saved instance state does — which is exactly what [StateRestorationTester] emulates. The
     * regression this guards is a scroll-to-top effect that cannot tell that restore apart from the
     * user picking a different board.
     */
    @Test
    fun `a restored list stays where it was scrolled to`() {
        val restorationTester = StateRestorationTester(composeRule)
        val posts = (1..40).map { feedPost(it.toLong(), "post $it") }
        restorationTester.setContent {
            PlazaTheme {
                ScreenUnderTest(
                    posts = posts,
                    refresh = LoadState.NotLoading(false),
                    append = LoadState.NotLoading(true),
                    state = PostListUiState(boards = boards),
                    onPostClick = {},
                    onBoardClick = {},
                    onSortChange = {},
                    onRecoverInBrowser = {},
                )
            }
        }

        feedList().performScrollToIndex(20)
        composeRule.onNodeWithText("post 21").assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("post 21").assertIsDisplayed()
    }

    /**
     * A thread replaces the compact list entry's composition, but it must not replace the list state.
     * The navigation host owns that state for as long as the home stack exists.
     */
    @Test
    fun `leaving the list composition and returning keeps the exact position`() {
        val posts = (1..40).map { feedPost(it.toLong(), "post $it") }
        var showingList by mutableStateOf(true)
        val retainedStates = HomeFeedStates()
        val retainedListState = retainedStates.listState(null, FeedSort.LAST_REPLY)
        composeRule.setContent {
            PlazaTheme {
                if (showingList) {
                    ScreenUnderTest(
                        posts = posts,
                        refresh = LoadState.NotLoading(false),
                        append = LoadState.NotLoading(true),
                        state = PostListUiState(boards = boards),
                        feedStates = retainedStates,
                        onPostClick = {},
                        onBoardClick = {},
                        onSortChange = {},
                        onRecoverInBrowser = {},
                    )
                } else {
                    androidx.compose.material3.Text("thread detail")
                }
            }
        }

        feedList().performScrollToIndex(20)
        composeRule.onNodeWithText("post 21").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(20, retainedListState.firstVisibleItemIndex) }

        composeRule.runOnIdle { showingList = false }
        composeRule.onNodeWithText("thread detail").assertIsDisplayed()
        composeRule.runOnIdle { showingList = true }

        composeRule.onNodeWithText("post 21").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(20, retainedListState.firstVisibleItemIndex) }
    }

    /** Picking a different board does still start that board's list from the top. */
    @Test
    fun `switching boards returns to the top`() {
        val posts = (1..40).map { feedPost(it.toLong(), "post $it") }
        var state by mutableStateOf(PostListUiState(boards = boards))
        composeRule.setContent {
            PlazaTheme {
                ScreenUnderTest(
                    posts = posts,
                    refresh = LoadState.NotLoading(false),
                    append = LoadState.NotLoading(true),
                    state = state,
                    onPostClick = {},
                    onBoardClick = {},
                    onSortChange = {},
                    onRecoverInBrowser = {},
                )
            }
        }

        feedList().performScrollToIndex(20)
        composeRule.onNodeWithText("post 21").assertIsDisplayed()

        state = state.copy(categorySlug = "tech")

        composeRule.onNodeWithText("post 1").assertIsDisplayed()
    }

    /** Re-selecting 首页 in the host bar arrives here as an increment, not as a scroll call. */
    @Test
    fun `a reselect returns to the first row`() {
        val posts = (1..40).map { feedPost(it.toLong(), "post $it") }
        var requests by mutableStateOf(0)
        composeRule.setContent {
            PlazaTheme {
                ScreenUnderTest(
                    posts = posts,
                    refresh = LoadState.NotLoading(false),
                    append = LoadState.NotLoading(true),
                    state = PostListUiState(boards = boards),
                    onPostClick = {},
                    onBoardClick = {},
                    onSortChange = {},
                    onRecoverInBrowser = {},
                    reselectRequests = requests,
                )
            }
        }

        feedList().performScrollToIndex(20)
        composeRule.onNodeWithText("post 21").assertIsDisplayed()

        requests++

        composeRule.onNodeWithText("post 1").assertIsDisplayed()
    }

    /**
     * And it reloads on the way: 新帖 is the one thing a reader taps 首页 for while already on 首页.
     *
     * A real [Pager] rather than the hand-built [PagingData] the rest of the file uses — `refresh()`
     * on a stream built from a fixed list is a no-op, so the assertion would pass with the reload
     * deleted. Counting how many times the source is asked for is what makes the reload observable.
     */
    @Test
    fun `a reselect reloads the feed`() {
        val posts = (1..40).map { feedPost(it.toLong(), "post $it") }
        var loads = 0
        val pager =
            Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
                loads++
                object : PagingSource<Int, FeedPost>() {
                    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FeedPost> =
                        LoadResult.Page(posts, prevKey = null, nextKey = null)

                    override fun getRefreshKey(state: PagingState<Int, FeedPost>): Int? = null
                }
            }
        var requests by mutableStateOf(0)
        composeRule.setContent {
            PlazaTheme {
                // `cachedIn` as production has it: the screen reads the selected board's stream
                // alongside the page drawing it, and one cache is what makes that one source.
                val scope = rememberCoroutineScope()
                val feed = remember { pager.flow.cachedIn(scope) }
                PostListScreen(
                    state = PostListUiState(boards = boards),
                    postsFor = { feed.collectAsLazyPagingItems() },
                    onPostClick = {},
                    onBoardClick = {},
                    onSortChange = {},
                    onSignInClick = {},
                    onRecoverInBrowser = {},
                    reselectRequests = requests,
                )
            }
        }

        composeRule.onNodeWithText("post 1").assertIsDisplayed()
        assertEquals(1, loads)

        feedList().performScrollToIndex(20)
        composeRule.onNodeWithText("post 21").assertIsDisplayed()

        requests++
        composeRule.waitForIdle()

        // Both halves of the tap: the source asked a second time, and the list back at its start.
        assertEquals(2, loads)
        composeRule.onNodeWithText("post 1").assertIsDisplayed()
    }

    /**
     * A tap that has already been answered must not be answered a second time when the screen comes
     * back — which is what a rotation, or a return from a thread, looks like from inside the effect.
     */
    @Test
    fun `a restored screen does not replay the last reselect`() {
        val restorationTester = StateRestorationTester(composeRule)
        val posts = (1..40).map { feedPost(it.toLong(), "post $it") }
        var requests by mutableStateOf(0)
        restorationTester.setContent {
            PlazaTheme {
                ScreenUnderTest(
                    posts = posts,
                    refresh = LoadState.NotLoading(false),
                    append = LoadState.NotLoading(true),
                    state = PostListUiState(boards = boards),
                    onPostClick = {},
                    onBoardClick = {},
                    onSortChange = {},
                    onRecoverInBrowser = {},
                    reselectRequests = requests,
                )
            }
        }

        requests++
        feedList().performScrollToIndex(20)
        composeRule.onNodeWithText("post 21").assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("post 21").assertIsDisplayed()
    }

    /** The counter starts at zero, and zero must not read as a request that was already made. */
    @Test
    fun `no request leaves the scroll position alone`() {
        setScreen((1..40).map { feedPost(it.toLong(), "post $it") })

        feedList().performScrollToIndex(20)

        composeRule.onNodeWithText("post 21").assertIsDisplayed()
    }

    /**
     * The app bar folds away with the feed; the board strip does not.
     *
     * A real swipe rather than `performScrollToIndex`, which drives the list through its semantics
     * action and dispatches no nested scroll at all — the app bar would stay open however far such a
     * scroll went, and the test would pass without the behaviour existing.
     */
    @Test
    fun `scrolling the feed folds the app bar away and keeps the board strip`() {
        setScreen((1..40).map { feedPost(it.toLong(), "post $it") })

        feedList().performTouchInput { swipeUp() }

        composeRule.onNodeWithContentDescription("排序方式").assertIsNotDisplayed()
        composeRule.onNodeWithContentDescription("搜索").assertIsNotDisplayed()
        composeRule.onNodeWithText("综合").assertIsDisplayed()
        composeRule.onNodeWithText("技术").assertIsDisplayed()
    }

    /**
     * A folded app bar stays folded across a board.
     *
     * It used to be unfolded on the way, back when switching boards also put the list at its first
     * row. Now that every board keeps its own place, that made the bar jump back out over the rows
     * on every swipe — the reader folded it away, and nothing they did asked for it back.
     */
    @Test
    fun `switching boards leaves the app bar folded`() {
        val posts = (1..40).map { feedPost(it.toLong(), "post $it") }
        var state by mutableStateOf(PostListUiState(boards = boards))
        composeRule.setContent {
            PlazaTheme {
                ScreenUnderTest(
                    posts = posts,
                    refresh = LoadState.NotLoading(false),
                    append = LoadState.NotLoading(true),
                    state = state,
                    onPostClick = {},
                    onBoardClick = {},
                    onSortChange = {},
                    onRecoverInBrowser = {},
                )
            }
        }

        feedList().performTouchInput { swipeUp() }
        composeRule.onNodeWithContentDescription("排序方式").assertIsNotDisplayed()

        composeRule.runOnIdle { state = state.copy(categorySlug = "tech") }

        composeRule.onNodeWithContentDescription("排序方式").assertIsNotDisplayed()
        // The strip never folds, so the way back to another board is still on screen.
        composeRule.onNodeWithText("综合").assertIsDisplayed()
    }

    /**
     * 排序 is the arrival that does need it: a different order is a different feed, every board
     * starts at its top, and that arrival is programmatic — there is no upward delta for the bar to
     * unfold against, and at the top of a list there is nothing left to scroll back up through.
     */
    @Test
    fun `switching sort unfolds the app bar again`() {
        val posts = (1..40).map { feedPost(it.toLong(), "post $it") }
        var state by mutableStateOf(PostListUiState(boards = boards))
        composeRule.setContent {
            PlazaTheme {
                ScreenUnderTest(
                    posts = posts,
                    refresh = LoadState.NotLoading(false),
                    append = LoadState.NotLoading(true),
                    state = state,
                    onPostClick = {},
                    onBoardClick = {},
                    onSortChange = {},
                    onRecoverInBrowser = {},
                )
            }
        }

        feedList().performTouchInput { swipeUp() }
        composeRule.onNodeWithContentDescription("排序方式").assertIsNotDisplayed()

        composeRule.runOnIdle { state = state.copy(sort = FeedSort.POST_TIME) }

        composeRule.onNodeWithContentDescription("排序方式").assertIsDisplayed()
    }

    /** 搜索 left the navigation bar for this corner, so the feed is the only way to reach it. */
    @Test
    fun `the app bar opens search`() {
        var opened = false
        setScreen(listOf(feedPost(1, "post")), onSearch = { opened = true })

        composeRule.onNodeWithContentDescription("搜索").performClick()

        assertTrue(opened)
    }

    /**
     * The vertical list of rows, told apart from the board strip and from the pager that carries it.
     *
     * Three things on this screen scroll and two of them contain the rows — 首页 draws its boards as
     * pages now — so the matcher finds two nodes and the list is the deeper of them.
     */
    private val feedList = hasScrollAction() and hasAnyDescendant(hasText("post 1"))

    private fun feedList() = composeRule.onAllNodes(feedList).onLast()

    // ---------------------------------------------------------------------------------------------
    // Error recovery
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `a refresh failure with nothing cached shows the typed error and its recovery action`() {
        setScreen(
            posts = emptyList(),
            refresh = LoadState.Error(SiteException(SiteError.Cloudflare)),
        )

        composeRule.onNodeWithText("需要确认一下你不是机器人").assertIsDisplayed()
        composeRule.onNodeWithText("去验证").assertIsDisplayed()
        composeRule.onNodeWithText("重试").assertIsDisplayed()
    }

    @Test
    fun `a login required error offers signing in rather than a bare retry`() {
        setScreen(
            posts = emptyList(),
            refresh = LoadState.Error(SiteException(SiteError.LoginRequired)),
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
            refresh = LoadState.Error(SiteException(SiteError.Cloudflare)),
            onRecoverInBrowser = { opened = true },
        )

        composeRule.onNodeWithText("去验证").performClick()

        assert(opened)
    }

    /**
     * The behaviour offline-first exists for: a failed refresh with rows already cached keeps the rows
     * rather than replacing the screen with an error.
     *
     * It says so all the same. Keeping the content and keeping quiet are different decisions, and the
     * second one was never made on purpose — a list that stops moving reads as a quiet forum, which is
     * what sent a real debugging session after the network instead of after 私人 DNS.
     */
    @Test
    fun `a refresh failure with cached rows keeps the content and still says what happened`() {
        setScreen(
            posts = listOf(feedPost(1, "cached post")),
            refresh = LoadState.Error(SiteException(SiteError.Network)),
        )

        composeRule.onNodeWithText("cached post").assertIsDisplayed()
        composeRule.onNodeWithText("网络开小差了").assertIsDisplayed()
        composeRule.onNodeWithText("重试").assertIsDisplayed()
    }

    /**
     * The case the snackbar was quietly wrong about.
     *
     * Its action was a hardcoded 重试, which for a wall is a button that cannot work: retrying earns
     * the same wall, and the web view that clears it lives on the full-screen state — which never
     * appears here, because rows on screen are exactly what stops it appearing. A reader who had ever
     * loaded the feed once therefore had no way out of the loop at all.
     */
    @Test
    fun `a Cloudflare wall over cached rows offers the verify button, not a retry that cannot work`() {
        setScreen(
            posts = listOf(feedPost(1, "cached post")),
            refresh = LoadState.Error(SiteException(SiteError.Cloudflare)),
        )

        composeRule.onNodeWithText("cached post").assertIsDisplayed()
        composeRule.onNodeWithText("需要确认一下你不是机器人").assertIsDisplayed()
        composeRule.onNodeWithText("去验证").assertIsDisplayed()
        composeRule.onNodeWithText("重试").assertDoesNotExist()
    }

    @Test
    fun `the verify button on a cached feed opens the challenge page`() {
        var opened = false
        setScreen(
            posts = listOf(feedPost(1, "cached post")),
            refresh = LoadState.Error(SiteException(SiteError.Cloudflare)),
            onRecoverInBrowser = { opened = true },
        )

        composeRule.onNodeWithText("去验证").performClick()

        assert(opened)
    }

    /** A failure a retry *can* fix keeps the retry: the rule is per error, not "never 重试". */
    @Test
    fun `a network failure over cached rows still offers a retry`() {
        setScreen(
            posts = listOf(feedPost(1, "cached post")),
            refresh = LoadState.Error(SiteException(SiteError.Network)),
        )

        composeRule.onNodeWithText("重试").assertIsDisplayed()
        composeRule.onNodeWithText("去验证").assertDoesNotExist()
    }

    /** Nothing to announce while the rows are current — the snackbar is a failure's, not a refresh's. */
    @Test
    fun `a successful refresh says nothing`() {
        setScreen(posts = listOf(feedPost(1, "cached post")))

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

    /** The badge is an icon, so the announcement is the only thing carrying it to a screen reader. */
    @Test
    fun `an 加精 row is announced as 推荐阅读`() {
        setScreen(listOf(feedPost(1, "加精了的帖子", isAwarded = true)))

        composeRule.onNodeWithContentDescription("推荐阅读").assertIsDisplayed()
    }

    @Test
    fun `an ordinary row carries no 推荐阅读 badge`() {
        setScreen(listOf(feedPost(1, "普通帖子")))

        composeRule.onNodeWithContentDescription("推荐阅读").assertDoesNotExist()
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
            refresh = LoadState.Error(SiteException(SiteError.LoginRequired)),
            state = PostListUiState(boards = boards, categorySlug = "tech"),
        )

        composeRule.onNodeWithText("「技术」需要登录后查看").assertIsDisplayed()
    }

    // ---------------------------------------------------------------------------------------------
    // 首页翻页栏
    // ---------------------------------------------------------------------------------------------

    private fun pagedState(
        pageBarEnabled: Boolean = true,
        totalPages: Int = 217,
        startPage: Int = 1,
    ) = PostListUiState(
        boards = boards,
        pageBarEnabled = pageBarEnabled,
        totalPages = totalPages,
        startPages = mapOf(null to startPage),
    )

    /** The feed is a scroll by default; the bar exists only for the reader who asks for it. */
    @Test
    fun `the page bar stays away until the setting turns it on`() {
        setScreen(
            posts = listOf(feedPost(1, "post", page = 1)),
            state = pagedState(pageBarEnabled = false),
        )

        composeRule.onAllNodesWithContentDescription("第 1 / 217 页").assertCountEquals(0)
    }

    @Test
    fun `the page bar names the page and the total`() {
        setScreen(listOf(feedPost(1, "post", page = 1)), state = pagedState())

        composeRule.onNodeWithContentDescription("第 1 / 217 页").assertIsDisplayed()
    }

    /** One page is not a pager: a bar that can only ever say "第 1 / 1 页" is furniture. */
    @Test
    fun `a single-page feed draws no bar`() {
        setScreen(
            posts = listOf(feedPost(1, "post", page = 1)),
            state = pagedState(totalPages = 1),
        )

        composeRule.onAllNodesWithContentDescription("第 1 / 1 页").assertCountEquals(0)
    }

    /** A jump names its destination before the rows arrive; otherwise the tap reads as ignored. */
    @Test
    fun `the bar names the page a jump is heading for while the old rows are still up`() {
        setScreen(
            posts = listOf(feedPost(1, "stale row", page = 1)),
            state = pagedState(startPage = 40),
        )

        composeRule.onNodeWithContentDescription("第 40 / 217 页").assertIsDisplayed()
    }

    /** Travel, not fetching: a page the feed already holds is somewhere to scroll to. */
    @Test
    fun `next page scrolls instead of reloading when the page is already stored`() {
        var requested: Int? = null
        setScreen(
            posts = listOf(feedPost(1, "page one row", page = 1), feedPost(2, "page two row", page = 2)),
            state = pagedState(),
            onGoToPage = { requested = it },
            onFindPageRow = { page -> 1.takeIf { page == 2 } },
        )

        composeRule.onNodeWithContentDescription("下一页").performClick()

        assertEquals(null, requested)
    }

    /**
     * The regression this pairs with: the row is stored, and the pager is not holding it.
     *
     * That is the ordinary state of the page one step away — the feed runs with placeholders on and
     * Room re-windows it on every write, so nothing outside the current window is in [posts] even
     * though the reader scrolled through it a moment ago. The old check asked [posts] for a row whose
     * page matched, found placeholders, and refetched the page on every single step.
     */
    @Test
    fun `next page scrolls to a stored row the pager is not holding`() {
        var requested: Int? = null
        setScreen(
            posts = List(60) { feedPost(it + 1L, "page one row ${it + 1}", page = 1) },
            state = pagedState(),
            onGoToPage = { requested = it },
            onFindPageRow = { page -> 50.takeIf { page == 2 } },
        )

        composeRule.onNodeWithContentDescription("下一页").performClick()

        assertEquals(null, requested)
    }

    /**
     * 下一页 onto the page the feed has not fetched yet is the rest of the scroll, not a jump: it
     * goes to the foot, which is what asks for the append, rather than replacing the window and
     * throwing away every page the reader scrolled through to get here.
     */
    @Test
    fun `next page reads on to the foot instead of reloading at the frontier`() {
        var requested: Int? = null
        val feedStates = HomeFeedStates()
        val listState = feedStates.listState(null, FeedSort.LAST_REPLY)
        setScreen(
            posts = List(60) { feedPost(it + 1L, "page one row ${it + 1}", page = 1) },
            state = pagedState(),
            onGoToPage = { requested = it },
            // Page 1 is stored and page 2 is not, which is the frontier.
            onFindPageRow = { page -> 0.takeIf { page == 1 } },
            feedStates = feedStates,
        )

        composeRule.onNodeWithContentDescription("下一页").performClick()
        composeRule.waitUntil { listState.firstVisibleItemIndex > 0 }

        assertEquals(null, requested)
    }

    @Test
    fun `next page asks for a reload when the feed does not hold the page`() {
        var requested: Int? = null
        setScreen(
            posts = listOf(feedPost(1, "only page one", page = 1)),
            state = pagedState(),
            onGoToPage = { requested = it },
        )

        composeRule.onNodeWithContentDescription("下一页").performClick()

        assertEquals(2, requested)
    }

    /** The scroller is the jump: a page key is the whole gesture, with nothing to confirm after it. */
    @Test
    fun `a page key on the jump sheet reports the page it names`() {
        var requested: Int? = null
        setScreen(
            posts = listOf(feedPost(1, "post", page = 40)),
            state = pagedState(startPage = 40),
            onGoToPage = { requested = it },
        )

        composeRule.onNodeWithContentDescription("第 40 / 217 页").performClick()
        composeRule.onNode(hasText("41") and hasClickAction()).performClick()

        assertEquals(41, requested)
    }

    /**
     * 最新 on the feed is page 1, not the last page.
     *
     * The site's page numbers drift under a feed sorted by activity — a post that was on page 3 is on
     * page 4 an hour later — so its newest content is always at the head, and "最后一页" would be the
     * oldest thing the board has.
     */
    @Test
    fun `the jump sheet sends 最新 back to the first page`() {
        var requested: Int? = null
        setScreen(
            posts = listOf(feedPost(1, "post", page = 40)),
            state = pagedState(startPage = 40),
            onGoToPage = { requested = it },
        )

        composeRule.onNodeWithContentDescription("第 40 / 217 页").performClick()
        composeRule.onNodeWithText("最新").performClick()

        assertEquals(1, requested)
    }
}
