package io.github.nodyssey.data

import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.local.toEntity
import io.github.nodyssey.data.local.toSnapshot
import io.github.nodyssey.model.RichNode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** How a thread accumulates across comment pages, and what a re-fetch is allowed to overwrite. */
@RunWith(RobolectricTestRunner::class)
class PostDetailCacheTest {
    private lateinit var database: NodeSeekDatabase
    private val remote = FakePostRemoteDataSource()
    private val clock = MutableClock()
    private lateinit var repository: OfflineFirstPostRepository

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        repository = OfflineFirstPostRepository(database, remote, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `page one stores the opening post and its comments`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 3, totalPages = 2)
            }

            repository.refreshThread(postId = 42, page = 1)
            val thread = requireNotNull(repository.thread(42).first())

            assertEquals("thread 42", thread.title)
            assertNotNull(thread.body)
            assertEquals(3, thread.comments.size)
            assertEquals(1, thread.loadedPages)
            assertEquals(2, thread.totalPages)
            assertTrue(thread.hasNextPage)
        }

    /**
     * The bug this exists to prevent: NodeSeek renders the opening post on page 1 only, so a naive
     * append would store `body = null` over the post the user is reading.
     */
    @Test
    fun `appending page two keeps the opening post`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(
                    postId = postId,
                    page = page,
                    body = if (page == 1) FakePostRemoteDataSource.content("the original post") else null,
                    commentCount = 2,
                    totalPages = 3,
                )
            }

            repository.refreshThread(postId = 42, page = 1)
            repository.refreshThread(postId = 42, page = 2)

            val thread = requireNotNull(repository.thread(42).first())
            val bodyText = (requireNotNull(thread.body).nodes.first() as RichNode.CodeBlock).code
            assertEquals("the original post", bodyText)
            assertEquals(4, thread.comments.size)
            assertEquals(2, thread.loadedPages)
        }

    @Test
    fun `comments stay in page then position order`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 3)
            }

            repository.refreshThread(postId = 42, page = 1)
            repository.refreshThread(postId = 42, page = 2)
            repository.refreshThread(postId = 42, page = 3)

            val texts =
                requireNotNull(repository.thread(42).first())
                    .comments
                    .map { (it.nodes.first() as RichNode.CodeBlock).code }
            assertEquals(
                listOf(
                    "page 1 comment 0",
                    "page 1 comment 1",
                    "page 2 comment 0",
                    "page 2 comment 1",
                    "page 3 comment 0",
                    "page 3 comment 1",
                ),
                texts,
            )
        }

    /** A thread shrinks when a comment is deleted, so a fresh read of page 1 has to clear the rest. */
    @Test
    fun `re-reading page one replaces every stored comment`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 2)
            }
            repository.refreshThread(postId = 42, page = 1)
            repository.refreshThread(postId = 42, page = 2)
            assertEquals(4, requireNotNull(repository.thread(42).first()).comments.size)

            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 1, totalPages = 1)
            }
            repository.refreshThread(postId = 42, page = 1)

            assertEquals(1, requireNotNull(repository.thread(42).first()).comments.size)
        }

    /** Re-reading page 1 deletes later rows, so the contiguous-page cursor resets with them. */
    @Test
    fun `re-reading page one resets the contiguous loaded page count`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 1, totalPages = 5)
            }

            repository.refreshThread(postId = 42, page = 1)
            repository.refreshThread(postId = 42, page = 2)
            repository.refreshThread(postId = 42, page = 3)
            repository.refreshThread(postId = 42, page = 1)

            val thread = requireNotNull(repository.thread(42).first())
            assertEquals(1, thread.loadedPages)
            assertEquals(1, thread.comments.size)
            assertTrue(thread.hasNextPage)
        }

    @Test
    fun `clearing session data removes feeds threads and read marks but keeps boards`() =
        runTest {
            database.boardDao().replaceAll(listOf(Board("inside", "内版", null).toEntity(0)))
            repository.refreshThread(postId = 42, page = 1)
            repository.markThreadRead(42)

            repository.clearSessionData()

            assertNull(repository.thread(42).first())
            assertNull(database.readMarkDao().find(42))
            assertEquals(1, database.boardDao().count())
        }

    @Test
    fun `a signed out cold start clears cache previously marked authenticated`() =
        runTest {
            repository.refreshThread(postId = 42, page = 1)
            repository.reconcileSession(isSignedIn = true, fingerprint = 7)

            val cleared = repository.reconcileSession(isSignedIn = false, fingerprint = 0)

            assertTrue(cleared)
            assertNull(repository.thread(42).first())
        }

    @Test
    fun `a signed out cold start keeps cache created while signed out`() =
        runTest {
            repository.refreshThread(postId = 42, page = 1)
            repository.reconcileSession(isSignedIn = false, fingerprint = 0)

            val cleared = repository.reconcileSession(isSignedIn = false, fingerprint = 0)

            assertFalse(cleared)
            assertNotNull(repository.thread(42).first())
        }

    @Test
    fun `a different authenticated cookie jar cannot inherit the previous cache`() =
        runTest {
            repository.refreshThread(postId = 42, page = 1)
            repository.reconcileSession(isSignedIn = true, fingerprint = 7)

            val cleared = repository.reconcileSession(isSignedIn = true, fingerprint = 8)

            assertTrue(cleared)
            assertNull(repository.thread(42).first())
        }

    @Test
    fun `an uncached thread emits null rather than an empty shell`() =
        runTest {
            assertNull(repository.thread(999).first())
        }

    @Test
    fun `a thread inside the cache window counts as fresh`() =
        runTest {
            repository.refreshThread(postId = 42, page = 1)

            clock.advanceBy(OfflineFirstPostRepository.THREAD_CACHE_TTL_MILLIS - 1)
            assertTrue(repository.isThreadFresh(42))

            clock.advanceBy(2)
            assertFalse(repository.isThreadFresh(42))
        }

    @Test
    fun `an unread thread is never fresh`() =
        runTest {
            assertFalse(repository.isThreadFresh(42))
        }

    /** Comments are cascade-deleted with their thread; a trim must not leave orphans behind. */
    @Test
    fun `trimming the cache removes the thread and its comments`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 1)
            }
            repository.refreshThread(postId = 1, page = 1)
            clock.advanceBy(1000)
            repository.refreshThread(postId = 2, page = 1)

            database.postDetailDao().trimTo(1)

            assertNull(database.postDetailDao().findDetail(1))
            assertNotNull(database.postDetailDao().findDetail(2))
            assertEquals(
                emptyList<Long>(),
                database
                    .postDetailDao()
                    .observeThread(1)
                    .first()
                    ?.comments
                    ?.map { it.postId }
                    .orEmpty(),
            )
        }

    /** Rich content survives a round trip through JSON, including the sealed-node discriminators. */
    @Test
    fun `stored rich content round trips`() =
        runTest {
            val nodes =
                listOf(
                    RichNode.Heading(level = 2, inlines = emptyList()),
                    RichNode.CodeBlock("println(1)", language = "kotlin"),
                    RichNode.Divider,
                    RichNode.Table(rows = listOf(listOf("a", "b"))),
                )
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(
                    postId = postId,
                    page = page,
                    body = FakePostRemoteDataSource.content("x").copy(nodes = nodes),
                    commentCount = 0,
                )
            }

            repository.refreshThread(postId = 42, page = 1)

            val stored = requireNotNull(database.postDetailDao().observeThread(42).first()).toSnapshot()
            assertEquals(nodes, requireNotNull(stored.body).nodes)
        }
}
