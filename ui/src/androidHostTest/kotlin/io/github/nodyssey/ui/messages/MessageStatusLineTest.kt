package io.github.nodyssey.ui.messages

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The line under a bubble of mine: what became of it, and the way to send it again (3d). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class MessageStatusLineTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val retried = mutableListOf<String>()

    private fun setScreen() {
        composeRule.setContent {
            PlazaTheme {
                MessageThreadScreen(
                    state =
                    MessageThreadUiState(
                        uid = 4471,
                        userName = "iwil",
                        nowMillis = NOW,
                        messages =
                        listOf(
                            mine(id = "1", content = "那大概什么时候", at = NOW - 60_000L, status = SendStatus.SENT),
                            mine(id = "2", content = "顺便问下星辰能转账吗", at = NOW, status = SendStatus.FAILED),
                        ),
                    ),
                    draftState = TextFieldState(),
                    onBack = {},
                    onSignIn = {},
                    onVerify = {},
                    onOpenBrowser = {},
                    onOpenSpace = {},
                    onRetryLoad = {},
                    onSend = {},
                    onRetrySend = { retried += it },
                    onQuote = {},
                    onRemoveQuote = {},
                    onPickImages = {},
                    onRemoveAttachment = {},
                    onRetryAttachment = {},
                    onToolbarChange = {},
                    onToolbarReset = {},
                )
            }
        }
    }

    /**
     * 「⚠ 发送失败 · 重试」 is one target, and its words end where 「已送达」 ends.
     *
     * 重试 used to be a 48dp box of its own with the word at its start. The line sits against the
     * end of the screen under a bubble of mine, so the rest of that box was empty space after the
     * word, and the whole line stood about 20dp further in than every other status line.
     */
    @Test
    fun `a failed message's whole status line retries, and ends where the others end`() {
        setScreen()

        val target = composeRule.onNode(hasClickAction() and hasText("重试"))
        target
            .assert(hasText("发送失败"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertEquals("重试", target.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        val height = target.getUnclippedBoundsInRoot().height
        assertTrue("the target is $height tall", height >= 48.dp)

        val delivered = composeRule.onNodeWithText("已送达", substring = true, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val retry = composeRule.onNodeWithText("重试", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(delivered.right.value, retry.right.value, 1f)

        target.performClick()
        assertEquals(listOf("2"), retried)
    }

    private fun mine(
        id: String,
        content: String,
        at: Long,
        status: SendStatus,
    ) = MessageBubble(
        id = id,
        isMine = true,
        content = content,
        isMarkdown = true,
        sentAtMillis = at,
        sentAtText = null,
        status = status,
    )

    private companion object {
        const val NOW = 1_785_000_000_000L
    }
}
