package io.github.nodyssey.ui.postdetail

import android.graphics.Bitmap
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.model.PostContent
import io.github.nodyssey.model.PostReactions
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1000dp")
class CommentRepliesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `评论固定按楼层顺序显示且直接回复默认收起`() {
        setScreen(listOf(replyComment(1), replyComment(2), replyComment(3, 1)))

        composeRule.onNodeWithText("平铺").assertDoesNotExist()
        composeRule.onNodeWithText("普通").assertDoesNotExist()
        composeRule.onNodeWithText("树形").assertDoesNotExist()
        composeRule.onNodeWithText("补全评论树").assertDoesNotExist()
        composeRule.onNodeWithText("继续此对话").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("1 条回复").assertIsDisplayed()
        val button = composeRule.onNodeWithContentDescription("1 条回复").getUnclippedBoundsInRoot()
        val icon = composeRule.onNodeWithContentDescription("1 条回复", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val arrow = composeRule.onNodeWithContentDescription("展开", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("按钮两侧留白应相等", (icon.left - button.left).value, (button.right - arrow.right).value, 1f)
        assertEquals("消息图标应垂直居中", (icon.top - button.top).value, (button.bottom - icon.bottom).value, 1f)
        assertEquals("箭头应垂直居中", (arrow.top - button.top).value, (button.bottom - arrow.bottom).value, 1f)
        val replyAction = composeRule.onNode(
            hasContentDescription("回复") and hasAnyAncestor(hasTestTag("comment-1")),
        ).getUnclippedBoundsInRoot()
        assertEquals("普通屏宽下回复操作应与消息按钮保持同一行", button.top.value, replyAction.top.value, 1f)
        composeRule.onNodeWithTag("reply-preview-comment-3").assertDoesNotExist()
        composeRule.onNodeWithTag("comment-replies-comment-2").assertDoesNotExist()
        composeRule.onNodeWithTag("comment-replies-comment-3").assertDoesNotExist()
        val positions = (1..3).map { composeRule.onNodeWithTag("comment-$it").getUnclippedBoundsInRoot().top }
        assertTrue(positions.zipWithNext().all { (earlier, later) -> earlier < later })
        capture("normal-collapsed")
    }

    @Test
    fun `展开只展示直接回复的作者楼层和内容并可从底部收起`() {
        setScreen(listOf(replyComment(1), replyComment(2), replyComment(3, 1), replyComment(4, 3), replyComment(5, 1)))
        composeRule.onNodeWithContentDescription("2 条回复").performClick()

        composeRule.onNodeWithTag("reply-preview-comment-3")
            .assertIsDisplayed()
            .assertTextContains("reader3")
            .assertTextContains("#3")
            .assertTextContains("comment 3", substring = true)
        composeRule.onNodeWithTag("reply-preview-comment-5").assertExists()
        composeRule.onNodeWithTag("reply-preview-comment-4").assertDoesNotExist()
        capture("normal-expanded")

        composeRule.onNodeWithTag("post-comments").performScrollToNode(hasText("收起回复"))
        composeRule.onNodeWithText("收起回复").performClick()
        composeRule.onNodeWithTag("reply-preview-comment-3").assertDoesNotExist()
        composeRule.onNodeWithTag("reply-preview-comment-5").assertDoesNotExist()
        composeRule.onNodeWithText("comment 1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("2 条回复").assertIsDisplayed()
    }

    @Test
    fun `点击预览中的引用跳转到这条回复的原始楼层`() {
        var openedLink: String? = null
        var requestedFloor: String? = null
        var recorded: Pair<Int, String?>? = null
        setScreen(
            comments = (1..22).map { replyComment(it, if (it == 12) 1 else null) },
            onLinkClick = { openedLink = it },
            onJumpToFloor = { requestedFloor = it },
            onReadingPositionChange = { page, floor -> recorded = page to floor },
        )
        composeRule.onNodeWithContentDescription("1 条回复").performClick()
        composeRule.onNode(
            hasText("comment 12", substring = true) and hasAnyAncestor(hasTestTag("reply-preview-comment-12")),
            useUnmergedTree = true,
        ).performTouchInput { click(Offset(6f, centerY)) }

        composeRule.onNodeWithTag("comment-12").assertIsDisplayed()
        composeRule.waitForIdle()
        assertEquals(2 to "#12", recorded)
        assertNull(openedLink)
        assertNull(requestedFloor)
        capture("normal-reply-jump")
    }

    @Test
    fun `预览代码块内的点击也跳转到回复原楼层`() {
        val comments = (1..12).map { floor ->
            val comment = replyComment(floor, if (floor == 12) 1 else null)
            if (floor == 12) {
                comment.copy(nodes = comment.nodes + RichNode.CodeBlock(code = "val answer = 42", language = "kotlin"))
            } else {
                comment
            }
        }
        setScreen(comments)
        composeRule.onNodeWithContentDescription("1 条回复").performClick()
        composeRule.onNode(
            hasContentDescription("复制") and hasAnyAncestor(hasTestTag("reply-preview-comment-12")),
        ).performTouchInput { click() }

        composeRule.onNodeWithTag("comment-12").assertIsDisplayed()
    }

    @Test
    fun `已展开的直接回复随分页更新且不混入间接回复`() {
        val initial = listOf(replyComment(1), replyComment(2), replyComment(3, 1))
        val state = setScreen(initial)
        composeRule.onNodeWithContentDescription("1 条回复").performClick()
        state.value = screenState(initial + listOf(replyComment(11, 1), replyComment(12, 3)))

        composeRule.onNodeWithContentDescription("2 条回复").assertIsDisplayed()
        composeRule.onNodeWithTag("reply-preview-comment-3").assertIsDisplayed()
        composeRule.onNodeWithTag("reply-preview-comment-11").assertExists()
        composeRule.onNodeWithTag("reply-preview-comment-12").assertDoesNotExist()
    }

    @Test
    fun `补入前页后保留原评论的展开状态`() {
        val page = listOf(replyComment(11), replyComment(12, 11))
        val state = setScreen(page)
        composeRule.onNodeWithContentDescription("1 条回复").performClick()
        state.value = screenState((1..10).map { replyComment(it) } + page)

        composeRule.onNodeWithTag("post-comments").performScrollToNode(hasTestTag("comment-11"))
        composeRule.onNodeWithTag("reply-preview-comment-12").assertIsDisplayed()
    }

    @Test
    fun `被屏蔽回复的预览沿用原来的显示规则`() {
        setScreen(listOf(replyComment(1), replyComment(2, 1).copy(isBlocked = true)))
        composeRule.onNodeWithContentDescription("1 条回复").performClick()
        composeRule.onNodeWithTag("reply-preview-comment-2").assertDoesNotExist()
        composeRule.onNodeWithText("reader2").assertDoesNotExist()
        composeRule.onNode(
            hasText("显示") and hasAnyAncestor(hasTestTag("comment-replies-comment-1")),
        ).performClick()
        composeRule.onNodeWithTag("reply-preview-comment-2").assertIsDisplayed()
    }

    @Test
    fun `缓存清空时不保留旧的回复卡片`() {
        val state = setScreen(listOf(replyComment(1), replyComment(2, 1)))
        composeRule.onNodeWithContentDescription("1 条回复").performClick()
        state.value = PostDetailUiState(postId = 42)

        composeRule.onNodeWithTag("reply-preview-comment-2").assertDoesNotExist()
        composeRule.onNodeWithText("收起回复").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w320dp-h800dp")
    fun `窄屏大字的长回复收起后回到原评论`() {
        val reply = replyComment(2, 1)
        val paragraphs = List(12) { RichNode.Paragraph(listOf(InlineNode.Text("这是一段用于检查长回复阅读位置的内容。"))) }
        setScreen(listOf(replyComment(1), reply.copy(nodes = reply.nodes + paragraphs)), fontScale = 1.5f)
        composeRule.onNodeWithTag("post-comments").performScrollToNode(hasContentDescription("1 条回复"))
        composeRule.onNodeWithContentDescription("1 条回复").performClick()

        val card = composeRule.onNodeWithTag("reply-preview-comment-2").getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertTrue(card.left >= root.left && card.right <= root.right)
        capture("normal-large-font")
        composeRule.onNodeWithTag("post-comments").performScrollToNode(hasText("收起回复"))
        composeRule.onNodeWithText("收起回复").performClick()
        composeRule.onNodeWithText("comment 1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("1 条回复").assertIsDisplayed()
    }

    @Test
    fun `长回复链的通知直接定位原楼层`() {
        var recorded: Pair<Int, String?>? = null
        setScreen(
            // 目标楼层之后留足内容，避免列表末尾限制滚动到顶部。
            comments = replyChain(30),
            pendingScroll = PendingScroll(2, "#14"),
            onReadingPositionChange = { page, floor -> recorded = page to floor },
        )

        composeRule.onNodeWithTag("comment-14").assertIsDisplayed()
        composeRule.onNodeWithTag("comment-conversation").assertDoesNotExist()
        composeRule.onNodeWithTag("reply-preview-comment-15").assertDoesNotExist()
        composeRule.waitForIdle()
        assertEquals(2 to "#14", recorded)
        capture("normal-long-chain-jump")
    }

    @Test
    fun `跨帖引用交给链接导航不会跳到本帖同号楼层`() {
        val reference = replyComment(2).copy(
            nodes = listOf(RichNode.Paragraph(listOf(InlineNode.QuoteRef("reader1", "#1", "/post-99-1#1"), InlineNode.Text("linked reply")))),
        )
        var opened: String? = null
        setScreen(listOf(replyComment(1), reference), onLinkClick = { opened = it })
        composeRule.onNodeWithTag("post-comments").performScrollToNode(hasText("linked reply", substring = true))
        composeRule.onNodeWithText("linked reply", substring = true).performTouchInput {
            click(Offset(6f, centerY))
        }
        assertEquals("/post-99-1#1", opened)
    }

    private fun screenState(comments: List<PostContent>): PostDetailUiState {
        val pages = comments.map { NodeSeekSite.pageOfFloor(requireNotNull(NodeSeekSite.parseFloorNumber(it.floor))) }
        return PostDetailUiState(
            postId = 42,
            title = "普通评论模式",
            body = replyComment(0),
            comments = comments.map { it.copy(reactions = PostReactions(upvoteCount = 4, likeCount = 1)) },
            commentPages = pages,
            firstLoadedPage = pages.minOrNull() ?: 1,
            lastLoadedPage = pages.maxOrNull() ?: 1,
            totalPages = pages.maxOrNull() ?: 1,
        )
    }

    private fun setScreen(
        comments: List<PostContent>,
        onLinkClick: (String) -> Unit = {},
        onJumpToFloor: (String) -> Unit = {},
        onReadingPositionChange: (Int, String?) -> Unit = { _, _ -> },
        fontScale: Float = 1f,
        pendingScroll: PendingScroll? = null,
    ): MutableState<PostDetailUiState> {
        val state = mutableStateOf(screenState(comments).copy(pendingScroll = pendingScroll))
        composeRule.setContent {
            PlazaTheme(fontScale = fontScale) {
                PostDetailScreen(
                    state = state.value,
                    postUrl = "https://www.nodeseek.com/post-42-1",
                    onBack = {},
                    onOpenBrowser = {},
                    onImageClick = {},
                    onRetry = {},
                    onLoadMore = {},
                    onVerify = {},
                    onLinkClick = onLinkClick,
                    onJumpToFloor = onJumpToFloor,
                    onReadingPositionChange = onReadingPositionChange,
                )
            }
        }
        return state
    }

    /** 设置输出目录时导出实际界面，用于核对卡片排版。 */
    private fun capture(name: String) {
        val directory = System.getenv("NODYSSEY_SCREENSHOTS")?.let(::File) ?: return
        directory.mkdirs()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        directory.resolve("$name.png").outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }
}
