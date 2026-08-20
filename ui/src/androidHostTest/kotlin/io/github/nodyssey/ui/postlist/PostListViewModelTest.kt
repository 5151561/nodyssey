package io.github.nodyssey.ui.postlist

import androidx.paging.testing.asSnapshot
import io.github.nodyssey.core.net.JsonSource
import io.github.nodyssey.data.CategoryRepository
import io.github.nodyssey.data.FakePostRemoteDataSource
import io.github.nodyssey.data.MutableClock
import io.github.nodyssey.data.OfflineFirstPostRepository
import io.github.nodyssey.data.inMemoryDatabase
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.session.SessionState
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.testSettingsRepository
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The ViewModel is now a thin state holder: the list belongs to Room, and Paging owns loading and
 * error state. What is left to test is board mirroring, board selection, and that rows really do
 * reach the UI through the database.
 *
 * Three tests that used to live here — "does not start a second page while one is in flight",
 * "discards a response that arrives after the board changed" and "cancelling an in-flight load does
 * not surface an error" — now live in `FeedRemoteMediatorTest`. They were not dropped; the
 * hand-rolled code they guarded was.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PostListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val remote = FakePostRemoteDataSource()
    private val clock = MutableClock()
    private lateinit var database: NodeSeekDatabase

    /** Fails on purpose, so the repository falls back to the offline board list. */
    private val failingJson =
        object : JsonSource {
            override suspend fun getJson(
                path: String,
                referer: String,
            ): String = throw SiteException(SiteError.Network)
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = inMemoryDatabase(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    private val session = MutableStateFlow(SessionState())

    private fun TestScope.viewModel(
        settings: SettingsRepository = testSettingsRepository(backgroundScope),
    ) = PostListViewModel(
        repository = OfflineFirstPostRepository(database, remote, clock),
        categoryRepository = CategoryRepository(failingJson, database.boardDao(), clock),
        settingsRepository = settings,
        session = session,
    )

    @Test
    fun `starts on the front page with the front page tab selected`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            assertEquals(null, vm.uiState.value.categorySlug)
        }

    @Test
    fun `mirrors the board list the repository owns`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            val boards = vm.uiState.value.boards
            assertEquals(CategoryRepository.FRONT_PAGE, boards.first())
            // The API call failed, so this is the offline fallback list — still more than nothing.
            assertTrue("expected fallback boards, got $boards", boards.size > 1)
        }

    @Test
    fun `the strip honours the 首页版块 preference and always keeps the front page`() =
        runTest(dispatcher) {
            val settings = testSettingsRepository(backgroundScope)
            settings.setHomeBoardHidden("trade", true)
            settings.setHomeBoardHidden("life", true)
            val vm = viewModel(settings)
            advanceUntilIdle()

            val boards = vm.uiState.value.boards
            assertEquals(CategoryRepository.FRONT_PAGE, boards.first())
            val slugs = boards.drop(1).map { it.slug }
            assertTrue("tech" in slugs)
            assertTrue("trade" !in slugs)
            assertTrue("life" !in slugs)
        }

    /**
     * Hiding the board being read would leave a selected pill that is no longer on the strip, and the
     * feed underneath would keep paging a board the user can no longer see or leave.
     */
    @Test
    fun `hiding the board being read falls back to the front page`() =
        runTest(dispatcher) {
            val settings = testSettingsRepository(backgroundScope)
            val vm = viewModel(settings)
            advanceUntilIdle()
            vm.selectCategory("trade")
            advanceUntilIdle()
            assertEquals("trade", vm.uiState.value.categorySlug)

            settings.setHomeBoardHidden("trade", true)
            advanceUntilIdle()

            assertEquals(null, vm.uiState.value.categorySlug)
        }

    @Test
    fun `selecting a board updates the state and the selected tab`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            val target =
                vm.uiState.value.boards
                    .first { it.slug != null }
            vm.selectCategory(target.slug)
            advanceUntilIdle()

            assertEquals(target.slug, vm.uiState.value.categorySlug)
        }

    @Test
    fun `re-selecting the current board is a no-op`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            vm.selectCategory("tech")
            advanceUntilIdle()
            val before = vm.uiState.value

            vm.selectCategory("tech")
            advanceUntilIdle()

            // Same instance, so nothing downstream — the pager included — was rebuilt.
            assertTrue(before === vm.uiState.value)
        }

    @Test
    fun `the challenge url follows the selected board`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            assertTrue(vm.challengeUrl().startsWith("https://www.nodeseek.com"))

            vm.selectCategory("tech")
            advanceUntilIdle()

            assertTrue("got ${vm.challengeUrl()}", vm.challengeUrl().contains("tech"))
        }

    /** End to end through Room: the network writes, the pager reads, the rows come out in order. */
    @Test
    fun `posts reach the ui through the database`() =
        runTest(dispatcher) {
            remote.listResult = { slug, page ->
                FakePostRemoteDataSource.page(slug, page, firstId = 10, count = 3, hasNextPage = false)
            }
            val vm = viewModel()
            advanceUntilIdle()

            val titles = vm.feed.asSnapshot().map { it.summary.title }

            assertEquals(listOf("post 10", "post 11", "post 12"), titles)
        }

    /**
     * The bug this exists for: signing in used to change nothing on screen. The cookies were shared
     * all along, but the feed was inside its five-minute cache window, so the signed-in reader kept
     * being served the list a signed-out one had fetched.
     */
    @Test
    fun `signing in refetches the feed instead of serving the signed-out cache`() =
        runTest(dispatcher) {
            remote.listResult = { slug, page ->
                FakePostRemoteDataSource.page(slug, page, firstId = 10, count = 1, hasNextPage = false)
            }
            val vm = viewModel()
            advanceUntilIdle()
            assertEquals(listOf("post 10"), vm.feed.asSnapshot().map { it.summary.title })
            val requestsBefore = remote.listRequests.size

            // What the site serves a signed-in reader is a different list.
            remote.listResult = { slug, page ->
                FakePostRemoteDataSource.page(slug, page, firstId = 99, count = 1, hasNextPage = false)
            }
            session.value = SessionState(isSignedIn = true, fingerprint = 1, generation = 1)
            advanceUntilIdle()

            assertEquals(listOf("post 99"), vm.feed.asSnapshot().map { it.summary.title })
            assertTrue(
                "expected a new request, still at $requestsBefore",
                remote.listRequests.size > requestsBefore,
            )
        }

    @Test
    fun `signing out removes content fetched by the authenticated session`() =
        runTest(dispatcher) {
            val repository = OfflineFirstPostRepository(database, remote, clock)
            repository.refreshThread(postId = 42, page = 1)
            session.value = SessionState(isSignedIn = true, fingerprint = 7)
            val vm =
                PostListViewModel(
                    repository = repository,
                    categoryRepository = CategoryRepository(failingJson, database.boardDao(), clock),
                    settingsRepository = testSettingsRepository(backgroundScope),
                    session = session,
                )
            advanceUntilIdle()
            assertNotNull(repository.thread(42).first())

            session.value = SessionState(isSignedIn = false, fingerprint = 0, generation = 1)
            advanceUntilIdle()

            assertNull(repository.thread(42).first())
            assertEquals(null, vm.uiState.value.categorySlug)
        }

    /** A cold start reads cookies that were already on disk; that is not a session *change*. */
    @Test
    fun `the session the app started with does not trigger an extra fetch`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            vm.feed.asSnapshot()
            val requestsBefore = remote.listRequests.size

            // Same generation re-emitted: the WebView was opened and nothing changed.
            session.value = SessionState()
            advanceUntilIdle()
            vm.feed.asSnapshot()

            assertEquals(requestsBefore, remote.listRequests.size)
        }
}
