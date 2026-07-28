package io.github.nodyssey.ui.composer

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.nodyssey.data.composer.ImageAttachment
import io.github.nodyssey.data.composer.PostDraft
import io.github.nodyssey.data.composer.UploadStatus
import io.github.nodyssey.ui.theme.NodysseyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screen-level tests for the post editor (boards 7a–7c and C5). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class PostComposerScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var viewMode = ComposerViewMode.CONTENT
    private var removed: ImageAttachment? = null

    private fun setScreen(state: PostComposerUiState) {
        composeRule.setContent {
            NodysseyTheme {
                PostComposerScreen(
                    state = state,
                    snackbarHostState = SnackbarHostState(),
                    onClose = {},
                    onTitleChange = {},
                    onBodyChange = {},
                    onBoardSelect = {},
                    onPermissionSelect = {},
                    onViewModeChange = { viewMode = it },
                    onPickImages = {},
                    onRemoveAttachment = { removed = it },
                    onRetryAttachment = {},
                    onPublish = {},
                    onContinueDraft = {},
                    onDiscardDraft = {},
                )
            }
        }
    }

    private fun draftState(
        title: String = "Debian 13 上用 nftables 做端口转发的坑",
        body: String = "最近把小鸡从 Debian 12 升到 13。",
    ) = PostComposerUiState(
        title = title,
        body = body,
        boardSlug = "tech",
        boardTitle = "技术",
        draftDecisionMade = true,
        isSignedIn = true,
    )

    @Test
    fun `the editor shows the board, the reading limit and the title counter`() {
        setScreen(draftState())

        composeRule.onNodeWithText("技术").assertIsDisplayed()
        composeRule.onNodeWithText("公开").assertIsDisplayed()
        composeRule.onNodeWithText("29/60").assertIsDisplayed()
    }

    @Test
    fun `the view switch offers all three site views`() {
        setScreen(draftState())

        composeRule.onNodeWithText("内容").assertIsDisplayed()
        composeRule.onNodeWithText("对照").assertIsDisplayed()
        composeRule.onNodeWithText("预览").performClick()

        assertEquals(ComposerViewMode.PREVIEW, viewMode)
    }

    @Test
    fun `the rules card is on every preview, not only the tech board`() {
        setScreen(draftState().copy(viewMode = ComposerViewMode.PREVIEW, boardSlug = "daily", boardTitle = "日常"))

        composeRule.onNodeWithText("重要提醒").assertIsDisplayed()
        composeRule.onNodeWithText("敏感话题请发内版 · 发卡站 / 大量出售必须发推广 · 禁止人身攻击 · 违规将受惩罚")
            .assertIsDisplayed()
    }

    @Test
    fun `a publish in flight replaces the button rather than leaving it tappable`() {
        setScreen(draftState().copy(isPublishing = true))

        composeRule.onNodeWithText("发布中").assertIsDisplayed()
    }

    @Test
    fun `every upload state in the queue is labelled, and a cell can be dismissed`() {
        val failed = ImageAttachment("3", "content://c", "c.png", UploadStatus.FAILED)
        setScreen(
            draftState().copy(
                attachments = listOf(
                    ImageAttachment("1", "content://a", "a.png", UploadStatus.UPLOADING, progress = 0.45f),
                    ImageAttachment("2", "content://b", "b.png", UploadStatus.UPLOADED, remoteUrl = "https://x/1.webp"),
                    failed,
                    ImageAttachment("4", "content://d", "d.png", UploadStatus.WAITING),
                ),
            ),
        )

        composeRule.onNodeWithText("上传中 45%").assertIsDisplayed()
        composeRule.onNodeWithText("已上传").assertIsDisplayed()
        composeRule.onNodeWithText("失败 · 重试").assertIsDisplayed()
        composeRule.onNodeWithText("等待中").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("移除 c.png").performClick()

        assertEquals(failed, removed)
    }

    @Test
    fun `the draft dialog counts the images it is holding on to`() {
        setScreen(
            PostComposerUiState(
                pendingDraft = PostDraft(
                    title = "洛杉矶 4837 年付小鸡测评",
                    body = "正文\n\n![a.png](https://cdn.nodeimage.com/i/a.webp)",
                    boardSlug = "review",
                    boardTitle = "测评",
                    savedAtMillis = 1_700_000_000_000L,
                ),
            ),
        )

        composeRule.onNodeWithText("继续上次的草稿？").assertIsDisplayed()
        composeRule.onNodeWithText("含 1 张图片", substring = true).assertIsDisplayed()
    }
}
