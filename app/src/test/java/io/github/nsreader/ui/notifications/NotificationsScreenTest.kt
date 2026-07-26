package io.github.nsreader.ui.notifications

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.nsreader.data.ForumNotification
import io.github.nsreader.data.MessageConversation
import io.github.nsreader.data.NotificationCategory
import io.github.nsreader.data.NotificationCounts
import io.github.nsreader.ui.theme.NodeSeekTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class NotificationsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** Board 7d: three groups, and no 「系统」 — the site has never had one. */
    @Test
    fun `offers exactly the three groups the site has`() {
        setContent(state(items = listOf(mention())))

        composeRule.onNodeWithText("@我").assertIsDisplayed()
        composeRule.onNodeWithText("回复主题").assertIsDisplayed()
        composeRule.onNodeWithText("私信").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("系统").fetchSemanticsNodes().size)
    }

    @Test
    fun `renders the mention sentence and both halves of the timestamp`() {
        setContent(state(items = listOf(mention())))

        composeRule.onNodeWithText("nssk 在帖子 求教如何改用户名 中@了我").assertIsDisplayed()
        composeRule.onNodeWithText("26 分钟前 · 2026/7/26 09:56:03", substring = true).assertIsDisplayed()
    }

    @Test
    fun `an unread conversation counts towards mark all read`() {
        var markedAllRead = false
        composeRule.setContent {
            NodeSeekTheme {
                NotificationsScreen(
                    state =
                    state(
                        category = NotificationCategory.MESSAGES,
                        conversations = listOf(systemConversation()),
                    ),
                    onSignIn = {},
                    onVerify = {},
                    onCategoryChange = {},
                    onRetry = {},
                    onMarkAllRead = { markedAllRead = true },
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

        composeRule.onNodeWithText("全部已读").performClick()

        assertEquals(true, markedAllRead)
    }

    /** Board 7e: the pinned system conversation shows its Markdown as text, never as syntax. */
    @Test
    fun `system conversation snippet drops its markdown syntax`() {
        setContent(
            state(
                category = NotificationCategory.MESSAGES,
                conversations = listOf(systemConversation()),
            ),
        )

        composeRule.onNodeWithText("系统通知").assertIsDisplayed()
        composeRule.onNodeWithText("您的评论被用户iwil投喂鸡腿").assertIsDisplayed()
    }

    private fun setContent(state: NotificationsUiState) {
        composeRule.setContent {
            NodeSeekTheme {
                NotificationsScreen(
                    state = state,
                    onSignIn = {},
                    onVerify = {},
                    onCategoryChange = {},
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
    }

    private fun state(
        category: NotificationCategory = NotificationCategory.MENTIONS,
        items: List<ForumNotification> = emptyList(),
        conversations: List<MessageConversation> = emptyList(),
    ) = NotificationsUiState(
        isSignedIn = true,
        selectedCategory = category,
        counts = NotificationCounts(replies = 5, mentions = 2, messages = 3),
        items = items,
        conversations = conversations,
        nowMillis = NOW,
    )

    private fun mention() =
        ForumNotification(
            id = "1",
            category = NotificationCategory.MENTIONS,
            postId = 1,
            floor = null,
            actorUid = 12,
            actorName = "nssk",
            avatarUrl = null,
            excerpt = null,
            threadTitle = "求教如何改用户名",
            createdAtMillis = NOW - 26 * 60_000L,
            createdAtText = null,
            isUnread = true,
        )

    private fun systemConversation() =
        MessageConversation(
            uid = 1,
            userName = MessageConversation.SYSTEM_NAME,
            avatarUrl = null,
            snippet = "您的[评论](/post-1-1)被用户[iwil](/space/4471)投喂鸡腿",
            isSnippetMine = false,
            updatedAtMillis = NOW - 70 * 60_000L,
            updatedAtText = null,
            unreadCount = 1,
            isSystem = true,
        )

    private companion object {
        /** 2026-07-26 10:22:03 in the JVM default zone the test runs in. */
        val NOW = io.github.nsreader.core.TimeFormat.parseTimestamp("2026-07-26 10:22:03")!!
    }
}
