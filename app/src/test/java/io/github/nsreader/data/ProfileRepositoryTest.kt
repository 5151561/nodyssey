package io.github.nsreader.data

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
                )

            val profile = repository.profile()

            assertEquals(31037L, profile.uid)
            assertEquals("缭雾", profile.name)
            assertEquals(1, profile.rank)
            assertEquals(292, profile.chickenCount)
            assertEquals(2, profile.starCount)
        }

    @Test(expected = NodeSeekException::class)
    fun `rejects a page without signed in profile data`() =
        runTest {
            NetworkProfileRepository(
                htmlSource = FakeProfileHtmlSource("<html><body></body></html>"),
                jsonSource = FakeProfileJsonSource(response = "{}"),
            ).profile()
        }
}

private class FakeProfileHtmlSource(
    private val response: String,
) : HtmlSource {
    override suspend fun getHtml(path: String): String = response
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
