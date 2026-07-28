package io.github.nodyssey.ui.postdetail

import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException
import io.github.nodyssey.data.FakePostRemoteDataSource
import io.github.nodyssey.data.MutableClock
import io.github.nodyssey.data.OfflineFirstPostRepository
import io.github.nodyssey.data.inMemoryDatabase
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.session.SessionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

/** Offline-first behaviour of the detail screen, against a real database. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PostDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val remote = FakePostRemoteDataSource()
    private val clock = MutableClock()
    private lateinit var database: NodeSeekDatabase
    private lateinit var repository: OfflineFirstPostRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = inMemoryDatabase(dispatcher)
        repository = OfflineFirstPostRepository(database, remote, clock)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    private val session = MutableStateFlow(SessionState())

    private fun viewModel(postId: Long = 42) = PostDetailViewModel(postId, repository, session)

    /**
     * A thread that answered "登录后查看" a second ago has content now. The freshness window would
     * otherwise suppress the one request that matters.
     */
    @Test
    fun `signing in refetches the thread even inside the cache window`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            val requestsBefore = remote.detailRequests.size
            assertTrue("expected an initial fetch", requestsBefore > 0)

            session.value = SessionState(isSignedIn = true, fingerprint = 1, generation = 1)
            advanceUntilIdle()

            assertTrue(
                "expected a refetch, still at $requestsBefore",
                remote.detailRequests.size > requestsBefore,
            )
        }

    @Test
    fun `an unchanged session does not refetch the thread`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            val requestsBefore = remote.detailRequests.size

            session.value = SessionState()
            advanceUntilIdle()

            assertEquals(requestsBefore, remote.detailRequests.size)
            assertNull(vm.uiState.value.error)
        }

    @Test
    fun `loads a thread that is not cached yet`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 3, totalPages = 1)
            }

            val vm = viewModel()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("thread 42", state.title)
            assertNotNull(state.body)
            assertEquals(3, state.comments.size)
            assertFalse(state.isLoading)
            assertNull(state.error)
        }

    @Test
    fun `appends later comment pages into one thread`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 3)
            }
            val vm = viewModel()
            advanceUntilIdle()
            assertEquals(2, vm.uiState.value.comments.size)

            vm.loadNextPage()
            advanceUntilIdle()

            assertEquals(4, vm.uiState.value.comments.size)
            assertEquals(2, vm.uiState.value.page)
            assertTrue(vm.uiState.value.hasNextPage)
        }

    @Test
    fun `stops paging at the last page`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 1, totalPages = 2)
            }
            val vm = viewModel()
            advanceUntilIdle()

            vm.loadNextPage()
            advanceUntilIdle()
            assertFalse(vm.uiState.value.hasNextPage)

            val before = remote.detailRequests.size
            vm.loadNextPage()
            advanceUntilIdle()

            assertEquals(before, remote.detailRequests.size)
        }

    /**
     * The point of phase two: a thread read moments ago paints from the database and issues no
     * request at all.
     */
    @Test
    fun `a freshly cached thread is shown without hitting the network`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 1)
            }
            repository.refreshThread(postId = 42, page = 1)
            val requestsAfterSeeding = remote.detailRequests.size

            val vm = viewModel()
            advanceUntilIdle()

            assertEquals(requestsAfterSeeding, remote.detailRequests.size)
            assertEquals(2, vm.uiState.value.comments.size)
            assertNotNull(vm.uiState.value.body)
        }

    @Test
    fun `a stale cached thread is shown and then refreshed`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 1, totalPages = 1)
            }
            repository.refreshThread(postId = 42, page = 1)
            clock.advanceBy(OfflineFirstPostRepository.THREAD_CACHE_TTL_MILLIS + 1)

            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 5, totalPages = 1)
            }
            val vm = viewModel()
            advanceUntilIdle()

            assertEquals(5, vm.uiState.value.comments.size)
        }

    @Test
    fun `removing session data clears a still alive detail entry`() =
        runTest(dispatcher) {
            repository.refreshThread(postId = 42, page = 1)
            val vm = viewModel()
            advanceUntilIdle()
            assertNotNull(vm.uiState.value.body)

            repository.clearSessionData()
            advanceUntilIdle()

            assertNull(vm.uiState.value.body)
            assertTrue(vm.uiState.value.comments.isEmpty())
            assertEquals(1, vm.uiState.value.page)
        }

    @Test
    fun `refresh after page three resumes from page two without a gap`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 1, totalPages = 5)
            }
            val vm = viewModel()
            advanceUntilIdle()
            vm.loadNextPage()
            advanceUntilIdle()
            vm.loadNextPage()
            advanceUntilIdle()
            assertEquals(3, vm.uiState.value.page)

            vm.refresh()
            advanceUntilIdle()
            assertEquals(1, vm.uiState.value.page)

            vm.loadNextPage()
            advanceUntilIdle()

            assertEquals(2, remote.detailRequests.last().second)
            assertEquals(2, vm.uiState.value.page)
        }

    /** A flaky connection must not throw away content the user is already reading. */
    @Test
    fun `a failed refresh keeps the cached thread on screen`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 1)
            }
            repository.refreshThread(postId = 42, page = 1)
            val vm = viewModel()
            advanceUntilIdle()

            remote.detailError = NodeSeekException(NodeSeekError.Network)
            vm.refresh()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(NodeSeekError.Network, state.error)
            assertEquals(2, state.comments.size)
            assertNotNull(state.body)
        }

    @Test
    fun `surfaces a typed error rather than a message string`() =
        runTest(dispatcher) {
            remote.detailError = NodeSeekException(NodeSeekError.LoginRequired)

            val vm = viewModel()
            advanceUntilIdle()

            assertEquals(NodeSeekError.LoginRequired, vm.uiState.value.error)
            assertFalse(vm.uiState.value.isLoading)
            assertNull(vm.uiState.value.body)
        }

    @Test
    fun `an unclassified failure becomes Unknown, not a crash`() =
        runTest(dispatcher) {
            remote.detailError = IllegalStateException("boom")

            val vm = viewModel()
            advanceUntilIdle()

            assertEquals(NodeSeekError.Unknown, vm.uiState.value.error)
        }

    /**
     * Regression: `runCatching` catches `CancellationException` too, so a load cut short by a refresh
     * used to surface a failure the user never caused.
     */
    @Test
    fun `cancelling an in-flight load does not surface an error`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 1, totalPages = 3)
            }
            val vm = viewModel()
            advanceUntilIdle()

            val gate = CompletableDeferred<Unit>()
            remote.gate = gate
            vm.loadNextPage()
            // Let it start and park on the gate — otherwise there is nothing to cancel.
            advanceUntilIdle()

            remote.gate = null
            vm.refresh()
            advanceUntilIdle()
            gate.complete(Unit)
            advanceUntilIdle()

            assertNull(vm.uiState.value.error)
            assertFalse(vm.uiState.value.isLoading)
            assertFalse(vm.uiState.value.isAppending)
        }

    @Test
    fun `opening a thread marks it read`() =
        runTest(dispatcher) {
            viewModel(postId = 42)
            advanceUntilIdle()

            assertNotNull(database.readMarkDao().find(42))
        }

    /**
     * Found on a device in aeroplane mode: opening an uncached post shows nothing but an error, and the
     * thread was still marked read and dimmed in the list as though it had been.
     */
    @Test
    fun `a thread that failed to load and has no cache is not marked read`() =
        runTest(dispatcher) {
            remote.detailError = NodeSeekException(NodeSeekError.Network)

            val vm = viewModel(postId = 42)
            advanceUntilIdle()

            assertNull(vm.uiState.value.body)
            assertEquals(NodeSeekError.Network, vm.uiState.value.error)
            assertNull("marked read despite showing only an error", database.readMarkDao().find(42))
        }

    /** The other half of the same rule: content read from the cache offline does count as read. */
    @Test
    fun `a cached thread read with no network is marked read`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 1)
            }
            repository.refreshThread(postId = 42, page = 1)

            remote.detailError = NodeSeekException(NodeSeekError.Network)
            clock.advanceBy(OfflineFirstPostRepository.THREAD_CACHE_TTL_MILLIS + 1)
            val vm = viewModel(postId = 42)
            advanceUntilIdle()

            assertEquals(2, vm.uiState.value.comments.size)
            assertNotNull("cached content was read but not recorded", database.readMarkDao().find(42))
        }

    /**
     * Regression: jumping used to append the target page directly after page 1, leaving a gap in the
     * middle of the thread and a `loadedPages` cursor that claimed pages 2-4 were present.
     */
    @Test
    fun `jumping to a later page fetches every page in between`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 5)
            }
            val vm = viewModel()
            advanceUntilIdle()

            vm.loadPage(4)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(listOf(2, 3, 4), remote.detailRequests.takeLast(3).map { it.second })
            assertEquals(4, state.page)
            assertEquals(8, state.comments.size)
            assertEquals((1..4).flatMap { page -> List(2) { page } }, state.commentPages)
            assertEquals(4, state.pendingScrollPage)

            vm.onPageScrollHandled()
            assertNull(vm.uiState.value.pendingScrollPage)
        }

    @Test
    fun `jumping to an already loaded page issues no request`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 2, totalPages = 3)
            }
            val vm = viewModel()
            advanceUntilIdle()
            vm.loadNextPage()
            advanceUntilIdle()
            val requestsBefore = remote.detailRequests.size

            vm.loadPage(1)
            advanceUntilIdle()

            assertEquals(requestsBefore, remote.detailRequests.size)
            assertEquals(1, vm.uiState.value.pendingScrollPage)
            assertEquals(2, vm.uiState.value.page)
        }

    @Test
    fun `the post url points at the page currently being read`() =
        runTest(dispatcher) {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, commentCount = 1, totalPages = 3)
            }
            val vm = viewModel()
            advanceUntilIdle()
            vm.loadNextPage()
            advanceUntilIdle()

            assertTrue("got ${vm.postUrl()}", vm.postUrl().endsWith("-2"))
        }
}
