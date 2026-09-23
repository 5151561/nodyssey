package io.github.nodyssey.render

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import io.github.nodyssey.ui.messages.MessageBubble
import io.github.nodyssey.ui.messages.MessageThreadScreen
import io.github.nodyssey.ui.messages.MessageThreadUiState
import io.github.nodyssey.ui.messages.SendStatus
import io.github.plaza.core.TimeFormat
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 私信对话 in both themes, against boards 3d and 3e: the thread with every delivery state under the
 * floating title card, and the same thread with a quote waiting and the tool grid open. See
 * [PostListScreenRenderTest] on the null avatars.
 *
 * 3e's long-press menu is a popup window and does not appear in a capture of the screen's root; it
 * is a stock `DropdownMenu`, and [io.github.nodyssey.ui.messages.MessageBubbleActionsTest] covers it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class MessageThreadScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun Screen(
        darkTheme: Boolean,
        state: MessageThreadUiState = STATE,
        draft: String = "",
    ) {
        PlazaTheme(darkTheme = darkTheme) {
            MessageThreadScreen(
                state = state,
                draftState = remember { TextFieldState(draft) },
                onBack = {},
                onSignIn = {},
                onVerify = {},
                onOpenBrowser = {},
                onOpenSpace = {},
                onRetryLoad = {},
                onToggleMarkdown = {},
                onSend = {},
                onRetrySend = {},
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

    @Test
    fun `the conversation in light`() {
        composeRule.setContent { Screen(darkTheme = false) }

        composeRule.onRoot().captureRender("message-thread-light")
    }

    @Test
    fun `the conversation in dark`() {
        composeRule.setContent { Screen(darkTheme = true) }

        composeRule.onRoot().captureRender("message-thread-dark")
    }

    @Test
    fun `a quote and the tool grid, in light`() {
        composeRule.setContent { Screen(darkTheme = false, state = QUOTING, draft = "能发下 `mtr` 的结果吗") }
        composeRule.onNodeWithContentDescription("附件与格式").performClick()

        composeRule.onRoot().captureRender("message-thread-tools-light")
    }

    @Test
    fun `a quote and the tool grid, in dark`() {
        composeRule.setContent { Screen(darkTheme = true, state = QUOTING, draft = "能发下 `mtr` 的结果吗") }
        composeRule.onNodeWithContentDescription("附件与格式").performClick()

        composeRule.onRoot().captureRender("message-thread-tools-dark")
    }

    private companion object {
        val NOW = TimeFormat.parseTimestamp("2026-09-23 10:53:00")!!

        private fun bubble(
            id: String,
            mine: Boolean,
            content: String,
            at: String,
            status: SendStatus = SendStatus.SENT,
        ) = MessageBubble(
            id = id,
            isMine = mine,
            content = content,
            isMarkdown = true,
            sentAtMillis = TimeFormat.parseTimestamp(at),
            sentAtText = null,
            status = status,
        )

        val MESSAGES =
            listOf(
                bubble("1", false, "看到你那篇 NAS 帖子了，想问下你东京那台跑回国是什么线路？", "2026-09-22 21:30:00"),
                bubble("2", true, "软银直连，晚高峰也还行。你是打算放什么业务？", "2026-09-22 21:34:00"),
                bubble("3", false, "主要是拉国内的备份，大概每天 20G 左右", "2026-09-23 10:41:00"),
                bubble("4", false, "那条 CN2 线路晚高峰丢包大概 3%，我截图给你看", "2026-09-23 10:48:00"),
                bubble("5", true, "20G 的话软银够用，我把 iperf 的结果整理一下发你", "2026-09-23 10:52:00", SendStatus.SENDING),
                bubble("6", true, "顺便问下你用的是哪家的 Btrfs 快照脚本？", "2026-09-23 10:52:30", SendStatus.FAILED),
            )

        val STATE =
            MessageThreadUiState(
                uid = 18842,
                userName = "轻舟",
                level = 3,
                messages = MESSAGES,
                nowMillis = NOW,
            )

        val QUOTING =
            STATE.copy(
                messages = MESSAGES.take(4),
                quotes = listOf(MESSAGES[3]),
                hasDraftText = true,
            )
    }
}
