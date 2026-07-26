package io.github.nsreader.data

import io.github.nsreader.core.AppDispatchers
import io.github.nsreader.core.html.Fixtures
import io.github.nsreader.core.net.HtmlSource
import io.github.nsreader.core.net.JsonSource
import io.github.nsreader.model.SearchSort
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchRepositoryTest {
    @Test
    fun `post search loads every server result page`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val html = RecordingHtmlSource(Fixtures.load("search-results.html"))
            val repository =
                NetworkSearchRepository(
                    htmlSource = html,
                    jsonSource = RecordingJsonSource("{}"),
                    dispatchers = AppDispatchers(dispatcher, dispatcher),
                )

            val posts = repository.searchPosts("Android TV", emptySet(), SearchSort.TIME)

            assertEquals(1, posts.size)
            assertEquals(
                setOf(
                    "/search?q=Android%20TV&sortBy=postTime",
                    "/search?q=Android%20TV&page=2&sortBy=postTime",
                    "/search?q=Android%20TV&page=3&sortBy=postTime",
                ),
                html.paths.toSet(),
            )
        }

    @Test
    fun `user search uses account endpoint and maps all real fields`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val json = RecordingJsonSource(Fixtures.load("user-search-results.json"))
            val repository =
                NetworkSearchRepository(
                    htmlSource = FailingHtmlSource,
                    jsonSource = json,
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
}

private object FailingHtmlSource : HtmlSource {
    override suspend fun getHtml(path: String): String = error("HTML must not be used for user search")
}

private class RecordingHtmlSource(private val body: String) : HtmlSource {
    val paths = mutableListOf<String>()

    override suspend fun getHtml(path: String): String {
        paths += path
        return body
    }
}

private class RecordingJsonSource(private val body: String) : JsonSource {
    var path: String? = null

    override suspend fun getJson(path: String, referer: String): String {
        this.path = path
        return body
    }
}
