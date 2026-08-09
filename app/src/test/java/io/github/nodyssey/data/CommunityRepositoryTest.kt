package io.github.nodyssey.data

import io.github.nodyssey.core.html.Fixtures
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.HtmlSource
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CommunityRepositoryTest {
    @Test
    fun `member count comes from one homepage request`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val htmlSource = RecordingCommunityHtmlSource(Fixtures.load("page-1.html"))
            val repository =
                NetworkCommunityRepository(
                    htmlSource = htmlSource,
                    dispatchers = AppDispatchers(dispatcher, dispatcher),
                )

            assertEquals(55_232L, repository.memberCount())
            assertEquals(listOf("/"), htmlSource.paths)
        }
}

private class RecordingCommunityHtmlSource(
    private val html: String,
) : HtmlSource {
    val paths = mutableListOf<String>()

    override suspend fun getHtml(path: String): String {
        paths += path
        return html
    }
}
