package io.github.nodyssey.data

import io.github.nodyssey.core.html.Fixtures
import io.github.nodyssey.core.net.JsonSource
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.HtmlSource
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchRepositoryTest {
    @Test
    fun `user search uses account endpoint and maps all real fields`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val json = RecordingJsonSource(Fixtures.load("user-search-results.json"))
            val repository =
                NetworkSearchRepository(
                    jsonSource = json,
                    htmlSource = RecordingHtmlSource(),
                    dispatchers = AppDispatchers(dispatcher, dispatcher),
                )

            val user = repository.searchUsers("花田").single()

            assertEquals("/api/account/find/%E8%8A%B1%E7%94%B0", json.path)
            assertEquals(63289L, user.uid)
            assertEquals("花田错不错", user.name)
            assertEquals("Android 用户", user.bio)
            assertEquals(1, user.level)
            assertEquals(4, user.topicCount)
            assertEquals(87, user.commentCount)
            assertEquals("22days ago", user.joinedText)
        }

    /**
     * A mention's uid comes from following the site's own `/member?t=` redirect, not from the
     * substring-search endpoint above — that search is capped to a page of results, so a short or
     * common name (`xy`) can rank outside the page even though the site resolves it without doubt.
     */
    @Test
    fun `mention resolution follows the site's own member redirect`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val html = RecordingHtmlSource(redirectsTo = "https://www.nodeseek.com/space/8052#/general")
            val repository =
                NetworkSearchRepository(
                    jsonSource = RecordingJsonSource(""),
                    htmlSource = html,
                    dispatchers = AppDispatchers(dispatcher, dispatcher),
                )

            assertEquals(8052L, repository.resolveMemberUid("xy"))
            assertEquals("/member?t=xy", html.requestedPath)
        }

    @Test
    fun `mention resolution fails when the member link does not redirect`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository =
                NetworkSearchRepository(
                    jsonSource = RecordingJsonSource(""),
                    htmlSource = RecordingHtmlSource(redirectsTo = null),
                    dispatchers = AppDispatchers(dispatcher, dispatcher),
                )

            assertNull(repository.resolveMemberUid("nobody"))
        }
}

private class RecordingJsonSource(private val body: String) : JsonSource {
    var path: String? = null

    override suspend fun getJson(path: String, referer: String): String {
        this.path = path
        return body
    }
}

private class RecordingHtmlSource(
    private val redirectsTo: String? = null,
) : HtmlSource {
    var requestedPath: String? = null
        private set

    override suspend fun getHtml(path: String): String = ""

    override suspend fun resolveRedirect(path: String): String? {
        requestedPath = path
        return redirectsTo
    }
}
