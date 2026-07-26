package io.github.nsreader.ui.messages

import android.webkit.CookieManager
import io.github.nsreader.core.AppClock
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.core.net.WebViewCookieJar
import io.github.nsreader.data.DirectMessage
import io.github.nsreader.data.MessageRepository
import io.github.nsreader.data.MessageThread
import io.github.nsreader.data.session.SessionRepository
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
    private val cookieManager = CookieManager.getInstance()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        cookieManager.removeAllCookies(null)
        cookieManager.setCookie(NodeSeekSite.BASE_URL, "session=test")
    }

    @After
    fun tearDown() {
        cookieManager.removeAllCookies(null)
        Dispatchers.resetMain()
    }

    @Test
    fun `a sent message appears immediately and settles once accepted`() =
        runTest(dispatcher) {
            val repository = FakeMessageRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.updateDraft("行，我发个投票试试")
            viewModel.send()

            // The bubble is on screen before the network answers, and the field is already clear.
            assertEquals(SendStatus.SENDING, viewModel.uiState.value.messages.last().status)
            assertEquals("", viewModel.uiState.value.draft)

            advanceUntilIdle()
            assertEquals(SendStatus.SENT, viewModel.uiState.value.messages.last().status)
        }

    @Test
    fun `a failed send keeps the text and can be retried`() =
        runTest(dispatcher) {
            val repository = FakeMessageRepository(sendError = NodeSeekException(NodeSeekError.Network))
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.updateDraft("顺便问下星辰能转账吗")
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
            val repository = FakeMessageRepository(sendError = NodeSeekException(NodeSeekError.Network))
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.updateDraft("在吗")
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
            viewModel.updateDraft("**不要加粗**")
            viewModel.send()
            advanceUntilIdle()

            assertEquals(false, repository.lastMarkdown)
        }

    private fun viewModel(repository: MessageRepository) =
        MessageThreadViewModel(
            repository = repository,
            session = SessionRepository(WebViewCookieJar(cookieManager)),
            clock = AppClock { NOW },
            uid = 4471,
            userName = "iwil",
        )

    private companion object {
        const val NOW = 1_785_000_000_000L
    }
}

private class FakeMessageRepository(
    var sendError: Throwable? = null,
) : MessageRepository {
    var sendCount = 0
    var lastMarkdown: Boolean? = null

    override suspend fun conversations() = emptyList<io.github.nsreader.data.MessageConversation>()

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
                    sentAtMillis = 1_784_999_000_000L,
                    sentAtText = null,
                    isEdited = false,
                ),
            ),
        )

    override suspend fun send(
        uid: Long,
        content: String,
        markdown: Boolean,
    ): DirectMessage? {
        sendCount++
        lastMarkdown = markdown
        sendError?.let { throw it }
        return DirectMessage(
            id = "sent-$sendCount",
            isMine = true,
            content = content,
            sentAtMillis = 1_785_000_000_000L,
            sentAtText = null,
            isEdited = false,
        )
    }
}
