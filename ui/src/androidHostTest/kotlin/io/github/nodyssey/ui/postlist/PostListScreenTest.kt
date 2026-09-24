package io.github.nodyssey.ui.postlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
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
        page: Int? = null,
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
            commentCount = 12,
            lastActiveText = "1分钟前",
            lastActiveTitle = null,
            isPinned = false,
            isLocked = false,
            isAwarded = false,
        ),
        isRead = false,
        newCommentCount = 0,
        page = page,
    )

    /** Renders the screen with a paging stream in an explicit load state. */
    private fun setScreen(
        posts: List<FeedPost>,
        refresh: LoadState = LoadState.NotLoading(false),
        append: LoadState = LoadState.NotLoading(true),
        state: PostListUiState = PostListUiState(boards = boards),
        onBoardClick: (String?) -> Unit = {},
        onRecoverInBrowser: (String) -> Unit = {},
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
                    onPostClick = {},
                    onBoardClick = onBoardClick,
                    onSortChange = {},
                    onRecoverInBrowser = onRecoverInBrowser,
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
        onRecoverInBrowser: (String) -> Unit,
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
            onGoToPage = onGoToPage,
            onFindPageRow = onFindPageRow,
            reselectRequests = reselectRequests,
        )
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

    /**
     * The app bar folds away with the feed; the board strip does not.
     *
     * A real swipe rather than `performScrollToIndex`, which drives the list through its semantics
     * action and dispatches no nested scroll at all — the app bar would stay open however far such a
     * scroll went, and the test would pass without the behaviour existing.
     */
    @Test
    fun `scrolling the feed folds the app bar away and keeps the board strip`() {
        val feedStates = HomeFeedStates()
        val listState = feedStates.listState(null, FeedSort.LAST_REPLY)
        setScreen((1..40).map { feedPost(it.toLong(), "post $it") }, feedStates = feedStates)

        feedList().performTouchInput { swipeUp() }

        composeRule.onNodeWithContentDescription("排序方式").assertIsNotDisplayed()
        composeRule.onNodeWithContentDescription("搜索").assertIsNotDisplayed()
        composeRule.onNodeWithText("综合").assertIsDisplayed()
        composeRule.onNodeWithText("技术").assertIsDisplayed()
        // The header folding is not the feed scrolling. With a collapse limit that never got
        // published, the header folded exactly like this and then swallowed the rest of the drag.
        composeRule.runOnIdle {
            assertTrue(listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)
        }
    }

    /**
     * The same fold on a signed-out home, where nothing recomposes the header after its first frame,
     * and back again: `enterAlways` has to bring the header out on the first drag toward the top
     * rather than after the reader has paid back however far the offset ran past the header's height.
     */
    @Test
    fun `a swipe back down brings the folded app bar out again`() {
        setScreen((1..40).map { feedPost(it.toLong(), "post $it") })

        // Found by any row rather than by [feedList]'s "post 1": the feed really scrolls now, and one
        // fling carries every row with that in its title off screen.
        val anyFeed = composeRule.onAllNodes(hasScrollAction() and hasAnyDescendant(hasText("post ", substring = true))).onLast()
        // Twice, so a header that kept folding past its own height would owe more than one swipe
        // back — which is what an unpublished collapse limit did.
        anyFeed.performTouchInput { swipeUp() }
        anyFeed.performTouchInput { swipeUp() }
        composeRule.onNodeWithContentDescription("排序方式").assertIsNotDisplayed()

        anyFeed.performTouchInput { swipeDown() }

        composeRule.onNodeWithContentDescription("排序方式").assertIsDisplayed()
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

    /**
     * 去验证 opens the address the *failure* names, not one the screen rebuilds.
     *
     * The bug: 综合 rebuilt it as `BASE_URL + listPath(null, 1, sort)`, which is `/?sortBy=…`, and
     * that is the one path NodeSeek's zone does not challenge. The web view opened an ordinary home
     * page, there was nothing to solve, Cloudflare issued no pass, and the next request hit the same
     * wall — with the jar still empty to prove it had never once been cleared.
     */
    @Test
    fun `the recovery button opens the address that was refused`() {
        var opened: String? = null
        setScreen(
            posts = emptyList(),
            refresh = LoadState.Error(SiteException(SiteError.Cloudflare(REFUSED))),
            onRecoverInBrowser = { opened = it },
        )

        composeRule.onNodeWithText("去验证").performClick()

        assertEquals(REFUSED, opened)
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
            refresh = LoadState.Error(SiteException(SiteError.Cloudflare(REFUSED))),
        )

        composeRule.onNodeWithText("cached post").assertIsDisplayed()
        composeRule.onNodeWithText("需要确认一下你不是机器人").assertIsDisplayed()
        composeRule.onNodeWithText("去验证").assertIsDisplayed()
        composeRule.onNodeWithText("重试").assertDoesNotExist()
    }

    // ---------------------------------------------------------------------------------------------
    // 首页翻页栏
    // ---------------------------------------------------------------------------------------------

    private fun pagedState(startPage: Int = 1) =
        PostListUiState(
            boards = boards,
            pageBarEnabled = true,
            totalPages = 217,
            startPages = mapOf(null to startPage),
        )

    /** A jump names its destination before the rows arrive; otherwise the tap reads as ignored. */
    @Test
    fun `the bar names the page a jump is heading for while the old rows are still up`() {
        setScreen(
            posts = listOf(feedPost(1, "stale row", page = 1)),
            state = pagedState(startPage = 40),
        )

        composeRule.onNodeWithContentDescription("第 40 / 217 页").assertIsDisplayed()
    }

    /**
     * Travel, not fetching: a page the feed already holds is somewhere to scroll to — even when the
     * row is stored and the pager is not holding it.
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

    private companion object {
        /** A path the zone actually challenges — deliberately not the exempt home page. */
        const val REFUSED = "https://www.nodeseek.com/page-2?sortBy=replyTime"
    }
}
