package io.github.nodyssey.ui.composer

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.theme.NodysseyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The reply sheet's editor chrome (6d / C6).
 *
 * This is the surface the toolbar geometry was decided on: it is the only one that keeps 发布 pinned
 * inside the strip, so it is the only one where the keys and the trailing button compete for the same
 * 360dp. The size and the no-scroll claim are asserted here rather than in a comment, because both
 * silently stop being true the moment an action is added to the list.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class ReplyComposerScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setSheet(state: ReplyComposerUiState) {
        composeRule.setContent {
            NodysseyTheme {
                ReplyComposerHost(
                    state = state,
                    onDismiss = {},
                    bodyState = rememberTextFieldState(state.body),
                    onClearQuote = {},
                    onPreviewChange = {},
                    onPickImages = {},
                    onRemoveAttachment = {},
                    onRetryAttachment = {},
                    onRetryFailedUploads = {},
                    onPublish = {},
                    onClearError = {},
                    onToolbarChange = {},
                    onToolbarReset = {},
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
    fun `every formatting key fits beside the pinned publish button`() {
        setSheet(draft())

        listOf("加粗", "行内代码", "引用", "提到某人", "图片", "表情").forEach { key ->
            composeRule.onNodeWithContentDescription(key).assertIsDisplayed()
        }
        composeRule.onNodeWithText("发布").assertIsDisplayed()
    }

    @Test
    fun `the keys are the compact size the sheet was measured for`() {
        setSheet(draft())

        // 42dp, not the 48dp every other strip gets: see EditorToolbarDefaults.CompactKeySize.
        composeRule.onNodeWithContentDescription("加粗").assertWidthIsEqualTo(42.dp)
        composeRule.onNodeWithContentDescription("表情").assertWidthIsEqualTo(42.dp)
    }

    @Test
    fun `the wrench rides at the end of the keys, not in the pinned slot`() {
        setSheet(draft())

        // Reachable, but only by scrolling past the six formatting keys — 发布 keeps the one pinned
        // slot, and the wrench is the key nobody reaches for mid-sentence.
        composeRule.onNodeWithContentDescription("自定义工具栏").assertExists()
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
        composeRule.onNodeWithContentDescription("引用").assertDoesNotExist()
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
