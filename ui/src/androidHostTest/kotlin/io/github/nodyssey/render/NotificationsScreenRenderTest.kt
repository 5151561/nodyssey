package io.github.nodyssey.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.nodyssey.data.ForumNotification
import io.github.nodyssey.data.MessageConversation
import io.github.nodyssey.data.NotificationCategory
import io.github.nodyssey.data.NotificationCounts
import io.github.nodyssey.data.NotificationSource
import io.github.nodyssey.data.NotificationTab
import io.github.nodyssey.data.contentPreview
import io.github.nodyssey.ui.notifications.NewConversationState
import io.github.nodyssey.ui.notifications.NotificationsScreen
import io.github.nodyssey.ui.notifications.NotificationsUiState
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
 * 通知 in both themes, against boards 5a and 5b: 互动 under its day headings with the unread marks,
 * the same list scrolled under the lifted header, and 私信 with 系统通知 in its own card. See
 * [PostListScreenRenderTest] on the null avatars.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class NotificationsScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun Screen(
        darkTheme: Boolean,
        tab: NotificationTab = NotificationTab.INTERACTIONS,
        base: NotificationsUiState = STATE,
    ) {
        PlazaTheme(darkTheme = darkTheme) {
            NotificationsScreen(
                state = base.copy(selectedTab = tab),
                onSignIn = {},
                onVerify = {},
                onTabChange = {},
                onRetry = {},
                onMarkAllRead = {},
                onNotificationClick = {},
                onConversationClick = {},
                onNewConversation = {},
                onNewConversationQueryChange = {},
                onNewConversationSearch = {},
                onNewConversationDismiss = {},
                onRecipientClick = {},
            )
        }
    }

    @Test
    fun `the interactions tab in light`() {
        composeRule.setContent { Screen(darkTheme = false) }

        composeRule.onRoot().captureRender("notifications-light")
    }

    @Test
    fun `the interactions tab in dark`() {
        composeRule.setContent { Screen(darkTheme = true) }

        composeRule.onRoot().captureRender("notifications-dark")
    }

    /** 5a's own state: the list run up under the header, which lifts and fades the rows into it. */
    @Test
    fun `the interactions tab scrolled, in light`() {
        composeRule.setContent { Screen(darkTheme = false) }
        composeRule.onRoot().performTouchInput { swipeUp(startY = bottom * 0.8f, endY = bottom * 0.3f) }
        composeRule.waitForIdle()

        composeRule.onRoot().captureRender("notifications-scrolled-light")
    }

    @Test
    fun `the interactions tab scrolled, in dark`() {
        composeRule.setContent { Screen(darkTheme = true) }
        composeRule.onRoot().performTouchInput { swipeUp(startY = bottom * 0.8f, endY = bottom * 0.3f) }
        composeRule.waitForIdle()

        composeRule.onRoot().captureRender("notifications-scrolled-dark")
    }

    @Test
    fun `the messages tab in light`() {
        composeRule.setContent { Screen(darkTheme = false, tab = NotificationTab.MESSAGES) }

        composeRule.onRoot().captureRender("messages-light")
    }

    @Test
    fun `the messages tab in dark`() {
        composeRule.setContent { Screen(darkTheme = true, tab = NotificationTab.MESSAGES) }

        composeRule.onRoot().captureRender("messages-dark")
    }

    /** 互动 with nothing in it: the shared empty state, on its card. */
    @Test
    fun `the interactions tab with nothing in it, in light`() {
        composeRule.setContent {
            Screen(darkTheme = false, base = STATE.copy(items = emptyList(), counts = NotificationCounts()))
        }

        composeRule.onRoot().captureRender("notifications-empty-light")
    }

    /** 新会话, which opens in a window of its own — hence the screen capture. */
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun `the new conversation sheet, in light`() {
        composeRule.setContent {
            Screen(
                darkTheme = false,
                tab = NotificationTab.MESSAGES,
                base = STATE.copy(newConversation = NewConversationState(isVisible = true)),
            )
        }
        composeRule.waitForIdle()

        captureScreenRoboImage(filePath = "build/outputs/renders/messages-new-conversation-light.png")
    }

    private companion object {
        /** A fixed instant so "X 分钟前" comes out the same every run. */
        val NOW = TimeFormat.parseTimestamp("2026-07-26 10:22:03")!!

        private fun notification(
            id: String,
            actor: String,
            actorUid: Long,
            threadTitle: String,
            category: NotificationCategory,
            minutesAgo: Long,
            unread: Boolean = true,
        ) = ForumNotification(
            id = id,
            sources = listOf(NotificationSource(category, id.toLong(), isUnread = unread)),
            commentId = id.toLongOrNull(),
            postId = id.toLong(),
            floor = null,
            actorUid = actorUid,
            actorName = actor,
            avatarUrl = null,
            threadTitle = threadTitle,
            createdAtMillis = NOW - minutesAgo * 60_000L,
            createdAtText = null,
        )

        val ITEMS =
            listOf(
                notification("1", "轻舟", 12, "自建 NAS 一年，聊聊我踩过的那些坑和真香时刻", NotificationCategory.REPLIES, 3),
                notification("2", "codemonkey", 56, "四盘位 NAS 选 ZFS 还是 Btrfs？", NotificationCategory.MENTIONS, 40),
                notification("3", "厨房杀手", 78, "有没有那种用了就回不去的小众 App", NotificationCategory.REPLIES, 111),
                notification("4", "省钱达人", 90, "关于最近 VPS 涨价，说说我的续费策略", NotificationCategory.REPLIES, 790, unread = false),
                notification("5", "kvm_fan", 91, "小内存机器跑 Docker 的几个优化", NotificationCategory.MENTIONS, 2 * 1440 + 380, unread = false),
                notification("6", "过过路人", 92, "黑五机场怎么选？把我对比的几家整理成了表格", NotificationCategory.REPLIES, 4 * 1440, unread = false),
                notification("7", "n100_router", 93, "OpenWrt 旁路由的几个坑", NotificationCategory.REPLIES, 6 * 1440, unread = false),
                notification("8", "夜猫子", 94, "凌晨三点的服务器告警，你们怎么处理", NotificationCategory.MENTIONS, 9 * 1440, unread = false),
            )

        private fun conversation(
            uid: Long,
            name: String,
            snippet: String,
            minutesAgo: Long,
            unread: Int = 0,
            mine: Boolean = false,
            system: Boolean = false,
        ) = MessageConversation(
            uid = uid,
            userName = name,
            avatarUrl = null,
            snippet = contentPreview(snippet),
            isSnippetMine = mine,
            updatedAtMillis = NOW - minutesAgo * 60_000L,
            updatedAtText = null,
            unreadCount = unread,
            isSystem = system,
        )

        val CONVERSATIONS =
            listOf(
                conversation(0, MessageConversation.SYSTEM_NAME, "你的帖子「四盘位 NAS 选 ZFS 还是 Btrfs？」已被移动到 技术", 5 * 1440, system = true),
                conversation(1, "轻舟", "那条 CN2 线路晚高峰丢包大概 3%，我截图给你看", 34, unread = 2),
                conversation(2, "codemonkey", "![](https://example.com/a.png)", 70, unread = 1),
                conversation(3, "厨房杀手", "好的，周末我把配置文件发你", 1440, mine = true),
                conversation(4, "省钱达人", "续费前记得先开工单问问有没有老用户价", 7 * 1440, mine = true),
                conversation(5, "kvm_fan", "谢谢，已经解决了", 11 * 1440),
                conversation(6, "过过路人", "那家的退款流程大概三个工作日", 14 * 1440, mine = true),
            )

        val STATE =
            NotificationsUiState(
                isSignedIn = true,
                selectedTab = NotificationTab.INTERACTIONS,
                counts = NotificationCounts(replies = 2, mentions = 1, messages = 1),
                items = ITEMS,
                conversations = CONVERSATIONS,
                nowMillis = NOW,
            )
    }
}
