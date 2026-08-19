package io.github.nodyssey.data.offline

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.github.nodyssey.data.FakePostRemoteDataSource
import io.github.nodyssey.data.MutableClock
import io.github.nodyssey.data.OfflineFailure
import io.github.nodyssey.data.OfflineSettings
import io.github.nodyssey.data.OfflineState
import io.github.nodyssey.data.RoomCollectedPostMetaStore
import io.github.nodyssey.data.inMemoryDatabase
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.testPreferenceStore
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.richtext.RichNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.file.Files

/**
 * What 「已离线」 has to be true for.
 *
 * Every claim the 收藏 screen makes about a downloaded thread is a claim about bytes, so these tests
 * run the real engine against real Room and a real directory: a fake DAO would only prove that the
 * fake stores what it was handed.
 */
@RunWith(RobolectricTestRunner::class)
class OfflineLibraryTest {
    private lateinit var database: NodeSeekDatabase
    private val remote = FakePostRemoteDataSource()
    private val clock = MutableClock()
    private val scheduler = RecordingScheduler()
    private val images = FakeImageSource()
    private lateinit var files: OfflineFileStore

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        files = OfflineFileStore.of(Files.createTempDirectory("offline-test").toFile().apply { deleteOnExit() })
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun library(store: DataStore<Preferences>) =
        RoomOfflineLibrary(
            dao = database.offlineDao(),
            remote = remote,
            files = files,
            images = images,
            collectedMeta = RoomCollectedPostMetaStore(database.collectedPostMetaDao(), clock),
            settingsStore = OfflineSettingsStore(store),
            scheduler = scheduler,
            clock = clock,
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined, default = Dispatchers.Unconfined),
            requestSpacingMillis = 0,
        )

    @Test
    fun `a download stores every page of the thread, not just the one on screen`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 3)
            }
            val library = library(testPreferenceStore(backgroundScope))

            library.download(listOf(42L))
            library.drainQueue()

            val state = library.states.first()[42L]
            assertTrue("$state", state is OfflineState.Downloaded)
            assertEquals(listOf(42L to 1, 42L to 2, 42L to 3), remote.detailRequests)
            // Page 3 is readable without a network, which is the whole promise.
            assertEquals(2, requireNotNull(library.storedPage(42, 3)).detail.comments.size)
        }

    @Test
    fun `a stored page carries the moment it was downloaded, not the moment it is read`() =
        runTest {
            val library = library(testPreferenceStore(backgroundScope))
            clock.nowMillis = 5_000L

            library.download(listOf(7L))
            library.drainQueue()
            clock.nowMillis = 9_000L

            assertEquals(5_000L, requireNotNull(library.storedPage(7, 1)).downloadedAtMillis)
        }

    @Test
    fun `nothing is stored for a page the download never reached`() =
        runTest {
            val library = library(testPreferenceStore(backgroundScope))

            library.download(listOf(7L))
            library.drainQueue()

            assertNull(library.storedPage(7, 2))
        }

    @Test
    fun `a thread the site will not show is failed as unavailable, and the queue moves on`() =
        runTest {
            remote.detailError = SiteException(SiteError.Http(404))
            val library = library(testPreferenceStore(backgroundScope))

            library.download(listOf(1L, 2L))
            library.drainQueue()

            assertEquals(OfflineState.Failed(OfflineFailure.Unavailable), library.states.first()[1L])
            assertEquals(OfflineState.Failed(OfflineFailure.Unavailable), library.states.first()[2L])
        }

    /**
     * A dropped connection stops the whole drain rather than burning through the queue.
     *
     * Marking every remaining thread failed would be a screenful of red for one lost network, and
     * would leave the reader tapping 重试 on each of them once it came back.
     */
    @Test
    fun `a lost network stops the drain and asks to be run again`() =
        runTest {
            remote.detailError = SiteException(SiteError.Network)
            val library = library(testPreferenceStore(backgroundScope))

            library.download(listOf(1L, 2L))
            val outcome = library.drainQueue()

            assertEquals(DrainOutcome.NETWORK_FAILED, outcome)
            assertEquals(OfflineState.Failed(OfflineFailure.Network), library.states.first()[1L])
            assertEquals(OfflineState.Downloading(null), library.states.first()[2L])
        }

    @Test
    fun `the site's own reply count is what makes a stored copy stale`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 4, totalPages = 1)
            }
            val library = library(testPreferenceStore(backgroundScope))
            library.download(listOf(3L))
            library.drainQueue()

            assertEquals(OfflineState.Downloaded(bytes = expectedBytes(library, 3L)), library.states.first()[3L])

            library.noteReplyCounts(mapOf(3L to 7))

            val state = library.states.first()[3L]
            assertEquals(3, (state as OfflineState.Stale).behindReplies)
            assertEquals(listOf(3L), library.staleIds())
        }

    /** A count for a thread nobody downloaded has nothing to be behind, and must not invent a row. */
    @Test
    fun `reply counts for undownloaded threads are ignored`() =
        runTest {
            val library = library(testPreferenceStore(backgroundScope))

            library.noteReplyCounts(mapOf(99L to 12))

            assertTrue(library.states.first().isEmpty())
        }

    /**
     * The catch-up re-reads the last stored page rather than starting over.
     *
     * That page was partial when it was stored — the replies that arrived since begin inside it —
     * so skipping it would leave a hole, and starting from page 1 would re-download the thread.
     */
    @Test
    fun `a catch-up starts at the last stored page`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 2)
            }
            val library = library(testPreferenceStore(backgroundScope))
            library.download(listOf(5L))
            library.drainQueue()
            remote.detailRequests.clear()
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 3)
            }

            library.download(listOf(5L))
            library.drainQueue()

            assertEquals(listOf(5L to 2, 5L to 3), remote.detailRequests)
        }

    /**
     * A half-thread is not readable and the retention sweep can never reach it — it counts from a
     * completed copy's timestamp, which a failed first attempt never got.
     */
    @Test
    fun `a failed first attempt leaves no fragment on disk`() =
        runTest {
            remote.detailResult = { postId, page ->
                if (page == 2) throw SiteException(SiteError.Http(404))
                FakePostRemoteDataSource
                    .detail(postId, page, commentCount = 2, totalPages = 3)
                    .copy(
                        body =
                        FakePostRemoteDataSource
                            .content("with a picture")
                            .copy(nodes = listOf(RichNode.BlockImage("https://img.example/a.png", alt = null))),
                    )
            }
            val library = library(testPreferenceStore(backgroundScope))

            library.download(listOf(4L))
            library.drainQueue()

            assertEquals(OfflineState.Failed(OfflineFailure.Unavailable), library.states.first()[4L])
            assertNull(library.storedPage(4, 1))
            assertEquals(0L, library.usage.first().totalBytes)
            assertNull(files.fileOf("https://img.example/a.png"))
        }

    /**
     * The one route that reaches a collection made on the web and never opened here.
     *
     * `list-collection` will not name its board or its author, and nothing else on this device has
     * ever seen the thread — but the pages a download fetches say all of it.
     */
    @Test
    fun `a completed download writes down what the pages said about the thread`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource
                    .detail(postId, page, commentCount = 3, totalPages = 1)
                    .copy(
                        body =
                        FakePostRemoteDataSource
                            .content("the opening post")
                            .copy(authorName = "原作者", categoryTitle = "日常", createdAtText = "3 天前"),
                    )
            }
            val library = library(testPreferenceStore(backgroundScope))

            library.download(listOf(9L))
            library.drainQueue()

            val known = requireNotNull(metaStore().observe().first()[9L])
            assertEquals("thread 9", known.title)
            assertEquals("原作者", known.authorName)
            assertEquals("日常", known.categoryTitle)
            assertEquals("3 天前", known.createdAtText)
            assertEquals(3, known.commentCount)
        }

    /** A download that never completed has nothing to say about the thread, and says nothing. */
    @Test
    fun `a failed download writes nothing down`() =
        runTest {
            remote.detailError = SiteException(SiteError.Http(404))
            val library = library(testPreferenceStore(backgroundScope))

            library.download(listOf(9L))
            library.drainQueue()

            assertTrue(metaStore().observe().first().isEmpty())
        }

    private fun metaStore() = RoomCollectedPostMetaStore(database.collectedPostMetaDao(), clock)

    @Test
    fun `cancelling a first download leaves nothing behind`() =
        runTest {
            val library = library(testPreferenceStore(backgroundScope))
            library.download(listOf(8L))

            library.cancel(8L)

            assertTrue(library.states.first().isEmpty())
            assertNull(library.storedPage(8, 1))
        }

    @Test
    fun `cancelling a catch-up keeps the copy that was already here`() =
        runTest {
            val library = library(testPreferenceStore(backgroundScope))
            library.download(listOf(8L))
            library.drainQueue()

            library.download(listOf(8L))
            library.cancel(8L)

            assertTrue(library.states.first()[8L] is OfflineState.Downloaded)
            assertEquals("thread 8", requireNotNull(library.storedPage(8, 1)).detail.title)
        }

    @Test
    fun `pictures are stored once and shared between the threads that use them`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource
                    .detail(postId, page, commentCount = 0, totalPages = 1)
                    .copy(
                        body =
                        FakePostRemoteDataSource
                            .content("with a picture")
                            .copy(nodes = listOf(RichNode.BlockImage("https://img.example/a.png", alt = null))),
                    )
            }
            val library = library(testPreferenceStore(backgroundScope))

            library.download(listOf(1L, 2L))
            library.drainQueue()

            assertEquals(listOf("https://img.example/a.png"), images.fetched)
            assertEquals(images.bytes.size.toLong(), library.usage.first().imageBytes)
            assertEquals(2, library.usage.first().posts)
        }

    @Test
    fun `switching pictures off stores the text alone`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource
                    .detail(postId, page, commentCount = 0, totalPages = 1)
                    .copy(
                        body =
                        FakePostRemoteDataSource
                            .content("with a picture")
                            .copy(nodes = listOf(RichNode.BlockImage("https://img.example/a.png", alt = null))),
                    )
            }
            val library = library(testPreferenceStore(backgroundScope))
            library.updateSettings(OfflineSettings(includeImages = false))

            library.download(listOf(1L))
            library.drainQueue()

            assertTrue(images.fetched.isEmpty())
            assertEquals(0L, library.usage.first().imageBytes)
            assertTrue(library.usage.first().textBytes > 0)
        }

    @Test
    fun `clearing removes every stored thread and every file`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource
                    .detail(postId, page, commentCount = 1, totalPages = 1)
                    .copy(
                        body =
                        FakePostRemoteDataSource
                            .content("with a picture")
                            .copy(nodes = listOf(RichNode.BlockImage("https://img.example/a.png", alt = null))),
                    )
            }
            val library = library(testPreferenceStore(backgroundScope))
            library.download(listOf(1L))
            library.drainQueue()

            library.clearAll()

            assertTrue(library.states.first().isEmpty())
            assertNull(files.fileOf("https://img.example/a.png"))
            assertEquals(0L, library.usage.first().totalBytes)
        }

    @Test
    fun `the retention sweep drops copies older than the setting and keeps the rest`() =
        runTest {
            val library = library(testPreferenceStore(backgroundScope))
            library.updateSettings(OfflineSettings(retentionDays = 7))
            clock.nowMillis = DAY
            library.download(listOf(1L))
            library.drainQueue()
            clock.nowMillis = DAY * 20
            library.download(listOf(2L))
            library.drainQueue()

            library.sweepExpired()

            assertEquals(setOf(2L), library.states.first().keys)
        }

    @Test
    fun `不清理 keeps everything however old`() =
        runTest {
            val library = library(testPreferenceStore(backgroundScope))
            library.updateSettings(OfflineSettings(retentionDays = OfflineSettings.KEEP_FOREVER))
            clock.nowMillis = DAY
            library.download(listOf(1L))
            library.drainQueue()
            clock.nowMillis = DAY * 400

            library.sweepExpired()

            assertEquals(setOf(1L), library.states.first().keys)
        }

    /** Before anything has been downloaded there is no basis for a size, and the toolbar says nothing. */
    @Test
    fun `an estimate needs a past download to be based on`() =
        runTest {
            val library = library(testPreferenceStore(backgroundScope))

            assertNull(library.estimateBytes(listOf(1L, 2L)))

            library.download(listOf(1L))
            library.drainQueue()

            val estimate = requireNotNull(library.estimateBytes(listOf(2L, 3L)))
            assertTrue("$estimate", estimate > 0)
            // 1 is already here; only what would actually be fetched is counted.
            assertNull(library.estimateBytes(listOf(1L)))
        }

    @Test
    fun `turning 仅 Wi-Fi 下载 off re-enqueues a queue that was waiting for it`() =
        runTest {
            val library = library(testPreferenceStore(backgroundScope))
            library.download(listOf(1L))
            scheduler.starts.clear()

            library.updateSettings(OfflineSettings(wifiOnly = false))

            assertEquals(listOf(false to true), scheduler.starts)
        }

    private suspend fun expectedBytes(
        library: RoomOfflineLibrary,
        postId: Long,
    ): Long = (library.states.first()[postId] as OfflineState.Downloaded).bytes

    private class RecordingScheduler : OfflineWorkScheduler {
        /** (wifiOnly, restart) for every kick, so a test can tell a re-enqueue from an ordinary one. */
        val starts = mutableListOf<Pair<Boolean, Boolean>>()
        var maintenance = 0

        override fun startDrain(
            wifiOnly: Boolean,
            restart: Boolean,
        ) {
            starts += wifiOnly to restart
        }

        override fun ensureMaintenance() {
            maintenance++
        }
    }

    private class FakeImageSource : OfflineImageSource {
        val fetched = mutableListOf<String>()
        val bytes = ByteArray(64) { it.toByte() }

        override suspend fun fetch(
            url: String,
            maxBytes: Long,
        ): ByteArray? {
            fetched += url
            return bytes
        }
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
