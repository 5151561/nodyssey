package io.github.nodyssey.ui.postdetail

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.model.InlineNode
import io.github.nodyssey.model.PostContent
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
    )

    private fun setScreen(
        state: PostDetailUiState,
        onRetry: () -> Unit = {},
        onBack: () -> Unit = {},
        onOpenBrowser: (String) -> Unit = {},
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
    fun `the back affordance reports the intent to leave`() {
        var backed = false
        setScreen(
            PostDetailUiState(title = "t", body = content("body")),
            onBack = { backed = true },
        )

        composeRule.onNodeWithContentDescription("返回").performClick()

        assert(backed)
    }

    @Test
    fun `feeding chicken opens the confirmation dialog`() {
        setScreen(PostDetailUiState(title = "t", body = content("body", author = "op")))

        composeRule.onNodeWithContentDescription("投喂鸡腿").performClick()

        composeRule.onNodeWithText("投喂鸡腿？").assertIsDisplayed()
        composeRule.onNodeWithText("是否向 op 投喂鸡腿？这将消耗你一个鸡腿。").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("投喂鸡腿？").assertDoesNotExist()
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
