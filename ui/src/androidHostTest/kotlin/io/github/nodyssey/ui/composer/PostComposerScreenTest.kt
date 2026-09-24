package io.github.nodyssey.ui.composer

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import io.github.nodyssey.data.composer.ImageAttachment
import io.github.nodyssey.data.composer.PostDraft
import io.github.nodyssey.data.composer.UploadStatus
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Sizes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screen-level tests for the post editor (boards 1d / 1e, and 7a–7c and C5 before them). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class PostComposerScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var viewMode = ComposerViewMode.CONTENT
    private var removed: ImageAttachment? = null

    private lateinit var titleState: TextFieldState

    private fun setScreen(state: PostComposerUiState) {
        composeRule.setContent {
            PlazaTheme {
                PostComposerScreen(
                    state = state,
                    titleState = rememberTextFieldState(state.title).also { titleState = it },
                    bodyState = rememberTextFieldState(state.body),
                    snackbarHostState = SnackbarHostState(),
                    onClose = {},
                    onBoardSelect = {},
                    onPermissionSelect = {},
                    onViewModeChange = { viewMode = it },
                    onPickImages = {},
                    onRemoveAttachment = { removed = it },
                    onRetryAttachment = {},
                    onPublish = {},
                    onVerify = {},
                    onContinueDraft = {},
                    onDiscardDraft = {},
                    onToolbarChange = {},
                    onToolbarReset = {},
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

        composeRule.onNodeWithText("发到 技术").assertIsDisplayed()
        composeRule.onNodeWithText("公开").assertIsDisplayed()
        composeRule.onNodeWithText("29/60").assertIsDisplayed()
    }

    /**
     * A line break typed into the middle of the title is dropped and the caret stays where it was.
     *
     * The title wraps, so its return key is live, and the filter that drops the break used to
     * replace the whole title to do it — which put the caret at the end, so the next word landed there.
     */
    @Test
    fun `a line break typed mid-title is dropped without moving the caret`() {
        setScreen(draftState(title = "Debian 13 上的坑"))
        val title = composeRule.onNode(hasSetTextAction() and hasText("Debian 13", substring = true))

        title.performTextInputSelection(TextRange(6))
        title.performTextInput("\n")

        assertEquals("Debian 13 上的坑", titleState.text.toString())
        assertEquals(TextRange(6), titleState.selection)
    }

    @Test
    fun `the top bar still reaches all three site views`() {
        setScreen(draftState())

        composeRule.onNodeWithContentDescription("对照").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("预览").performClick()

        assertEquals(ComposerViewMode.PREVIEW, viewMode)
    }

    @Test
    fun `tapping the lit view goes back to the text`() {
        setScreen(draftState().copy(viewMode = ComposerViewMode.COMPARE))

        // 内容 has no button of its own: it is what is left when neither view is lit.
        composeRule.onNodeWithContentDescription("对照").assertIsOn().performClick()

        assertEquals(ComposerViewMode.CONTENT, viewMode)
    }

    @Test
    fun `formatting waits behind the 格式 pill and the wrench rides with it`() {
        setScreen(draftState())

        // 先写，后排版: the bar carries what is inserted, not what is formatted.
        listOf("图片", "表情", "提到某人", "APP").forEach { key ->
            composeRule.onNodeWithContentDescription(key).assertIsDisplayed()
        }
        composeRule.onNodeWithContentDescription("加粗").assertDoesNotExist()

        composeRule.onNodeWithText("格式").performClick()

        listOf("加粗", "删除线", "二级标题", "链接", "引用", "无序列表", "行内代码", "斜体").forEach { key ->
            composeRule.onNodeWithContentDescription(key).assertIsDisplayed()
        }
        composeRule.onNodeWithContentDescription("自定义工具栏").assertIsDisplayed()

        composeRule.onNodeWithText("收起").performClick()

        composeRule.onNodeWithContentDescription("加粗").assertDoesNotExist()
        composeRule.onNodeWithText("格式").assertIsDisplayed()
    }

    /**
     * On a tablet the quick bar and the 格式 card keep to the column the title and the body are in,
     * and the emoji panel spans the window.
     *
     * The panel stands in for the keyboard, and a keyboard is the window's width. Narrowing the whole
     * editor bar to the text column narrowed the panel along with the keys it was meant for.
     */
    @Test
    @Config(qualifiers = "w1000dp-h800dp")
    fun `on a wide window the emoji panel spans the window while the bar keeps to the text`() {
        setScreen(draftState())
        val column = (1000.dp - Sizes.readableContentWidth) / 2

        composeRule.onNodeWithContentDescription("表情").performClick()

        val key = composeRule.onNodeWithContentDescription("表情").getUnclippedBoundsInRoot()
        assertTrue("the bar's 表情 key at ${key.left}", key.left >= column)
        val pill = composeRule.onNodeWithText("最近使用").getUnclippedBoundsInRoot()
        assertTrue("the panel's first pill at ${pill.left}", pill.left < column)

        composeRule.onNodeWithText("格式").performClick()
        val bold = composeRule.onNodeWithContentDescription("加粗").getUnclippedBoundsInRoot()
        assertTrue("the card's 加粗 key at ${bold.left}", bold.left >= column)
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
        // 发布中… is the top bar's widest state, and the view toggles share the bar with it. Both
        // have to survive 360dp together or the toggles are what the Row measures to nothing.
        composeRule.onNodeWithContentDescription("对照").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("预览").assertIsDisplayed()
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
        // A finished upload says so only to a screen reader: its check mark carries it on screen.
        composeRule.onNodeWithContentDescription("已上传").assertIsDisplayed()
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
