package io.github.nsreader.ui.postdetail

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.model.InlineNode
import io.github.nsreader.model.PostContent
import io.github.nsreader.model.RichNode
import io.github.nsreader.ui.theme.NodeSeekTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Screen-level tests for the thread view, including the offline-first error behaviour. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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
            NodeSeekTheme {
                PostDetailScreen(
                    state = state,
                    onBack = onBack,
                    onOpenBrowser = onOpenBrowser,
                    onImageClick = {},
                    onRetry = onRetry,
                    onLoadMore = {},
                    onOpenPostInBrowser = {},
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

        // Twice by design: truncated in the app bar, and in full as the list header.
        composeRule.onAllNodesWithText("a thread title").assertCountEquals(2)
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
    fun `an error with nothing cached takes over the screen`() {
        setScreen(PostDetailUiState(body = null, error = NodeSeekError.Cloudflare))

        composeRule.onNodeWithText("需要在浏览器里完成一次人机验证，之后就能正常浏览了").assertIsDisplayed()
        composeRule.onNodeWithText("在浏览器中验证").assertIsDisplayed()
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
        composeRule.onNodeWithText("连不上 NodeSeek，检查一下网络").assertDoesNotExist()
    }

    @Test
    fun `a first load shows a spinner rather than an empty thread`() {
        setScreen(PostDetailUiState(isLoading = true, body = null))

        composeRule.onNodeWithText("重试").assertDoesNotExist()
    }
}
