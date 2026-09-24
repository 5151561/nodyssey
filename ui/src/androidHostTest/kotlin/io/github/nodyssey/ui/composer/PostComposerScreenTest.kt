package io.github.nodyssey.ui.composer

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screen-level tests for the post editor. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class PostComposerScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var viewMode = ComposerViewMode.CONTENT

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
                    onRemoveAttachment = {},
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
    fun `tapping the lit view goes back to the text`() {
        setScreen(draftState().copy(viewMode = ComposerViewMode.COMPARE))

        // 内容 has no button of its own: it is what is left when neither view is lit.
        composeRule.onNodeWithContentDescription("对照").assertIsOn().performClick()

        assertEquals(ComposerViewMode.CONTENT, viewMode)
    }
}
