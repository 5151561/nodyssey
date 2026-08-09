package io.github.nodyssey.data

import io.github.nodyssey.core.html.Fixtures
import io.github.nodyssey.core.net.JsonSource
import io.github.plaza.core.AppDispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

private class RecordingJsonSource(private val body: String) : JsonSource {
    var path: String? = null

    override suspend fun getJson(path: String, referer: String): String {
        this.path = path
        return body
    }
}
