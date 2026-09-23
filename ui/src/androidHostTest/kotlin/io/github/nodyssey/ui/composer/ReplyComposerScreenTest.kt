package io.github.nodyssey.ui.composer

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.editor.toolbarLayout
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The reply sheet's editor chrome (2c).
 *
 * The sheet used to be where the toolbar geometry was decided, because it kept 发布 pinned inside the
 * strip and had to squeeze six keys to 42dp beside it. 2c moved 发布 into the header and the
 * formatting keys onto the 格式 card; what is asserted here is that both moves held — full-size keys,
 * the formatting still one tap away, and nothing the old strip offered lost on the way.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class ReplyComposerScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setSheet(state: ReplyComposerUiState) {
        composeRule.setContent {
            PlazaTheme {
                ReplyComposerHost(
                    state = state,
                    onDismiss = {},
                    bodyState = rememberTextFieldState(state.body),
                    onClearReplyTo = {},
                    onPreviewChange = {},
                    onPickImages = {},
                    onRemoveAttachment = {},
                    onRetryAttachment = {},
                    onRetryFailedUploads = {},
                    onPublish = {},
                    onClearError = {},
                    onSignIn = {},
                    onVerify = {},
                    onToolbarChange = {},
                    onToolbarReset = {},
                    onCreateVote = { _, _, _, _, _ -> },
                    onDismissVoteCreation = {},
                    payeeUid = { 52_425L },
                    onInsertReceiveCode = { _, _, _, _ -> },
                )
            }
        }
    }

    private fun draft() =
        ReplyComposerUiState(
            postId = 1L,
            visible = true,
            body = "确实是学计算机的，不过这个项目只有架构是自己定的。",
        )

    @Test
    fun `the bar carries the inserts, and 发布 sits in the header`() {
        setSheet(draft())

        listOf("图片", "表情", "提到某人").forEach { key ->
            composeRule.onNodeWithContentDescription(key).assertIsDisplayed()
        }
        // 先写，后排版: formatting waits behind the 格式 pill rather than on the bar.
        composeRule.onNodeWithContentDescription("加粗").assertDoesNotExist()
        composeRule.onNodeWithText("格式").assertIsDisplayed()
        composeRule.onNodeWithText("发布").assertIsDisplayed()
    }

    @Test
    fun `the keys are full size now that 发布 left the strip`() {
        setSheet(draft())

        // 42dp for as long as 发布 was pinned at the strip's end; the header took it, and the keys
        // went back to Material's 48dp minimum.
        composeRule.onNodeWithContentDescription("图片").assertWidthIsEqualTo(48.dp)
        composeRule.onNodeWithContentDescription("表情").assertWidthIsEqualTo(48.dp)
    }

    @Test
    fun `the 格式 card offers every formatting key and the wrench`() {
        setSheet(draft())

        composeRule.onNodeWithText("格式").performClick()

        listOf("加粗", "删除线", "二级标题", "链接", "引用", "无序列表", "行内代码", "斜体").forEach { key ->
            composeRule.onNodeWithContentDescription(key).assertIsDisplayed()
        }
        composeRule.onNodeWithContentDescription("自定义工具栏").assertIsDisplayed()
    }

    @Test
    fun `the header names the floor and the chip names the author`() {
        setSheet(draft().copy(replyTo = FloorReference(floor = 7, author = "轻舟", excerpt = "电源那条深有体会")))

        composeRule.onNodeWithText("回复 #7").assertIsDisplayed()
        composeRule.onNodeWithText("@轻舟 #7").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("移除回复对象").assertIsDisplayed()
    }

    /**
     * The reply box carries the APP menu on its bar, beside the inserts.
     *
     * The site's own reply editor has always offered 投票 and 收款码; this app only grew the entry with
     * the 收款码, and the reply sheet is the half that is easy to forget.
     */
    @Test
    fun `the APP menu is offered in the reply sheet and opens both entries`() {
        setSheet(draft())

        composeRule.onNodeWithContentDescription("APP").assertExists()
        composeRule.onNodeWithContentDescription("APP").performClick()

        composeRule.onNodeWithText("插入投票").assertIsDisplayed()
        composeRule.onNodeWithText("插入星辰收款码").assertIsDisplayed()
    }

    @Test
    fun `the strip shows the stored arrangement rather than the defaults`() {
        setSheet(
            draft().copy(
                toolbar = toolbarLayout(listOf("EMOJI", "BOLD"), EditorActions.Reply),
            ),
        )

        composeRule.onNodeWithContentDescription("表情").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("加粗").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("提到某人").assertDoesNotExist()
    }

    @Test
    fun `preview is chrome, not a formatting key`() {
        setSheet(draft())

        // In the sheet's own header row, beside 草稿已保存 and the close button, which makes it a
        // plain `IconButton` — 40dp in Material 3 1.5, the same box the ✕ next to it gets — rather
        // than one of the strip's 42dp keys. Header buttons are spaced, so their 48dp minimum touch
        // targets have room to expand into; the strip's abutting keys do not.
        composeRule.onNodeWithContentDescription("预览").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("预览").assertWidthIsEqualTo(40.dp)
    }
}
