package io.github.nodyssey.ui.postdetail

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.data.FreeChickenLegs
import io.github.nodyssey.model.InlineNode
import io.github.nodyssey.model.PostContent
import io.github.nodyssey.model.PostReactions
import io.github.nodyssey.model.ReactionAction
import io.github.nodyssey.model.RichNode
import io.github.nodyssey.ui.theme.NodysseyTheme
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
    ) = PostContent(
        commentId = text.hashCode().toLong(),
        floor = null,
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
    )

    private fun setScreen(
        state: PostDetailUiState,
        onRetry: () -> Unit = {},
        onBack: () -> Unit = {},
        onOpenBrowser: (String) -> Unit = {},
        onSignIn: () -> Unit = {},
        onReact: (Long, ReactionAction) -> Unit = { _, _ -> },
    ) {
        composeRule.setContent {
            NodysseyTheme {
                PostDetailScreen(
                    state = state,
                    postUrl = "https://www.nodeseek.com/post-1-1",
                    onBack = onBack,
                    onOpenBrowser = onOpenBrowser,
                    onImageClick = {},
                    onRetry = onRetry,
                    onLoadMore = {},
                    onSignIn = onSignIn,
                    onReact = onReact,
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

    @Test
    fun `an error with nothing cached takes over the screen`() {
        setScreen(PostDetailUiState(body = null, error = NodeSeekError.Cloudflare))

        composeRule.onNodeWithText("需要确认一下你不是机器人").assertIsDisplayed()
        composeRule.onNodeWithText("去验证").assertIsDisplayed()
    }

    @Test
    fun `retrying from the error state is reported`() {
        var retried = false
        setScreen(
            PostDetailUiState(body = null, error = NodeSeekError.Network),
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
                error = NodeSeekError.Network,
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
}
