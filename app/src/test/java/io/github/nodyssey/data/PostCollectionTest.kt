package io.github.nodyssey.data

import io.github.nodyssey.core.net.JsonApi
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Collecting a thread, from the request through to what the screen ends up reading.
 *
 * The star renders from Room like everything else on the detail screen, so a toggle the site
 * accepted but that never reached the database would leave the reader looking at the state they had
 * just changed away from.
 */
@RunWith(RobolectricTestRunner::class)
class PostCollectionTest {
    private lateinit var database: NodeSeekDatabase
    private val remote = FakePostRemoteDataSource()
    private val clock = MutableClock()

    @Before
    fun setUp() {
        database = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun repository(api: JsonApi) =
        OfflineFirstPostRepository(
            database,
            remote,
            clock,
            collections = PostCollectionWriter(api),
        )

    @Test
    fun `the page's own blob decides the star's starting state`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, collected = true, collectionCount = 7)
            }
            val repository = repository(api("""{"success":true}"""))

            repository.refreshThread(postId = 42, page = 1)

            val thread = requireNotNull(repository.thread(42).first())
            assertEquals(true, thread.collected)
            assertEquals(7, thread.collectionCount)
        }

    /** A page with no blob leaves it unknown, which is what keeps the star from being drawn at all. */
    @Test
    fun `a page that said nothing about the collection leaves it unknown`() =
        runTest {
            val repository = repository(api("""{"success":true}"""))

            repository.refreshThread(postId = 42, page = 1)

            val thread = requireNotNull(repository.thread(42).first())
            assertNull(thread.collected)
            assertNull(thread.collectionCount)
        }

    @Test
    fun `collecting writes the new state and count into the cached thread`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, collected = false, collectionCount = 6)
            }
            val repository = repository(api("""{"success":true,"message":"added","postCollectionCount":7}"""))
            repository.refreshThread(postId = 42, page = 1)

            repository.setCollected(postId = 42, collected = true)

            val thread = requireNotNull(repository.thread(42).first())
            assertEquals(true, thread.collected)
            assertEquals(7, thread.collectionCount)
        }

    @Test
    fun `un-collecting writes the removal through`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, collected = true, collectionCount = 7)
            }
            val repository = repository(api("""{"success":true,"message":"removed","postCollectionCount":6}"""))
            repository.refreshThread(postId = 42, page = 1)

            repository.setCollected(postId = 42, collected = false)

            val thread = requireNotNull(repository.thread(42).first())
            assertEquals(false, thread.collected)
            assertEquals(6, thread.collectionCount)
        }

    /**
     * The site is the one that knows. A double tap, or a star drawn from a stale page, both end with
     * the request and the answer disagreeing — and following the request would leave Room holding a
     * state the server never entered.
     */
    @Test
    fun `the site's echo wins over what was asked for`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, collected = false, collectionCount = 6)
            }
            val repository = repository(api("""{"success":true,"message":"removed","postCollectionCount":5}"""))
            repository.refreshThread(postId = 42, page = 1)

            repository.setCollected(postId = 42, collected = true)

            val thread = requireNotNull(repository.thread(42).first())
            assertEquals(false, thread.collected)
        }

    @Test
    fun `a refusal carries the site's own sentence and leaves the cache alone`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, collected = false, collectionCount = 6)
            }
            val repository = repository(api("""{"success":false,"message":"收藏夹已满"}"""))
            repository.refreshThread(postId = 42, page = 1)

            val thrown =
                assertThrows(SiteException::class.java) {
                    runBlocking { repository.setCollected(postId = 42, collected = true) }
                }

            assertEquals("收藏夹已满", thrown.detail)
            val thread = requireNotNull(repository.thread(42).first())
            assertEquals(false, thread.collected)
        }

    /** A build that never wired the writer refuses outright rather than reporting a write it never sent. */
    @Test
    fun `an unwired repository refuses the toggle`() =
        runTest {
            val repository = OfflineFirstPostRepository(database, remote, clock)

            val thrown =
                assertThrows(SiteException::class.java) {
                    runBlocking { repository.setCollected(postId = 42, collected = true) }
                }

            assertEquals(SiteError.NotWired, thrown.error)
        }

    /**
     * Only page 1 is guaranteed to carry the blob today, and a later page arriving without it must
     * not turn a known star into an unknown one.
     */
    @Test
    fun `appending a page that carried no blob keeps the known collection state`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(
                    postId,
                    page,
                    totalPages = 2,
                    collected = if (page == 1) true else null,
                    collectionCount = if (page == 1) 7 else null,
                )
            }
            val repository = repository(api("""{"success":true}"""))
            repository.refreshThread(postId = 42, page = 1)

            repository.extendThread(postId = 42, page = 2)

            val thread = requireNotNull(repository.thread(42).first())
            assertEquals(true, thread.collected)
            assertEquals(7, thread.collectionCount)
        }

    private fun api(answer: String) = FakeCollectionJsonApi(answer)
}

private class FakeCollectionJsonApi(
    private val answer: String,
) : JsonApi {
    override suspend fun getJson(path: String, referer: String): String = error("unexpected read of $path")

    override suspend fun postJson(path: String, body: String, referer: String): String {
        require(path == "/api/statistics/collection") { "unexpected write to $path" }
        return answer
    }
}
