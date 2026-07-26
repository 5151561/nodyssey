package io.github.nsreader.data

import io.github.nsreader.core.AppClock
import io.github.nsreader.core.html.Fixtures
import io.github.nsreader.core.net.HtmlSource
import io.github.nsreader.core.net.JsonSource
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.core.net.NodeSeekJsonClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileRepositoryTest {
    @Test
    fun `loads the current user and refreshes values from the account endpoint`() =
        runTest {
            val jsonSource =
                FakeProfileJsonSource(
                    response =
                    """
                    {"data":{"member_id":31037,"member_name":"缭雾","rank":2,
                    "coin":305,"stardust":7,"created_at":"2025-04-27T14:29:22.000Z"}}
                    """.trimIndent(),
                )
            val repository =
                NetworkProfileRepository(
                    htmlSource = FakeProfileHtmlSource(Fixtures.load("page-1.html")),
                    jsonSource = jsonSource,
                    clock = AppClock { 0L },
                )

            val profile = repository.profile()

            assertEquals(31037L, profile.uid)
            assertEquals("缭雾", profile.name)
            assertEquals(2, profile.rank)
            assertEquals(305, profile.chickenCount)
            assertEquals(7, profile.starCount)
            assertEquals("https://www.nodeseek.com/avatar/31037.png", profile.avatarUrl)
            assertEquals(NodeSeekJsonClient.accountInfoPath(31037), jsonSource.requestedPath)
        }

    @Test
    fun `keeps bootstrap values when the account endpoint is temporarily unavailable`() =
        runTest {
            val repository =
                NetworkProfileRepository(
                    htmlSource = FakeProfileHtmlSource(Fixtures.load("page-1.html")),
                    jsonSource =
                    FakeProfileJsonSource(
                        error = NodeSeekException(NodeSeekError.Http(500)),
                    ),
                    clock = AppClock { 0L },
                )

            val profile = repository.profile()

            assertEquals(31037L, profile.uid)
            assertEquals("缭雾", profile.name)
            assertEquals(1, profile.rank)
            assertEquals(292, profile.chickenCount)
            assertEquals(2, profile.starCount)
        }

    /** The profile-area screens each ask on entry; a minute-long cache keeps that to one fetch. */
    @Test
    fun `serves repeat profile calls from the cache until refreshed`() =
        runTest {
            val htmlSource = FakeProfileHtmlSource(Fixtures.load("page-1.html"))
            val repository =
                NetworkProfileRepository(
                    htmlSource = htmlSource,
                    jsonSource = FakeProfileJsonSource(error = NodeSeekException(NodeSeekError.Http(500))),
                    clock = AppClock { 0L },
                )

            repository.profile()
            repository.profile()
            assertEquals(1, htmlSource.callCount)

            repository.profile(refresh = true)
            assertEquals(2, htmlSource.callCount)
        }

    @Test(expected = NodeSeekException::class)
    fun `rejects a page without signed in profile data`() =
        runTest {
            NetworkProfileRepository(
                htmlSource = FakeProfileHtmlSource("<html><body></body></html>"),
                jsonSource = FakeProfileJsonSource(response = "{}"),
                clock = AppClock { 0L },
            ).profile()
        }
}

private class FakeProfileHtmlSource(
    private val response: String,
) : HtmlSource {
    var callCount = 0
        private set

    override suspend fun getHtml(path: String): String {
        callCount++
        return response
    }
}

private class FakeProfileJsonSource(
    private val response: String? = null,
    private val error: Throwable? = null,
) : JsonSource {
    var requestedPath: String? = null
        private set

    override suspend fun getJson(path: String, referer: String): String {
        requestedPath = path
        error?.let { throw it }
        return requireNotNull(response)
    }
}
