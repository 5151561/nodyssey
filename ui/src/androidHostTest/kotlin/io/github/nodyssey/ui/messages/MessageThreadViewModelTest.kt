package io.github.nodyssey.ui.messages

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.JsonApi
import io.github.nodyssey.data.DirectMessage
import io.github.nodyssey.data.MessageRepository
import io.github.nodyssey.data.MessageThread
import io.github.nodyssey.data.NotificationRepository
import io.github.nodyssey.data.composer.ImageUploader
import io.github.nodyssey.data.session.FakeSessionCookieStore
import io.github.nodyssey.data.session.SessionRepository
import io.github.plaza.core.AppClock
import io.github.plaza.core.net.SessionCookies
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MessageThreadViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val cookies = FakeSessionCookieStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        cookies.setCookie(NodeSeekSite.BASE_URL, "session=test")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a sent message appears immediately and settles once accepted`() =
        runTest(dispatcher) {
            val repository = FakeMessageRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.draftState.setTextAndPlaceCursorAtEnd("行，我发个投票试试")
            viewModel.send()

            // The bubble is on screen before the network answers, and the field is already clear.
            assertEquals(SendStatus.SENDING, viewModel.uiState.value.messages.last().status)
            assertEquals("", viewModel.draftState.text.toString())

            advanceUntilIdle()
            assertEquals(SendStatus.SENT, viewModel.uiState.value.messages.last().status)
        }

    @Test
    fun `a failed send keeps the text and can be retried`() =
        runTest(dispatcher) {
            val repository = FakeMessageRepository(sendError = SiteException(SiteError.Network))
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.draftState.setTextAndPlaceCursorAtEnd("顺便问下星辰能转账吗")
            viewModel.send()
            advanceUntilIdle()

            val failed = viewModel.uiState.value.messages.last()
            assertEquals(SendStatus.FAILED, failed.status)
            assertEquals("顺便问下星辰能转账吗", failed.content)

            repository.sendError = null
            viewModel.retry(failed.id)
            advanceUntilIdle()

            assertEquals(SendStatus.SENT, viewModel.uiState.value.messages.last().status)
            assertEquals(2, repository.sendCount)
        }

    /** A refresh mid-flight must not drop the bubble the user is still waiting on. */
    @Test
    fun `reloading the thread keeps a message that has not been delivered`() =
        runTest(dispatcher) {
            val repository = FakeMessageRepository(sendError = SiteException(SiteError.Network))
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.draftState.setTextAndPlaceCursorAtEnd("在吗")
            viewModel.send()
            advanceUntilIdle()

            viewModel.refresh()
            advanceUntilIdle()

            val messages = viewModel.uiState.value.messages
            assertEquals(2, messages.size)
            assertTrue(messages.any { it.status == SendStatus.FAILED && it.content == "在吗" })
        }

    @Test
    fun `the markdown switch travels with the send`() =
        runTest(dispatcher) {
            val repository = FakeMessageRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.toggleMarkdown()
            viewModel.draftState.setTextAndPlaceCursorAtEnd("**不要加粗**")
            viewModel.send()
            advanceUntilIdle()

            assertEquals(false, repository.lastMarkdown)
        }

    /**
     * Bug: fetching a conversation is not what clears it — the ids have to go back — so the 私信
     * badge survived reading the message that put it there.
     */
    @Test
    fun `opening a conversation clears its unread messages and the badge`() =
        runTest(dispatcher) {
            val repository = FakeMessageRepository(unreadIds = listOf(11L, 12L))
            val counts = FakeCountsApi(unread = """{"message":0}""")
            val notifications = NotificationRepository(counts)
            notifications.refreshCounts()
            advanceUntilIdle()

            viewModel(repository, notifications)
            advanceUntilIdle()

            assertEquals(listOf(11L, 12L), repository.markedRead)
            assertEquals(0, notifications.counts.value.messages)
        }

    /**
     * 引用. The message waits over the bar as a card (3e) and goes out as a blockquote ahead of the
     * reply — which is only Markdown if the switch is on, so quoting turns it on.
     */
    @Test
    fun `引用 sends the message as a blockquote ahead of the reply`() =
        runTest(dispatcher) {
            val repository = FakeMessageRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()
            viewModel.toggleMarkdown()

            viewModel.quote(viewModel.uiState.value.messages.single())
            // The draft is left alone: the quotation is the card's, not the field's.
            assertEquals("", viewModel.draftState.text.toString())
            assertTrue(viewModel.uiState.value.isMarkdown)

            viewModel.draftState.setTextAndPlaceCursorAtEnd("问到了吗")
            viewModel.send()
            advanceUntilIdle()

            assertEquals("> 改名的事我问过管理\n\n问到了吗", repository.lastContent)
            assertTrue(viewModel.uiState.value.quotes.isEmpty())
        }

    /** Every line takes a `>`, or a blank line inside the message would end the quotation early. */
    @Test
    fun `a multi-line message is quoted line by line`() =
        runTest(dispatcher) {
            val repository = FakeMessageRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.quote(bubble("第一行\n\n第三行"))
            viewModel.draftState.setTextAndPlaceCursorAtEnd("好")
            viewModel.send()
            advanceUntilIdle()

            assertEquals("> 第一行\n> \n> 第三行\n\n好", repository.lastContent)
        }

    /** Cumulative, like the reply editor's: quoting a second message must not eat the first. */
    @Test
    fun `每引用一条就多一段，互不覆盖`() =
        runTest(dispatcher) {
            val repository = FakeMessageRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.quote(bubble("在的", id = "a"))
            viewModel.quote(bubble("那明天见", id = "b"))
            viewModel.draftState.setTextAndPlaceCursorAtEnd("看到了")
            viewModel.send()
            advanceUntilIdle()

            assertEquals("> 在的\n\n> 那明天见\n\n看到了", repository.lastContent)
        }

    @Test
    fun `the ✕ on a quote card drops only that quotation`() =
        runTest(dispatcher) {
            val viewModel = viewModel(FakeMessageRepository())
            advanceUntilIdle()

            viewModel.quote(bubble("在的", id = "a"))
            viewModel.quote(bubble("那明天见", id = "b"))
            viewModel.removeQuote("a")

            assertEquals(listOf("b"), viewModel.uiState.value.quotes.map { it.id })
        }

    private fun bubble(
        content: String,
        id: String = "b",
    ) = MessageBubble(
        id = id,
        isMine = false,
        content = content,
        isMarkdown = true,
        sentAtMillis = NOW,
        sentAtText = null,
        status = SendStatus.SENT,
    )

    private fun viewModel(
        repository: MessageRepository,
        notifications: NotificationRepository = NotificationRepository(FakeCountsApi()),
    ) = MessageThreadViewModel(
        repository = repository,
        notifications = notifications,
        session = SessionRepository(SessionCookies(NodeSeekSite.CONFIG, cookies)),
        clock = AppClock { NOW },
        uploader = ImageUploader { _, _ -> "https://cdn.nodeimage.com/i/x.webp" },
        uid = 4471,
        userName = "iwil",
    )

    private companion object {
        const val NOW = 1_785_000_000_000L
    }
}

private class FakeCountsApi(
    private val unread: String = """{"message":0}""",
) : JsonApi {
    override suspend fun getJson(path: String, referer: String): String = unread

    override suspend fun postJson(path: String, body: String, referer: String): String =
        """{"success":true}"""
}

private class FakeMessageRepository(
    var sendError: Throwable? = null,
    private val unreadIds: List<Long> = emptyList(),
) : MessageRepository {
    var sendCount = 0
    var lastMarkdown: Boolean? = null
    var lastContent: String? = null
    val markedRead = mutableListOf<Long>()

    override suspend fun conversations() = emptyList<io.github.nodyssey.data.MessageConversation>()

    override suspend fun markAllRead() = Unit

    override suspend fun markRead(messageIds: List<Long>) {
        markedRead += messageIds
    }

    override suspend fun thread(uid: Long) =
        MessageThread(
            uid = uid,
            userName = "iwil",
            avatarUrl = null,
            level = 4,
            messages =
            listOf(
                DirectMessage(
                    id = "1",
                    isMine = false,
                    content = "改名的事我问过管理",
                    isMarkdown = true,
                    sentAtMillis = 1_784_999_000_000L,
                    sentAtText = null,
                ),
            ),
            unreadIds = unreadIds,
        )

    override suspend fun send(
        uid: Long,
        content: String,
        markdown: Boolean,
    ): DirectMessage? {
        sendCount++
        lastMarkdown = markdown
        lastContent = content
        sendError?.let { throw it }
        return DirectMessage(
            id = "sent-$sendCount",
            isMine = true,
            content = content,
            isMarkdown = markdown,
            sentAtMillis = 1_785_000_000_000L,
            sentAtText = null,
        )
    }
}
