package io.github.nodyssey.ui.postdetail

import android.graphics.Bitmap
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
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
            hasContentDescription("回复") and hasAnyAncestor(hasTestTag(commentTag(1))),
        ).getUnclippedBoundsInRoot()
        assertEquals("普通屏宽下回复操作应与消息按钮保持同一行", button.top.value, replyAction.top.value, 1f)
        // 这一行的对齐靠两个必须互相抵消的偏移量，之前没有任何断言看着它。
        val body = composeRule.onNode(
            hasText("comment 1", substring = true) and hasAnyAncestor(hasTestTag(commentTag(1))),
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot()
        assertEquals("回复图标左缘应与正文左缘对齐", body.left.value, icon.left.value, 1f)
        // 右缘的基准是楼层号而不是正文那行字：正文的右缘是文字宽度，不是页边距。
        // 量的是按钮边界减去这一行自己的内边距，不是图标 —— 窄到 48dp 触达区以下的按钮会把内容
        // 居中，图标因此可能落在边距内侧几个点。这里要证的是三个偏移量互相抵消，
        // 内边距从左侧按钮量出来，不写死 12dp，M3 换一版就会跟着动。
        val inset = icon.left - button.left
        val floorLabel = composeRule.onNode(
            hasText("#1") and hasAnyAncestor(hasTestTag(commentTag(1))),
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot()
        assertEquals("最后一个按钮的墨水应落在楼层号那条边上", floorLabel.right.value, (replyAction.right - inset).value, 1f)
        composeRule.onNodeWithTag("reply-preview-${commentTag(3)}").assertDoesNotExist()
        composeRule.onNodeWithTag("comment-replies-${commentTag(2)}").assertDoesNotExist()
        composeRule.onNodeWithTag("comment-replies-${commentTag(3)}").assertDoesNotExist()
        val positions = (1..3).map { composeRule.onNodeWithTag(commentTag(it)).getUnclippedBoundsInRoot().top }
        assertTrue(positions.zipWithNext().all { (earlier, later) -> earlier < later })
        capture("normal-collapsed")
    }

    @Test
    fun `展开只展示直接回复并通过同一图标按钮收起`() {
        setScreen(listOf(replyComment(1), replyComment(2), replyComment(3, 1), replyComment(4, 3), replyComment(5, 1)))
        composeRule.onNodeWithContentDescription("2 条回复").performClick()

        composeRule.onNodeWithTag("reply-preview-${commentTag(3)}")
            .assertIsDisplayed()
            .assertTextContains("reader3")
            .assertTextContains("#3")
            .assertTextContains("comment 3", substring = true)
        composeRule.onNodeWithTag("reply-preview-${commentTag(5)}").assertExists()
        composeRule.onNodeWithTag("reply-preview-${commentTag(4)}").assertDoesNotExist()
        composeRule.onNodeWithText("收起回复").assertDoesNotExist()
        capture("normal-expanded")

        toggleReplies(1, count = 2)
        composeRule.onNodeWithTag("reply-preview-${commentTag(3)}").assertDoesNotExist()
        composeRule.onNodeWithTag("reply-preview-${commentTag(5)}").assertDoesNotExist()
        composeRule.onNodeWithText("comment 1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("2 条回复").assertIsDisplayed()
    }

    @Test
    fun `中间评论同时展示回复目标和直接回复并独立收起`() {
        setScreen(listOf(replyComment(1), replyComment(2, 1), replyComment(3, 2)))
        composeRule.onNodeWithTag("reply-target-preview-${commentTag(2)}").assertDoesNotExist()
        openReplyTarget(2)
        toggleReplies(2)
        composeRule.onNodeWithTag("post-comments").performScrollToNode(hasTestTag(commentTag(2)))

        val target = composeRule.onNodeWithTag("reply-target-preview-${commentTag(2)}")
        target.assertIsDisplayed()
            .assertTextContains("回复给")
            .assertTextContains("reader1")
            .assertTextContains("#1")
            .assertTextContains("comment 1", substring = true)
        val reply = composeRule.onNodeWithTag("reply-preview-${commentTag(3)}")
        reply.assertIsDisplayed().assertTextContains("comment 3", substring = true)
        val body = composeRule.onNode(
            hasText("comment 2", substring = true) and hasAnyAncestor(hasTestTag(commentTag(2))),
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot()
        assertTrue(target.getUnclippedBoundsInRoot().bottom <= body.top)
        assertTrue(body.bottom <= reply.getUnclippedBoundsInRoot().top)
        capture("reply-both-directions")

        composeRule.onNodeWithContentDescription("收起引用").performClick()
        target.assertDoesNotExist()
        reply.assertIsDisplayed()
        openReplyTarget(2)
        toggleReplies(2)
        reply.assertDoesNotExist()
        target.assertIsDisplayed()
    }

    @Test
    fun `回复目标不在已加载分页时仍保留楼层跳转入口`() {
        var requestedFloor: String? = null
        setScreen(listOf(replyComment(11, 2), replyComment(12, 11)), onJumpToFloor = { requestedFloor = it })
        toggleReplies(11)
        openReplyTarget(11)

        assertEquals("#2", requestedFloor)
        composeRule.onNodeWithTag("reply-target-preview-${commentTag(11)}").assertDoesNotExist()
        composeRule.onNodeWithTag("reply-preview-${commentTag(12)}").assertIsDisplayed()
    }

    @Test
    fun `补入前页后两个方向的展开状态都留在原评论`() {
        val page = listOf(replyComment(11), replyComment(12, 11), replyComment(13, 12))
        val state = setScreen(page)
        openReplyTarget(12)
        toggleReplies(12)
        state.value = screenState((1..10).map { replyComment(it) } + page)

        composeRule.onNodeWithTag("post-comments").performScrollToNode(hasTestTag(commentTag(12)))
        composeRule.onNodeWithTag("reply-target-preview-${commentTag(12)}").assertIsDisplayed().assertTextContains("reader11")
        composeRule.onNodeWithTag("reply-preview-${commentTag(13)}").assertIsDisplayed()
        composeRule.onNodeWithTag("reply-target-preview-${commentTag(2)}").assertDoesNotExist()
    }

    @Test
    fun `屏蔽的回复目标须主动显示才能看到作者和正文`() {
        setScreen(listOf(replyComment(1).copy(isBlocked = true), replyComment(2, 1)))
        openReplyTarget(2)
        val preview = hasTestTag("reply-target-preview-${commentTag(2)}")
        composeRule.onNode(hasText("reader1") and hasAnyAncestor(preview), useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNode(hasText("comment 1") and hasAnyAncestor(preview), useUnmergedTree = true).assertDoesNotExist()

        composeRule.onNode(hasText("显示") and hasAnyAncestor(preview)).performClick()
        composeRule.onNodeWithTag("reply-target-preview-${commentTag(2)}")
            .assertTextContains("reader1")
            .assertTextContains("comment 1", substring = true)
    }

    @Test
    fun `回复目标预览里的链接点击也只跳到目标原楼层`() {
        var openedLink: String? = null
        var requestedFloor: String? = null
        var recorded: Pair<Int, String?>? = null
        val comments = (1..25).map { floor ->
            val comment = replyComment(floor, if (floor == 14) 2 else null)
            if (floor == 2) {
                comment.copy(nodes = listOf(RichNode.Paragraph(listOf(InlineNode.Link("original link", "https://example.com")))))
            } else {
                comment
            }
        }
        setScreen(
            comments,
            pendingScroll = PendingScroll(2, "#14"),
            onLinkClick = { openedLink = it },
            onJumpToFloor = { requestedFloor = it },
            onReadingPositionChange = { page, floor -> recorded = page to floor },
        )
        openReplyTarget(14)
        composeRule.onNode(
            hasText("original link") and hasAnyAncestor(hasTestTag("reply-target-preview-${commentTag(14)}")),
            useUnmergedTree = true,
        ).performTouchInput { click(Offset(6f, centerY)) }

        composeRule.onNodeWithTag(commentTag(2)).assertIsDisplayed()
        composeRule.waitForIdle()
        assertEquals(1 to "#2", recorded)
        assertNull(openedLink)
        assertNull(requestedFloor)
    }

    @Test
    fun `回复楼主可以预览正文且正文更新后引用同步更新`() {
        val state = setScreen(listOf(replyComment(1, 0)))
        openReplyTarget(1)
        composeRule.onNodeWithTag("reply-target-preview-${commentTag(1)}").assertTextContains("comment 0", substring = true)
        state.value = state.value.copy(body = replyComment(0).copy(nodes = listOf(RichNode.Paragraph(listOf(InlineNode.Text("updated original post"))))))

        composeRule.onNodeWithTag("reply-target-preview-${commentTag(1)}").assertTextContains("updated original post", substring = true)
        state.value = PostDetailUiState(postId = 42)
        composeRule.onNodeWithTag("reply-target-preview-${commentTag(1)}").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w320dp-h800dp")
    fun `窄屏大字仍能同时查看原消息和后续回复`() {
        val parent = replyComment(1).copy(
            authorName = "这是一个需要省略显示的很长的作者名字",
            nodes = List(12) { RichNode.Paragraph(listOf(InlineNode.Text("这是一条用来检查引用摘要高度的原始消息。"))) },
        )
        setScreen(listOf(parent, replyComment(2, 1), replyComment(3, 2)), fontScale = 1.5f)
        openReplyTarget(2)
        toggleReplies(2)
        composeRule.onNodeWithTag("post-comments").performScrollToNode(hasTestTag(commentTag(2)))

        val card = composeRule.onNodeWithTag("reply-target-preview-${commentTag(2)}").getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertTrue(card.left >= root.left && card.right <= root.right)
        assertTrue("原消息摘要不应挤满屏幕", card.bottom - card.top < (root.bottom - root.top) / 2)
        composeRule.onNodeWithContentDescription("收起引用").assertIsDisplayed()
        composeRule.onNodeWithTag("reply-preview-${commentTag(3)}").assertIsDisplayed()
        capture("reply-both-directions-large-font")
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
            hasText("comment 12", substring = true) and hasAnyAncestor(hasTestTag("reply-preview-${commentTag(12)}")),
            useUnmergedTree = true,
        ).performTouchInput { click(Offset(6f, centerY)) }

        composeRule.onNodeWithTag(commentTag(12)).assertIsDisplayed()
        composeRule.waitForIdle()
        assertEquals(2 to "#12", recorded)
        assertNull(openedLink)
        assertNull(requestedFloor)
        capture("normal-reply-jump")
    }

    /**
     * 预览里的代码块不再画「复制」—— 一个复制不到东西的按钮比没有更糟。之前它画着、按下去
     * 的结果却是跳楼层，因为覆盖层把整片触摸都收走了。现在它根本不在，整片触摸落到卡片上。
     */
    @Test
    fun `预览里的代码块不提供复制并把点击交给卡片`() {
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

        val preview = hasTestTag("reply-preview-${commentTag(12)}")
        composeRule.onNode(hasContentDescription("复制") and hasAnyAncestor(preview)).assertDoesNotExist()

        composeRule.onNode(
            hasText("val answer = 42", substring = true) and hasAnyAncestor(preview),
            useUnmergedTree = true,
        ).performTouchInput { click() }

        // 跳到了原楼层，而那里的同一个代码块照常可以复制。
        composeRule.onNodeWithTag(commentTag(12)).assertIsDisplayed()
        composeRule.onNode(hasContentDescription("复制") and hasAnyAncestor(hasTestTag(commentTag(12)))).assertExists()
    }

    @Test
    fun `已展开的直接回复随分页更新且不混入间接回复`() {
        val initial = listOf(replyComment(1), replyComment(2), replyComment(3, 1))
        val state = setScreen(initial)
        composeRule.onNodeWithContentDescription("1 条回复").performClick()
        state.value = screenState(initial + listOf(replyComment(11, 1), replyComment(12, 3)))

        composeRule.onNodeWithContentDescription("2 条回复").assertIsDisplayed()
        composeRule.onNodeWithTag("reply-preview-${commentTag(3)}").assertIsDisplayed()
        composeRule.onNodeWithTag("reply-preview-${commentTag(11)}").assertExists()
        composeRule.onNodeWithTag("reply-preview-${commentTag(12)}").assertDoesNotExist()
    }

    @Test
    fun `补入前页后保留原评论的展开状态`() {
        val page = listOf(replyComment(11), replyComment(12, 11))
        val state = setScreen(page)
        composeRule.onNodeWithContentDescription("1 条回复").performClick()
        state.value = screenState((1..10).map { replyComment(it) } + page)

        composeRule.onNodeWithTag("post-comments").performScrollToNode(hasTestTag(commentTag(11)))
        composeRule.onNodeWithTag("reply-preview-${commentTag(12)}").assertIsDisplayed()
    }

    @Test
    fun `被屏蔽回复的预览沿用原来的显示规则`() {
        setScreen(listOf(replyComment(1), replyComment(2, 1).copy(isBlocked = true)))
        composeRule.onNodeWithContentDescription("1 条回复").performClick()
        composeRule.onNodeWithTag("reply-preview-${commentTag(2)}").assertDoesNotExist()
        composeRule.onNodeWithText("reader2").assertDoesNotExist()
        composeRule.onNode(
            hasText("显示") and hasAnyAncestor(hasTestTag("comment-replies-${commentTag(1)}")),
        ).performClick()
        composeRule.onNodeWithTag("reply-preview-${commentTag(2)}").assertIsDisplayed()
    }

    @Test
    fun `缓存清空时不保留旧的回复卡片`() {
        val state = setScreen(listOf(replyComment(1), replyComment(2, 1)))
        composeRule.onNodeWithContentDescription("1 条回复").performClick()
        state.value = PostDetailUiState(postId = 42)

        composeRule.onNodeWithTag("reply-preview-${commentTag(2)}").assertDoesNotExist()
        composeRule.onNodeWithText("收起回复").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w320dp-h800dp")
    fun `窄屏大字的长回复通过图标收起后回到原评论`() {
        val reply = replyComment(2, 1)
        val paragraphs = List(12) { RichNode.Paragraph(listOf(InlineNode.Text("这是一段用于检查长回复阅读位置的内容。"))) }
        setScreen(listOf(replyComment(1), reply.copy(nodes = reply.nodes + paragraphs)), fontScale = 1.5f)
        composeRule.onNodeWithTag("post-comments").performScrollToNode(hasContentDescription("1 条回复"))
        composeRule.onNodeWithContentDescription("1 条回复").performClick()

        val card = composeRule.onNodeWithTag("reply-preview-${commentTag(2)}").getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertTrue(card.left >= root.left && card.right <= root.right)
        capture("normal-large-font")
        toggleReplies(1)
        composeRule.onNodeWithTag("reply-preview-${commentTag(2)}").assertDoesNotExist()
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

        composeRule.onNodeWithTag(commentTag(14)).assertIsDisplayed()
        composeRule.onNodeWithTag("comment-conversation").assertDoesNotExist()
        composeRule.onNodeWithTag("reply-preview-${commentTag(15)}").assertDoesNotExist()
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
        composeRule.onNodeWithTag("reply-target-button-${commentTag(2)}").assertDoesNotExist()
    }

    /**
     * A 发 #1，B 回 #1 发了 #2，A 回 #2 发了 #3。#1 底下只列 #2，但 #2 那张卡自己带「1 条回复」，
     * 点开才是 #3 —— 整段对话一层一层往下走，而不是在 #1 那里一次拉平。
     */
    @Test
    fun `回复卡可以再展开它自己的回复`() {
        setScreen(listOf(replyComment(1), replyComment(2, 1), replyComment(3, 2)))
        // 限定到 #1：#2 那一行自己也有一条回复，不限定就撞上两个同名节点。
        toggleReplies(1)

        val nested = "reply-preview-${commentTag(2)}"
        composeRule.onNodeWithTag(nested).assertIsDisplayed()
        // #3 是 #2 的回复，不是 #1 的，所以这一层看不到它。
        composeRule.onNodeWithTag("reply-preview-${commentTag(3)}").assertDoesNotExist()

        composeRule.onNodeWithTag("post-comments").performScrollToNode(hasTestTag("reply-expand-${commentTag(2)}"))
        composeRule.onNodeWithTag("reply-expand-${commentTag(2)}").performClick()
        composeRule.onNodeWithTag("reply-preview-${commentTag(3)}")
            .assertIsDisplayed()
            .assertTextContains("comment 3", substring = true)
        capture("normal-nested-expanded")

        composeRule.onNodeWithTag("reply-expand-${commentTag(2)}").performClick()
        composeRule.onNodeWithTag("reply-preview-${commentTag(3)}").assertDoesNotExist()
        composeRule.onNodeWithTag(nested).assertIsDisplayed()
    }

    /**
     * 嵌套到上限后不再往里套：那一层的「N 条回复」改成跳到原楼层，在主列表里它自己又是第 0 层。
     * 没有这个闸，一条长对话会把卡片套到正文只剩几个字宽。
     */
    @Test
    fun `嵌套到上限后改为跳到原楼层`() {
        var jumped: String? = null
        setScreen(replyChain(6), onJumpToFloor = { jumped = it })
        composeRule.onNode(
            hasContentDescription("1 条回复") and hasAnyAncestor(hasTestTag(commentTag(1))),
        ).performClick()
        listOf(2, 3).forEach { floor ->
            val tag = "reply-expand-${commentTag(floor)}"
            composeRule.onNodeWithTag("post-comments").performScrollToNode(hasTestTag(tag))
            composeRule.onNodeWithTag(tag).performClick()
        }

        capture("normal-nested-deepest")

        // 第三层的卡片是 #4，它的「1 条回复」不再展开 #5，而是把人送到 #4 那一楼。
        val deepest = "reply-expand-${commentTag(4)}"
        composeRule.onNodeWithTag("post-comments").performScrollToNode(hasTestTag(deepest))
        composeRule.onNodeWithTag(deepest).performClick()
        composeRule.onNodeWithTag("reply-preview-${commentTag(5)}").assertDoesNotExist()
        composeRule.onNodeWithTag(commentTag(4)).assertIsDisplayed()
        assertNull("楼层在已加载列表里，应该滚过去而不是请求它那一页", jumped)
    }

    /**
     * 表头项数是 `ThreadList` 在第一条评论之前发出的项数，写死过一次就漂过一次：正文那一项为了
     * 共享元素动画改成无条件发出后，`2 + (body != null)` 还留在原地，于是每一次「从通知打开后面
     * 页的帖子」都落在目标楼层的上一楼 —— 恰好是整套定位机制唯一存在的理由。
     *
     * 断言落点而不是数字：以后在评论前加一项（锁帖横幅、加载前页提示）而忘了登记，这里会红。
     */
    @Test
    fun `没有正文时通知的楼层落在它自己身上`() {
        setScreen(
            comments = (11..20).map { replyComment(it) },
            pendingScroll = PendingScroll(page = 2, floor = "#14"),
            withBody = false,
        )
        composeRule.waitForIdle()

        val list = composeRule.onNodeWithTag("post-comments").getUnclippedBoundsInRoot()
        val target = composeRule.onNodeWithTag(commentTag(14)).getUnclippedBoundsInRoot()
        assertEquals("目标楼层应停在列表顶部", list.top.value, target.top.value, 1f)
        composeRule.onNodeWithTag(commentTag(13)).assertIsNotDisplayed()
    }

    /**
     * 主列表的键带着页码（见 [commentKeys]）：一条楼层可以同时存在于两个已加载页，而一行的身份
     * 不能因为别处出现了重复就改名。测试用同一条规则算出来，不在五十处各写一遍。
     */
    private fun commentTag(floor: Int): String = "comment-$floor-p${NodeSeekSite.pageOfFloor(floor)}"

    private fun openReplyTarget(floor: Int) {
        val tag = "reply-target-button-${commentTag(floor)}"
        composeRule.onNodeWithTag("post-comments").performScrollToNode(hasTestTag(tag))
        composeRule.onNodeWithTag(tag).performClick()
    }

    private fun toggleReplies(floor: Int, count: Int = 1) {
        val button = hasContentDescription("$count 条回复") and hasAnyAncestor(hasTestTag(commentTag(floor)))
        composeRule.onNodeWithTag("post-comments").performScrollToNode(button)
        composeRule.onNode(button).performClick()
    }

    private fun screenState(comments: List<PostContent>, withBody: Boolean = true): PostDetailUiState {
        val pages = comments.map { NodeSeekSite.pageOfFloor(requireNotNull(NodeSeekSite.parseFloorNumber(it.floor))) }
        return PostDetailUiState(
            postId = 42,
            title = "普通评论模式",
            body = replyComment(0).takeIf { withBody },
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
        withBody: Boolean = true,
    ): MutableState<PostDetailUiState> {
        val state = mutableStateOf(screenState(comments, withBody).copy(pendingScroll = pendingScroll))
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
