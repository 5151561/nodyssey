package io.github.nsreader.data

import io.github.nsreader.core.net.JsonSource
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.data.local.NodeSeekDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoryRepositoryTest {
    private lateinit var database: NodeSeekDatabase
    private val clock = MutableClock()

    @Before
    fun setUp() {
        database = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private class RecordingJsonSource(
        var body: () -> String,
    ) : JsonSource {
        var calls = 0

        override suspend fun getJson(
            path: String,
            referer: String,
        ): String {
            calls++
            return body()
        }
    }

    private fun repository(client: JsonSource) = CategoryRepository(client, database.boardDao(), clock)

    private fun successBody(vararg keys: String) =
        """
        {"success":true,"data":[${keys.joinToString(",") { """{"key":"$it","cn_text":"$it-cn"}""" }}]}
        """.trimIndent()

    @Test
    fun `the front page leads the list and is never stored`() =
        runTest {
            val client = RecordingJsonSource { successBody("daily", "tech") }
            val repository = repository(client)

            repository.refreshIfNeeded()

            val boards = repository.boards.first()
            assertEquals(CategoryRepository.FRONT_PAGE, boards.first())
            assertEquals(listOf(null, "daily", "tech"), boards.map { it.slug })
            assertEquals(2, database.boardDao().count())
        }

    @Test
    fun `server order is preserved across reads`() =
        runTest {
            val repository = repository(RecordingJsonSource { successBody("zulu", "alpha", "mike") })

            repository.refreshIfNeeded()

            assertEquals(
                listOf(null, "zulu", "alpha", "mike"),
                repository.boards.first().map { it.slug },
            )
        }

    /**
     * The `meaningless` board disappeared upstream once already. Upserting without deleting would let
     * a removed board live on in the tab strip forever.
     */
    @Test
    fun `a board removed upstream disappears locally`() =
        runTest {
            var keys = arrayOf("daily", "tech", "meaningless")
            val client = RecordingJsonSource { successBody(*keys) }
            val repository = repository(client)
            repository.refreshIfNeeded()
            assertEquals(3, database.boardDao().count())

            keys = arrayOf("daily", "tech")
            clock.advanceBy(CategoryRepository.CACHE_TTL_MILLIS + 1)
            repository.refreshIfNeeded()

            assertEquals(listOf(null, "daily", "tech"), repository.boards.first().map { it.slug })
        }

    @Test
    fun `an empty database falls back to the offline board list`() =
        runTest {
            val repository =
                repository(
                    object : JsonSource {
                        override suspend fun getJson(
                            path: String,
                            referer: String,
                        ): String = throw NodeSeekException(NodeSeekError.Network)
                    },
                )

            repository.refreshIfNeeded()

            val boards = repository.boards.first()
            assertTrue("expected a fallback list, got $boards", boards.size > 1)
        }

    /** Losing the network must not empty a tab strip that already worked offline. */
    @Test
    fun `a failed refresh leaves the stored boards alone`() =
        runTest {
            var fail = false
            val client =
                RecordingJsonSource {
                    if (fail) throw NodeSeekException(NodeSeekError.Cloudflare) else successBody("daily", "tech")
                }
            val repository = repository(client)
            repository.refreshIfNeeded()

            fail = true
            clock.advanceBy(CategoryRepository.CACHE_TTL_MILLIS + 1)
            repository.refreshIfNeeded()

            assertEquals(listOf(null, "daily", "tech"), repository.boards.first().map { it.slug })
        }

    @Test
    fun `a fresh list is not re-fetched`() =
        runTest {
            val client = RecordingJsonSource { successBody("daily") }
            val repository = repository(client)

            repository.refreshIfNeeded()
            repository.refreshIfNeeded()
            repository.refreshIfNeeded()

            assertEquals(1, client.calls)
        }

    @Test
    fun `a stale list is re-fetched`() =
        runTest {
            val client = RecordingJsonSource { successBody("daily") }
            val repository = repository(client)
            repository.refreshIfNeeded()

            clock.advanceBy(CategoryRepository.CACHE_TTL_MILLIS + 1)
            repository.refreshIfNeeded()

            assertEquals(2, client.calls)
        }

    @Test
    fun `concurrent callers collapse into one request`() =
        runTest {
            val client = RecordingJsonSource { successBody("daily") }
            val repository = repository(client)

            val first = async { repository.refreshIfNeeded() }
            val second = async { repository.refreshIfNeeded() }
            val third = async { repository.refreshIfNeeded() }
            first.await()
            second.await()
            third.await()

            assertEquals(1, client.calls)
        }

    @Test
    fun `a success false response is treated as a failure`() =
        runTest {
            val repository = repository(RecordingJsonSource { """{"success":false,"data":[]}""" })

            repository.refreshIfNeeded()

            // Falls back rather than showing a tab strip with only the front page on it.
            assertTrue(repository.boards.first().size > 1)
        }
}
