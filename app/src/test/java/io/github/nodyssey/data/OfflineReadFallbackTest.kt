package io.github.nodyssey.data

import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What a downloaded thread is *for*: reading it when the site cannot be reached.
 *
 * The detail screen knows nothing about any of this. It asks the repository for a page as usual, and
 * the repository quietly answers out of the download store — which is the only shape that makes
 * 「已离线」 mean anything without every reading surface growing an offline branch.
 */
@RunWith(RobolectricTestRunner::class)
class OfflineReadFallbackTest {
    private lateinit var database: NodeSeekDatabase
    private val remote = FakePostRemoteDataSource()
    private val clock = MutableClock()
    private val stored = FakeOfflineThreadReader()

    @Before
    fun setUp() {
        database = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun repository(reader: OfflineThreadReader? = stored) =
        OfflineFirstPostRepository(database, remote, clock, offlineThreads = reader)

    @Test
    fun `a thread with no connection reads from the downloaded copy`() =
        runTest {
            remote.detailError = SiteException(SiteError.Network)
            stored.pages[7L to 1] =
                StoredThreadPage(
                    detail = FakePostRemoteDataSource.detail(postId = 7, page = 1, commentCount = 3),
                    downloadedAtMillis = 500L,
                )

            repository().refreshThread(postId = 7, page = 1)

            val thread = requireNotNull(repository().thread(7).first())
            assertEquals("thread 7", thread.title)
            assertEquals(3, thread.comments.size)
        }

    /**
     * The copy keeps its own age.
     *
     * Stamped with "now" it would read as a page fetched a moment ago, and the screen would skip the
     * refresh that is the entire point of the network coming back.
     */
    @Test
    fun `a downloaded copy is not passed off as a fresh fetch`() =
        runTest {
            remote.detailError = SiteException(SiteError.Network)
            clock.nowMillis = 10_000_000L
            stored.pages[7L to 1] =
                StoredThreadPage(
                    detail = FakePostRemoteDataSource.detail(postId = 7, page = 1),
                    downloadedAtMillis = clock.nowMillis - OfflineFirstPostRepository.THREAD_CACHE_TTL_MILLIS - 1,
                )

            repository().refreshThread(postId = 7, page = 1)

            assertFalse(repository().isThreadFresh(7))
        }

    /**
     * A refusal is an answer, and answers are not overridden.
     *
     * A thread the site has just said this account may not see must not come back out of storage
     * looking as though nothing had happened.
     */
    @Test
    fun `a refusal from the site is passed through, stored copy or not`() =
        runTest {
            remote.detailError = SiteException(SiteError.LoginRequired)
            stored.pages[7L to 1] =
                StoredThreadPage(FakePostRemoteDataSource.detail(postId = 7, page = 1), downloadedAtMillis = 500L)

            assertEquals(SiteError.LoginRequired, refusalOf { repository().refreshThread(postId = 7, page = 1) })
            assertNull(repository().thread(7).first())
        }

    /**
     * A challenge is not a lost connection. The screen's way out of one is a WebView, and a reader
     * handed a stored copy instead would never learn their session wants attention.
     */
    @Test
    fun `a Cloudflare challenge is not answered from storage`() =
        runTest {
            remote.detailError = SiteException(SiteError.Cloudflare)
            stored.pages[7L to 1] =
                StoredThreadPage(FakePostRemoteDataSource.detail(postId = 7, page = 1), downloadedAtMillis = 500L)

            assertEquals(SiteError.Cloudflare, refusalOf { repository().refreshThread(postId = 7, page = 1) })
        }

    @Test
    fun `a page nobody downloaded still fails`() =
        runTest {
            remote.detailError = SiteException(SiteError.Network)

            assertEquals(SiteError.Network, refusalOf { repository().refreshThread(postId = 7, page = 4) })
        }

    @Test
    fun `a build with no download store simply has no fallback`() =
        runTest {
            remote.detailError = SiteException(SiteError.Network)
            stored.pages[7L to 1] =
                StoredThreadPage(FakePostRemoteDataSource.detail(postId = 7, page = 1), downloadedAtMillis = 500L)

            assertEquals(SiteError.Network, refusalOf { repository(reader = null).refreshThread(postId = 7, page = 1) })
        }

    /** The site's own error behind whatever the call threw, so a test can name it rather than a type. */
    private suspend fun refusalOf(block: suspend () -> Unit): SiteError? =
        runCatching { block() }.exceptionOrNull().let { (it as? SiteException)?.error }

    private class FakeOfflineThreadReader : OfflineThreadReader {
        val pages = mutableMapOf<Pair<Long, Int>, StoredThreadPage>()

        override suspend fun storedPage(
            postId: Long,
            page: Int,
        ): StoredThreadPage? = pages[postId to page]
    }
}
