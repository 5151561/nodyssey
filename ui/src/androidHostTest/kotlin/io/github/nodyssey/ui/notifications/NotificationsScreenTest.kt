package io.github.nodyssey.ui.notifications

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import io.github.nodyssey.data.ForumNotification
import io.github.nodyssey.data.MessageConversation
import io.github.nodyssey.data.NotificationCategory
import io.github.nodyssey.data.NotificationCounts
import io.github.nodyssey.data.NotificationSource
import io.github.nodyssey.data.NotificationTab
import io.github.nodyssey.data.PreviewPart
import io.github.nodyssey.data.PreviewPlaceholder
import io.github.nodyssey.data.contentPreview
import io.github.plaza.designsys.theme.PlazaTheme
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

    /**
     * Board 7d's groups, as the App shows them: 回复 and @我 folded into one chip, 私信 beside it.
     *
     * Still no 「系统」 — the site has never had one, and merging two groups is not an excuse to
     * invent a third.
     */
    @Test
    fun `offers the merged notification chip and 私信`() {
        setContent(state(items = listOf(mention())))

        composeRule.onNodeWithText("回复与@我").assertIsDisplayed()
        composeRule.onNodeWithText("私信").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("回复主题").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("系统").fetchSemanticsNodes().size)
    }

    /** The badge over the merged chip counts rows, not the two groups' rows added up. */
    @Test
    fun `the merged chip discounts what both groups counted twice`() {
        setContent(
            state(items = listOf(mention()), counts = NotificationCounts(replies = 5, mentions = 2, overlap = 2)),
        )

        composeRule.onNodeWithText("5").assertIsDisplayed()
    }

    /** The point of the row: what was written, without opening the thread. */
    @Test
    fun `shows the comment itself, with a placeholder for the picture`() {
        setContent(state(items = listOf(mention(preview = preview()))))

        composeRule.onNodeWithText("还没有这个功能 [图片]").assertIsDisplayed()
    }

    /** A comment that replied *and* @-ed is one row, and says so. */
    @Test
    fun `a folded row says both things happened`() {
        setContent(state(items = listOf(mention(sources = bothGroups()))))

        composeRule.onNodeWithText("nssk 在帖子 求教如何改用户名 中回复并@了我").assertIsDisplayed()
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
            PlazaTheme {
                NotificationsScreen(
                    state =
                    state(
                        tab = NotificationTab.MESSAGES,
                        conversations = listOf(systemConversation()),
                    ),
                    onSignIn = {},
                    onVerify = {},
                    onTabChange = {},
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

    /**
     * The stamps form a column, so every row's must end at the same x. They drifted when the name
     * and a spacer both carried weight and split the slack between them.
     */
    @Test
    fun `conversation stamps share one right edge`() {
        setContent(
            state(
                tab = NotificationTab.MESSAGES,
                conversations =
                listOf(
                    systemConversation(),
                    conversation(uid = 2, name = "a", stamp = NOW - 26 * 60 * 60_000L),
                    conversation(uid = 3, name = "一个很长的用户名字", stamp = NOW - 40L * 24 * 60 * 60_000L),
                ),
            ),
        )

        // The row is clickable, so its semantics are merged: asking the merged tree for a stamp
        // hands back the whole row, whose width is the screen's and always matches.
        val rightEdges =
            listOf("09:12", "昨天", "6月16日")
                .map { composeRule.onNodeWithText(it, useUnmergedTree = true).fetchSemanticsNode() }
                .map { it.positionInRoot.x + it.size.width }

        rightEdges.forEach { assertEquals(rightEdges.first(), it, 1f) }
    }

    /** Board 7e: the pinned system conversation shows its Markdown as text, never as syntax. */
    @Test
    fun `system conversation snippet drops its markdown syntax`() {
        setContent(
            state(
                tab = NotificationTab.MESSAGES,
                conversations = listOf(systemConversation()),
            ),
        )

        composeRule.onNodeWithText("系统通知").assertIsDisplayed()
        composeRule.onNodeWithText("您的评论被用户iwil投喂鸡腿").assertIsDisplayed()
    }

    /** A picture in a private message is named, for the same reason it is in a notification row. */
    @Test
    fun `a conversation whose last message is a picture says so`() {
        setContent(
            state(
                tab = NotificationTab.MESSAGES,
                conversations =
                listOf(
                    conversation(uid = 7, name = "老哥", stamp = NOW)
                        .copy(snippet = contentPreview("![](https://img.example/1.png) 看这个")),
                ),
            ),
        )

        composeRule.onNodeWithText("[图片] 看这个").assertIsDisplayed()
    }

    /**
     * Tapping 通知 while already on 通知 is the same "back to the top" the 首页 tab answers, and the
     * screen hears about it as a counter rather than a call — see the note on it in `Navigation`.
     */
    @Test
    fun `re-tapping the tab brings the list back to the top`() {
        var requests by mutableIntStateOf(0)
        val items = List(40) { mention(id = "$it", threadTitle = "第${it}帖") }
        composeRule.setContent {
            PlazaTheme {
                NotificationsScreen(
                    state = state(items = items),
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
                    scrollToTopRequests = requests,
                )
            }
        }
        // The last of the two lazy lists on screen: the group chips are the other one.
        composeRule.onAllNodes(hasScrollToIndexAction()).onLast().performScrollToIndex(35)
        composeRule.onNodeWithText(sentence("第0帖")).assertIsNotDisplayed()

        composeRule.runOnIdle { requests++ }

        composeRule.onNodeWithText(sentence("第0帖")).assertIsDisplayed()
    }

    private fun sentence(threadTitle: String) = "nssk 在帖子 $threadTitle 中@了我"

    private fun setContent(state: NotificationsUiState) {
        composeRule.setContent {
            PlazaTheme {
                NotificationsScreen(
                    state = state,
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
    }

    private fun state(
        tab: NotificationTab = NotificationTab.INTERACTIONS,
        items: List<ForumNotification> = emptyList(),
        conversations: List<MessageConversation> = emptyList(),
        counts: NotificationCounts = NotificationCounts(replies = 5, mentions = 2, messages = 3),
    ) = NotificationsUiState(
        isSignedIn = true,
        selectedTab = tab,
        counts = counts,
        items = items,
        conversations = conversations,
        nowMillis = NOW,
    )

    private fun mention(
        id: String = "1",
        threadTitle: String = "求教如何改用户名",
        preview: List<PreviewPart> = emptyList(),
        sources: List<NotificationSource> = listOf(mentionSource()),
    ) = ForumNotification(
        id = id,
        sources = sources,
        commentId = id.toLongOrNull(),
        postId = 1,
        floor = null,
        actorUid = 12,
        actorName = "nssk",
        avatarUrl = null,
        preview = preview,
        threadTitle = threadTitle,
        createdAtMillis = NOW - 26 * 60_000L,
        createdAtText = null,
    )

    private fun mentionSource() = NotificationSource(NotificationCategory.MENTIONS, 1L, isUnread = true)

    private fun bothGroups() =
        listOf(
            NotificationSource(NotificationCategory.REPLIES, 7L, isUnread = true),
            mentionSource(),
        )

    private fun preview() =
        listOf(
            PreviewPart.Text("还没有这个功能 "),
            PreviewPart.Placeholder(PreviewPlaceholder.IMAGE),
        )

    private fun conversation(
        uid: Long,
        name: String,
        stamp: Long,
    ) = MessageConversation(
        uid = uid,
        userName = name,
        avatarUrl = null,
        snippet = contentPreview("摘要"),
        isSnippetMine = false,
        updatedAtMillis = stamp,
        updatedAtText = null,
        unreadCount = 0,
        isSystem = false,
    )

    private fun systemConversation() =
        MessageConversation(
            uid = 1,
            userName = MessageConversation.SYSTEM_NAME,
            avatarUrl = null,
            snippet = contentPreview("您的[评论](/post-1-1)被用户[iwil](/space/4471)投喂鸡腿"),
            isSnippetMine = false,
            updatedAtMillis = NOW - 70 * 60_000L,
            updatedAtText = null,
            unreadCount = 1,
            isSystem = true,
        )

    private companion object {
        /** 2026-07-26 10:22:03 in the JVM default zone the test runs in. */
        val NOW = io.github.plaza.core.TimeFormat.parseTimestamp("2026-07-26 10:22:03")!!
    }
}
