package io.github.nodyssey.ui.notifications

import android.webkit.CookieManager
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.JsonApi
import io.github.nodyssey.data.MessageConversation
import io.github.nodyssey.data.MessageRepository
import io.github.nodyssey.data.MessageThread
import io.github.nodyssey.data.NotificationRepository
import io.github.nodyssey.data.SearchRepository
import io.github.nodyssey.data.UserSearchResult
import io.github.nodyssey.data.session.SessionRepository
import io.github.plaza.core.AppClock
import io.github.plaza.core.net.WebViewCookieJar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The badge half of board 7d.
 *
 * Every case here is the same bug seen from a different side: acting on a notification used to move
 * the row and leave the number that pointed at it exactly where it was.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NotificationsViewModelTest {
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
    fun `opening a notification tells the server and drops the badge`() =
        runTest(dispatcher) {
            val api = FakeApi(counts = """{"atMe":2}""")
            val viewModel = viewModel(api)
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.counts.mentions)

            api.counts = """{"atMe":1}"""
            viewModel.markOpened("1")
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.items.single().isUnread)
            assertEquals(listOf("/api/notification/at-me/markViewed"), api.postedPaths)
            assertEquals(listOf("""{"atMe":[1]}"""), api.postedBodies)
            assertEquals(1, viewModel.uiState.value.counts.mentions)
        }

    /** Opening the same row twice must not post it twice, nor take the badge down twice. */
    @Test
    fun `opening a row that is already read posts nothing`() =
        runTest(dispatcher) {
            val api = FakeApi(counts = """{"atMe":2}""")
            val viewModel = viewModel(api, unread = false)
            advanceUntilIdle()

            viewModel.markOpened("1")
            advanceUntilIdle()

            assertEquals(emptyList<String>(), api.postedPaths)
            assertEquals(2, viewModel.uiState.value.counts.mentions)
        }

    /** 全部已读 on @我 used to zero all three badges, 私信 included. */
    @Test
    fun `mark all read only clears the group the button belongs to`() =
        runTest(dispatcher) {
            val api = FakeApi(counts = """{"atMe":2,"message":3}""")
            val viewModel = viewModel(api)
            advanceUntilIdle()

            api.counts = """{"atMe":0,"message":3}"""
            viewModel.markAllRead()
            advanceUntilIdle()

            assertEquals(0, viewModel.uiState.value.counts.mentions)
            assertEquals(3, viewModel.uiState.value.counts.messages)
        }

    /**
     * Every return to the screen calls this, so the throttle carries the difference between "timely"
     * and "a request per tab tap": straight back is served from what is showing, a stale return
     * re-reads the server.
     */
    @Test
    fun `coming back into view refreshes only once the last load is stale`() =
        runTest(dispatcher) {
            var now = 1_785_000_000_000L
            val api = FakeApi(counts = """{"atMe":2}""")
            val viewModel = viewModel(api, clock = { now })
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.counts.mentions)

            api.counts = """{"atMe":5}"""
            viewModel.refreshIfStale()
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.counts.mentions)

            now += 30_000L
            viewModel.refreshIfStale()
            advanceUntilIdle()
            assertEquals(5, viewModel.uiState.value.counts.mentions)
        }

    private fun viewModel(
        api: FakeApi,
        unread: Boolean = true,
        clock: AppClock = AppClock { 1_785_000_000_000L },
    ): NotificationsViewModel {
        val notifications = NotificationRepository(api)
        api.mentionUnread = unread
        return NotificationsViewModel(
            repository = notifications,
            messages = NoMessages,
            search = NoSearch,
            session = SessionRepository(WebViewCookieJar(NodeSeekSite.CONFIG, cookieManager)),
            clock = clock,
        )
    }
}

private object NoSearch : SearchRepository {
    override suspend fun searchUsers(query: String) = emptyList<UserSearchResult>()
}

private object NoMessages : MessageRepository {
    override suspend fun conversations() = emptyList<MessageConversation>()

    override suspend fun thread(uid: Long) =
        MessageThread(uid = uid, userName = "", avatarUrl = null, level = null, messages = emptyList())

    override suspend fun send(
        uid: Long,
        content: String,
        markdown: Boolean,
    ) = null

    override suspend fun markRead(messageIds: List<Long>) = Unit

    override suspend fun markAllRead() = Unit
}

private class FakeApi(
    var counts: String,
) : JsonApi {
    var mentionUnread = true
    val postedPaths = mutableListOf<String>()
    val postedBodies = mutableListOf<String>()

    override suspend fun getJson(path: String, referer: String): String =
        when {
            path.startsWith("/api/notification/unread-count") -> counts

            else ->
                """{"atList":[{"id":1,"member_id":12,"username":"nssk","post_title":"求教如何改用户名",
                   "viewed":${if (mentionUnread) 0 else 1}}]}"""
        }

    override suspend fun postJson(path: String, body: String, referer: String): String {
        postedPaths += path
        postedBodies += body
        return """{"success":true}"""
    }
}
