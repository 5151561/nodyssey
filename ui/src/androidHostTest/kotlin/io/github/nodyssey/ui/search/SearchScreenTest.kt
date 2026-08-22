package io.github.nodyssey.ui.search

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.nodyssey.data.Board
import io.github.nodyssey.data.FeedPost
import io.github.nodyssey.model.PostSummary
import io.github.nodyssey.model.SearchTarget
import io.github.plaza.core.net.SiteError
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
 * Screen-level tests for search, on Robolectric for the same reason the feed's are: no emulator.
 *
 * These cover the collapsing header. The rest of the screen's behaviour lives in
 * [SearchViewModelTest], which is where the query, the scope and the history are decided.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class SearchScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val boards = listOf(Board("daily", "日常", null), Board("tech", "技术", null))

    private fun result(id: Long) =
        FeedPost(
            summary =
            PostSummary(
                postId = id,
                title = "result $id",
                authorName = "tester",
                authorUid = 1,
                avatarUrl = null,
                categoryTitle = "日常",
                categorySlug = "daily",
                viewCount = 100,
                commentCount = 3,
                lastActiveText = "1分钟前",
                lastActiveTitle = null,
                isPinned = false,
                isLocked = false,
            ),
            isRead = false,
            newCommentCount = 0,
        )

    /**
     * The results list.
     *
     * Matched by the scroll-to-index action, which on this screen only a lazy list has: plain
     * `hasScrollAction` also catches the search field, and naming a row it contains would stop
     * matching the moment a swipe carried that row off the screen.
     */
    private val resultList = hasScrollToIndexAction()

    /** Renders the screen against a mutable state so a test can change the search out from under it. */
    private fun setScreen(
        initial: SearchUiState,
        onNavigationBarHiddenChanged: (Boolean) -> Unit = {},
    ): () -> Unit {
        var state by mutableStateOf(initial)
        composeRule.setContent {
            PlazaTheme {
                val pagingData =
                    PagingData.from(
                        data = (1..40L).map { result(it) },
                        sourceLoadStates =
                        LoadStates(
                            refresh = LoadState.NotLoading(false),
                            prepend = LoadState.NotLoading(true),
                            append = LoadState.NotLoading(true),
                        ),
                    )
                SearchScreen(
                    state = state,
                    postResults = flowOf(pagingData).collectAsLazyPagingItems(),
                    queryState = rememberTextFieldState("轻量"),
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
                    onNavigationBarHiddenChanged = onNavigationBarHiddenChanged,
                )
            }
        }
        // Clearing the query is what the caller is given: it is the one transition that has to put
        // the header back, and the only way to reach it is from outside the screen.
        return { state = state.copy(submittedQuery = null) }
    }

    private fun searchedState() =
        SearchUiState(
            submittedQuery = "轻量",
            target = SearchTarget.POSTS,
            boards = boards,
        )

    /**
     * A one-character query, which the site declines to search on either tab.
     *
     * It used to arrive as [SiteError.LoginRequired] and put a sign-in screen in front of a reader
     * who was already signed in — the tell that the state did not exist and the refusal was falling
     * through to the nearest one that did.
     */
    @Test
    fun `a term the site will not search says so instead of asking for a sign-in`() {
        setScreen(
            searchedState().copy(
                target = SearchTarget.USERS,
                userLoadState = SearchLoadState.Error(SiteError.QueryTooShort),
            ),
        )

        composeRule.onNodeWithText("关键词太短了").assertIsDisplayed()
        composeRule.onNodeWithText("需要登录后查看").assertDoesNotExist()
    }

    /**
     * The whole header goes: the field, the tabs and the scope row. That is ~170dp of an 800dp
     * screen, which is the two extra results this exists to show.
     */
    @Test
    fun `scrolling the results folds the entire header away`() {
        setScreen(searchedState())

        composeRule.onNodeWithContentDescription("搜索").assertIsDisplayed()
        composeRule.onNodeWithText("帖子").assertIsDisplayed()
        composeRule.onNodeWithText("全部版块").assertIsDisplayed()

        composeRule.onNode(resultList).performTouchInput { swipeUp() }

        composeRule.onNodeWithContentDescription("搜索").assertIsNotDisplayed()
        composeRule.onNodeWithText("帖子").assertIsNotDisplayed()
        composeRule.onNodeWithText("全部版块").assertIsNotDisplayed()
    }

    /**
     * Going back to the setup screen has to unfold it.
     *
     * That screen can be empty — no history, no scrollable list anywhere on it — so a header left
     * folded would take the search field off the screen with no gesture left that could return it.
     */
    @Test
    fun `clearing the query brings the folded header back`() {
        val clearQuery = setScreen(searchedState())

        composeRule.onNode(resultList).performTouchInput { swipeUp() }
        composeRule.onNodeWithContentDescription("搜索").assertIsNotDisplayed()

        clearQuery()

        composeRule.onNodeWithContentDescription("搜索").assertIsDisplayed()
        composeRule.onNodeWithText("帖子").assertIsDisplayed()
    }

    /**
     * The host's bar answers the same swipe, and the screen tells it so rather than hiding anything
     * itself — the bar belongs to the navigation scaffold, which this screen sits inside.
     */
    @Test
    fun `scrolling the results asks the host to hide its bar`() {
        val reported = mutableListOf<Boolean>()
        setScreen(searchedState(), onNavigationBarHiddenChanged = reported::add)

        composeRule.onNode(resultList).performTouchInput { swipeUp() }

        assertEquals(listOf(true), reported)
    }

    /** Leaving must not stick the host with a bar nobody is left to bring back. */
    @Test
    fun `clearing the query gives the bar back`() {
        val reported = mutableListOf<Boolean>()
        val clearQuery = setScreen(searchedState(), onNavigationBarHiddenChanged = reported::add)

        composeRule.onNode(resultList).performTouchInput { swipeUp() }
        clearQuery()

        composeRule.runOnIdle { assertEquals(listOf(true, false), reported) }
    }

    /**
     * The point of the whole thing, asserted as the height the list is given rather than as a row
     * that happens to be on screen: which row that is depends on how far one swipe carries, and the
     * complaint this answers was about the size of the area, not about any particular result.
     *
     */
    @Test
    fun `folding the header hands the space to the results`() {
        setScreen(searchedState())
        val before = composeRule.onNode(resultList).getBoundsInRoot().height

        composeRule.onNode(resultList).performTouchInput { swipeUp() }

        val after = composeRule.onNode(resultList).getBoundsInRoot().height
        assertTrue("list was $before, is $after", after - before > 100.dp)
    }
}
