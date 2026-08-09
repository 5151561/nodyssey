package io.github.nodyssey.ui.postdetail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.nodyssey.data.FreeChickenLegs
import io.github.nodyssey.model.InlineNode
import io.github.nodyssey.model.PostContent
import io.github.nodyssey.model.PostReactions
import io.github.nodyssey.model.ReactionAction
import io.github.nodyssey.model.RichNode
import io.github.plaza.core.net.SiteError
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screen-level tests for the thread view, including the offline-first error behaviour. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// The design targets a 360×800dp compact phone; Robolectric's default window is far
// shorter than any real device and would fail screens that fit fine in the hand.
@Config(qualifiers = "w360dp-h800dp")
class PostDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun content(
        text: String,
        author: String = "tester",
        signature: List<RichNode> = emptyList(),
        /** Null is a page that never carried the tallies, which is what disables the three marks. */
        reactions: PostReactions? = null,
        floor: String? = null,
        blocked: Boolean = false,
    ) = PostContent(
        commentId = text.hashCode().toLong(),
        floor = floor,
        authorName = author,
        authorUid = 1,
        avatarUrl = null,
        isOriginalPoster = false,
        badges = emptyList(),
        createdAtText = "1分钟前",
        createdAtTitle = null,
        categoryTitle = null,
        nodes = listOf(RichNode.Paragraph(listOf(InlineNode.Text(text)))),
        signatureNodes = signature,
        reactions = reactions,
        isBlocked = blocked,
    )

    private fun setScreen(
        state: PostDetailUiState,
        onRetry: () -> Unit = {},
        onBack: () -> Unit = {},
        onOpenBrowser: (String) -> Unit = {},
        onSignIn: () -> Unit = {},
        onReact: (Long, ReactionAction) -> Unit = { _, _ -> },
        onLoadPage: (Int) -> Unit = {},
        onCollect: () -> Unit = {},
    ) {
        composeRule.setContent {
            PlazaTheme {
                PostDetailScreen(
                    state = state,
                    postUrl = "https://www.nodeseek.com/post-1-1",
                    onBack = onBack,
                    onOpenBrowser = onOpenBrowser,
                    onImageClick = {},
                    onRetry = onRetry,
                    onLoadMore = {},
                    onLoadPage = onLoadPage,
                    onSignIn = onSignIn,
                    onReact = onReact,
                    onCollect = onCollect,
                )
            }
        }
    }

    @Test
    fun `renders the title, the body and the comments`() {
        setScreen(
            PostDetailUiState(
                title = "a thread title",
                body = content("the opening post", author = "op"),
                comments = listOf(content("first reply"), content("second reply")),
            ),
        )

        // The app bar stays quiet until the full title has scrolled away.
        composeRule.onAllNodesWithText("a thread title").assertCountEquals(1)
        composeRule.onNodeWithText("the opening post").assertIsDisplayed()
        composeRule.onNodeWithText("first reply").assertIsDisplayed()
        composeRule.onNodeWithText("second reply").assertIsDisplayed()
    }

    /**
     * The floor is kept and collapsed, not dropped. A reply quoting #2 has to still find a #2 there,
     * and the reader has to be able to see what was hidden without turning blocking off for the app.
     */
    @Test
    fun `collapses a blocked floor into a row that can be opened`() {
        setScreen(
            PostDetailUiState(
                title = "t",
                body = content("the opening post", author = "op"),
                comments =
                listOf(
                    content("blocked reply", floor = "#1", blocked = true),
                    content("ordinary reply", floor = "#2"),
                ),
            ),
        )

        composeRule.onAllNodesWithText("blocked reply").assertCountEquals(0)
        composeRule.onNodeWithText("ordinary reply").assertIsDisplayed()
        composeRule.onNodeWithText("#1 · 已屏蔽用户的评论").assertIsDisplayed()

        composeRule.onNodeWithText("显示").performClick()

        composeRule.onNodeWithText("blocked reply").assertIsDisplayed()
    }

    /** 临时显示被屏蔽内容 opens every floor at once, without touching the network. */
    @Test
    fun `the reveal switch draws blocked floors as ordinary ones`() {
        setScreen(
            PostDetailUiState(
                title = "t",
                body = content("the opening post", author = "op"),
                comments = listOf(content("blocked reply", floor = "#1", blocked = true)),
                showBlockedContent = true,
            ),
        )

        composeRule.onNodeWithText("blocked reply").assertIsDisplayed()
        composeRule.onAllNodesWithText("已屏蔽用户的评论").assertCountEquals(0)
    }

    @Test
    fun `renders the public signature and opens its link`() {
        var opened: String? = null
        val signature = listOf(
            RichNode.Paragraph(
                listOf(InlineNode.Link(text = "个人博客", url = "https://example.com")),
            ),
        )
        setScreen(
            PostDetailUiState(
                title = "t",
                body = content("body", author = "op", signature = signature),
            ),
            onOpenBrowser = { opened = it },
        )

        composeRule.onNodeWithText("个人博客").assertIsDisplayed().performClick()

        assert(opened == "https://example.com")
    }

    @Test
    fun `the back affordance reports the intent to leave`() {
        var backed = false
        setScreen(
            PostDetailUiState(title = "t", body = content("body")),
            onBack = { backed = true },
        )

        composeRule.onNodeWithContentDescription("返回").performClick()

        assert(backed)
    }

    private fun signedInState(reactions: PostReactions? = PostReactions()) =
        PostDetailUiState(
            title = "t",
            body = content("body", author = "op", reactions = reactions),
            isSignedIn = true,
        )

    @Test
    fun `feeding chicken opens the confirmation dialog`() {
        setScreen(signedInState())

        composeRule.onNodeWithContentDescription("投喂鸡腿").performClick()

        composeRule.onNodeWithText("投喂鸡腿？").assertIsDisplayed()
        composeRule.onNodeWithText("是否向 op 投喂鸡腿？这将消耗你一个鸡腿。").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("投喂鸡腿？").assertDoesNotExist()
    }

    /** Confirming is what sends it; dismissing must not. */
    @Test
    fun `confirming the dialog spends the chicken leg`() {
        val sent = mutableListOf<ReactionAction>()
        setScreen(signedInState(), onReact = { _, action -> sent += action })

        composeRule.onNodeWithContentDescription("投喂鸡腿").performClick()
        composeRule.onNodeWithText("取消").performClick()
        assert(sent.isEmpty())

        composeRule.onNodeWithContentDescription("投喂鸡腿").performClick()
        composeRule.onNodeWithText("投喂").performClick()

        assert(sent == listOf(ReactionAction.ChickenLeg)) { "sent $sent" }
    }

    /** 点赞 is free and the site does not confirm it either — the tap is the whole interaction. */
    @Test
    fun `upvoting sends straight away without a dialog`() {
        val sent = mutableListOf<ReactionAction>()
        setScreen(signedInState(), onReact = { _, action -> sent += action })

        composeRule.onNodeWithContentDescription("点赞").performClick()

        assert(sent == listOf(ReactionAction.Upvote)) { "sent $sent" }
    }

    /** 反对 costs two chicken legs, and the dialog is the only place that says so. */
    @Test
    fun `the dislike confirmation names its price`() {
        setScreen(signedInState())

        composeRule.onNodeWithContentDescription("点踩").performClick()

        composeRule.onNodeWithText("是否反对该楼层？这将消耗你两个鸡腿，且不能撤销。").assertIsDisplayed()
    }

    /** Only when the site confirmed the allowance — an unread quota must not promise "免费". */
    @Test
    fun `says the feed is free when today's allowance still covers it`() {
        setScreen(signedInState().copy(freeChickenLegs = FreeChickenLegs(max = 5, used = 2)))

        composeRule.onNodeWithContentDescription("投喂鸡腿").performClick()

        composeRule.onNodeWithText("是否向 op 投喂鸡腿？今日还有 3 次免费投喂，本次不消耗鸡腿。").assertIsDisplayed()
    }

    @Test
    fun `shows the tallies the page carried`() {
        setScreen(signedInState(PostReactions(likeCount = 3, upvoteCount = 1, dislikeCount = 0)))

        composeRule.onNodeWithText("3").assertIsDisplayed()
    }

    /** Signing in comes before the spend, not after the site has rejected it. */
    @Test
    fun `sends a signed-out reader to sign in instead of reacting`() {
        var signIn = 0
        val sent = mutableListOf<ReactionAction>()
        setScreen(
            PostDetailUiState(title = "t", body = content("body", reactions = PostReactions())),
            onSignIn = { signIn++ },
            onReact = { _, action -> sent += action },
        )

        composeRule.onNodeWithContentDescription("点赞").performClick()

        assert(signIn == 1) { "sign-in asked $signIn times" }
        assert(sent.isEmpty())
    }

    /** A mark already spent cannot be spent again — the site has no undo for any of the three. */
    @Test
    fun `an already spent mark is not clickable`() {
        val sent = mutableListOf<ReactionAction>()
        setScreen(
            signedInState(PostReactions(upvoteCount = 1, upvoted = true)),
            onReact = { _, action -> sent += action },
        )

        composeRule.onNodeWithContentDescription("点赞").performClick()

        assert(sent.isEmpty()) { "sent $sent" }
    }

    /** Whole-thread on NodeSeek, so the star belongs to the opening post and to nothing else. */
    @Test
    fun `only the opening post carries the collect star`() {
        setScreen(
            PostDetailUiState(
                title = "t",
                body = content("body", author = "op", reactions = PostReactions()),
                comments = listOf(content("a reply", reactions = PostReactions())),
                isSignedIn = true,
                collected = false,
                collectionCount = 6,
            ),
        )

        composeRule.onAllNodesWithContentDescription("收藏").assertCountEquals(1)
        composeRule.onNodeWithText("6").assertIsDisplayed()
    }

    /**
     * Null is not false. No fetched page has said which way the toggle points, and a star drawn on a
     * guess is one tap away from silently un-collecting the thread.
     */
    @Test
    fun `no star at all while the collection state is unknown`() {
        setScreen(signedInState())

        composeRule.onAllNodesWithContentDescription("收藏").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("已收藏").assertCountEquals(0)
    }

    @Test
    fun `a collected thread shows the filled star`() {
        setScreen(
            PostDetailUiState(
                title = "t",
                body = content("body", author = "op", reactions = PostReactions()),
                isSignedIn = true,
                collected = true,
                collectionCount = 7,
            ),
        )

        composeRule.onNodeWithContentDescription("已收藏").assertIsDisplayed()
    }

    @Test
    fun `tapping the star reports the toggle`() {
        var collects = 0
        setScreen(
            PostDetailUiState(
                title = "t",
                body = content("body", author = "op", reactions = PostReactions()),
                isSignedIn = true,
                collected = false,
                collectionCount = 6,
            ),
            onCollect = { collects++ },
        )

        composeRule.onNodeWithContentDescription("收藏").performClick()

        assertEquals(1, collects)
    }

    /** Same gate as the marks and the editor: the account comes before the action. */
    @Test
    fun `a signed-out reader is sent to sign in instead of collecting`() {
        var signIn = 0
        var collects = 0
        setScreen(
            PostDetailUiState(
                title = "t",
                body = content("body", author = "op", reactions = PostReactions()),
                collected = false,
                collectionCount = 6,
            ),
            onSignIn = { signIn++ },
            onCollect = { collects++ },
        )

        composeRule.onNodeWithContentDescription("收藏").performClick()

        assertEquals(1, signIn)
        assertEquals(0, collects)
    }

    @Test
    fun `an error with nothing cached takes over the screen`() {
        setScreen(PostDetailUiState(body = null, error = SiteError.Cloudflare))

        composeRule.onNodeWithText("需要确认一下你不是机器人").assertIsDisplayed()
        composeRule.onNodeWithText("去验证").assertIsDisplayed()
    }

    @Test
    fun `retrying from the error state is reported`() {
        var retried = false
        setScreen(
            PostDetailUiState(body = null, error = SiteError.Network),
            onRetry = { retried = true },
        )

        composeRule.onNodeWithText("重试").performClick()

        assert(retried)
    }

    /**
     * The behaviour that makes a flaky connection tolerable: a failed refresh reports the failure but
     * leaves the cached thread readable underneath it.
     */
    @Test
    fun `a failed refresh over cached content keeps the thread readable`() {
        setScreen(
            PostDetailUiState(
                title = "cached thread",
                body = content("cached body"),
                comments = listOf(content("cached reply")),
                error = SiteError.Network,
            ),
        )

        composeRule.onNodeWithText("cached body").assertIsDisplayed()
        composeRule.onNodeWithText("cached reply").assertIsDisplayed()
        composeRule.onNodeWithText("网络开小差了").assertDoesNotExist()
    }

    @Test
    fun `a first load shows a spinner rather than an empty thread`() {
        setScreen(PostDetailUiState(isLoading = true, body = null))

        composeRule.onNodeWithText("重试").assertDoesNotExist()
    }

    /**
     * A thread opened on page 13 is on page 13, and the toolbar has to say so. It read the first
     * *loaded* comment's page as page 1 while the slice always started there, and would have gone on
     * claiming page 1 of 40 for a reader who had jumped.
     */
    @Test
    fun `the toolbar reads the page the reader jumped to`() {
        setScreen(jumpedState())

        composeRule.onNodeWithText("第 13 / 40 页").assertIsDisplayed()
    }

    /** Paging on from a jumped-to slice asks for the page after it, not for page 2. */
    @Test
    fun `the next page button asks for the page after the loaded slice`() {
        var requested: Int? = null
        setScreen(
            jumpedState(),
            onLoadPage = { requested = it },
        )

        composeRule.onNodeWithContentDescription("下一页").performClick()

        assertEquals(14, requested)
    }

    /** And back the other way: the page before the slice, which is not on screen either. */
    @Test
    fun `the previous page button asks for the page before the loaded slice`() {
        var requested: Int? = null
        setScreen(
            jumpedState(),
            onLoadPage = { requested = it },
        )

        composeRule.onNodeWithContentDescription("上一页").performClick()

        assertEquals(12, requested)
    }

    /**
     * The last leg of a notification: the page it named has arrived, and the floor on it has to be
     * the thing the reader is looking at rather than the top of the page.
     */
    @Test
    fun `a pending scroll lands on its floor once the page holding it is loaded`() {
        setScreen(jumpedState(pendingScroll = PendingScroll(page = 13, floor = "#127")))

        composeRule.onNodeWithText("floor 127").assertIsDisplayed()
        // The opening post sits above the whole page and must have scrolled off with it.
        composeRule.onNodeWithText("the opening post").assertDoesNotExist()
    }

    /** A page jump with no floor named lands on the page's first floor, not back on the opening post. */
    @Test
    fun `a pending scroll to a page lands on that page's first floor`() {
        setScreen(jumpedState(pendingScroll = PendingScroll(page = 13)))

        composeRule.onNodeWithText("floor 121").assertIsDisplayed()
        composeRule.onNodeWithText("the opening post").assertDoesNotExist()
    }

    /**
     * Both pages hold ten floors, so nothing about the *size* of the list changes when one replaces
     * the other. Waiting on the comment count is what made a jump between two equal-length pages
     * arrive and then sit there without scrolling.
     */
    @Test
    fun `a jump between two pages of the same length still scrolls`() {
        var state by mutableStateOf(
            jumpedState(
                floors = (41..50).toList(),
                page = 5,
                pendingScroll = null,
            ),
        )
        composeRule.setContent {
            PlazaTheme {
                PostDetailScreen(
                    state = state,
                    postUrl = "https://www.nodeseek.com/post-1-5",
                    onBack = {},
                    onOpenBrowser = {},
                    onImageClick = {},
                    onRetry = {},
                    onLoadMore = {},
                )
            }
        }
        composeRule.onNodeWithText("floor 41").assertIsDisplayed()

        // The order the ViewModel produces: the request to scroll is set when the fetch returns, and
        // the page it asked for arrives afterwards, on Room's own emission.
        state = state.copy(pendingScroll = PendingScroll(page = 13, floor = "#127"))
        composeRule.waitForIdle()
        state = jumpedState(pendingScroll = PendingScroll(page = 13, floor = "#127"))

        composeRule.onNodeWithText("floor 127").assertIsDisplayed()
    }

    /**
     * A notification about a floor on page 1 — most of them, since most threads are one page long.
     *
     * The empty state a thread opens in already claims to hold page 1, so the request was answered
     * against a list with no floors in it: the screen scrolled to index 0, reported the scroll done,
     * and the floors arriving a moment later found nothing left asking to be scrolled to. The reader
     * was left at the top of the post, which is exactly where they would have landed without the
     * floor in the first place.
     */
    @Test
    fun `a pending scroll to a floor on page one waits for that page to arrive`() {
        // The ViewModel's own first emission: the request to scroll is set before anything has been
        // fetched, so the list is empty and its "loaded pages" are still the default 1..1.
        var state by mutableStateOf(
            PostDetailUiState(
                title = "a thread",
                pendingScroll = PendingScroll(page = 1, floor = "#7"),
            ),
        )
        composeRule.setContent {
            PlazaTheme {
                PostDetailScreen(
                    state = state,
                    postUrl = "https://www.nodeseek.com/post-1-1",
                    onBack = {},
                    onOpenBrowser = {},
                    onImageClick = {},
                    onRetry = {},
                    onLoadMore = {},
                    // What the ViewModel does with it, and what makes consuming the request against
                    // an empty list unrecoverable rather than merely early.
                    onScrollHandled = { state = state.copy(pendingScroll = null) },
                )
            }
        }
        composeRule.waitForIdle()

        state = state.copy(
            body = content("the opening post"),
            comments = (1..10).map { content("floor $it", floor = "#$it") },
            commentPages = List(10) { 1 },
            totalPages = 4,
            hasNextPage = true,
        )

        composeRule.onNodeWithText("floor 7").assertIsDisplayed()
    }

    /** One page of a long thread, loaded on its own — what a jump or a notification produces. */
    private fun jumpedState(
        floors: List<Int> = (121..130).toList(),
        page: Int = 13,
        pendingScroll: PendingScroll? = null,
    ) = PostDetailUiState(
        title = "a long thread",
        body = content("the opening post"),
        comments = floors.map { content("floor $it", floor = "#$it") },
        commentPages = List(floors.size) { page },
        firstLoadedPage = page,
        lastLoadedPage = page,
        totalPages = 40,
        hasNextPage = true,
        pendingScroll = pendingScroll,
    )
}
