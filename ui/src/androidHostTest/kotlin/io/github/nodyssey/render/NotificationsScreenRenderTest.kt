package io.github.nodyssey.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.nodyssey.data.ForumNotification
import io.github.nodyssey.data.NotificationCategory
import io.github.nodyssey.data.NotificationCounts
import io.github.nodyssey.data.NotificationSource
import io.github.nodyssey.data.NotificationTab
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
 * 消息 › 互动 in both themes — @提及 and 回复 with their unread marks. See [PostListScreenRenderTest]
 * on the null avatars.
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
    private fun Screen(darkTheme: Boolean) {
        PlazaTheme(darkTheme = darkTheme) {
            NotificationsScreen(
                state = STATE,
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
                notification("2", "过路人", 34, "有没有那种用了就回不去的小众 App，求推荐", NotificationCategory.MENTIONS, 26),
                notification("3", "codemonkey", 56, "第一次跑长文本翻译，本地模型和 API 的取舍", NotificationCategory.REPLIES, 58),
                notification("4", "厨房杀手", 78, "深夜放毒：分享一个我常做的十分钟快手菜", NotificationCategory.REPLIES, 92, unread = false),
                notification("5", "省钱达人", 90, "关于最近 VPS 涨价，说说我的续费策略", NotificationCategory.MENTIONS, 140, unread = false),
            )

        val STATE =
            NotificationsUiState(
                isSignedIn = true,
                selectedTab = NotificationTab.INTERACTIONS,
                counts = NotificationCounts(replies = 3, mentions = 2, messages = 1),
                items = ITEMS,
                conversations = emptyList(),
                nowMillis = NOW,
            )
    }
}
