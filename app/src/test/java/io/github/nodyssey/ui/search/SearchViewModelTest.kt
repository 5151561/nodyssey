package io.github.nodyssey.ui.search

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import io.github.nodyssey.core.net.JsonSource
import io.github.nodyssey.data.CategoryRepository
import io.github.nodyssey.data.FakePostRemoteDataSource
import io.github.nodyssey.data.FeedPost
import io.github.nodyssey.data.MutableClock
import io.github.nodyssey.data.OfflineFirstPostRepository
import io.github.nodyssey.data.SearchRepository
import io.github.nodyssey.data.UserSearchResult
import io.github.nodyssey.data.inMemoryDatabase
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.testSettingsRepository
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.SearchTarget
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What the ViewModel still decides now that post search is a feed.
 *
 * The paging itself moved to `OfflineFirstPostRepository.searchFeed` and is covered by
 * `SearchFeedTest`; what is left here is which feed gets asked for, and — the part that used to
 * cost requests — when a new one is asked for at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val remote = FakePostRemoteDataSource()
    private val clock = MutableClock()
    private lateinit var database: NodeSeekDatabase

    /** Fails on purpose, so the board strip falls back to the offline list instead of the network. */
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
        remote.searchResult = { request ->
            FakePostRemoteDataSource.page(
                request.categorySlug,
                request.page,
                firstId = 100,
                count = 2,
                hasNextPage = false,
            )
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    private fun TestScope.viewModel() =
        SearchViewModel(
            postRepository = OfflineFirstPostRepository(database, remote, clock),
            searchRepository = NoUsersSearchRepository,
            categoryRepository = CategoryRepository(failingJson, database.boardDao(), clock),
            settings = testSettingsRepository(backgroundScope),
        )

    /**
     * Types into the box the way the UI does.
     *
     * [Snapshot.sendApplyNotifications] is what a Compose frame would do: without it the
     * `snapshotFlow` watching the field never sees the edit, and the ViewModel keeps results the
     * box no longer describes — in the test only, which is exactly why it has to be spelled out.
     */
    private fun TestScope.type(
        viewModel: SearchViewModel,
        text: String,
    ) {
        viewModel.query.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()
    }

    private suspend fun Flow<PagingData<FeedPost>>.ids(): List<Long> = asSnapshot().map { it.summary.postId }

    @Test
    fun `submitting a search asks for the feed named by the box, the board and the order`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.selectBoard("tech")
            vm.selectSort(FeedSort.POST_TIME)
            type(vm, "  android  ")

            vm.submitSearch()
            advanceUntilIdle()
            vm.postResults.ids()

            assertEquals(
                FakePostRemoteDataSource.SearchRequest(
                    query = "android",
                    page = 1,
                    categorySlug = "tech",
                    sort = FeedSort.POST_TIME,
                ),
                remote.searchRequests.single(),
            )
            assertEquals("android", vm.uiState.value.submittedQuery)
        }

    /**
     * The regression that made a search screen expensive to sit on: submitting the same query again
     * used to tear the pager down and rebuild it, and every rebuild started at page one.
     */
    @Test
    fun `re-submitting an unchanged search spends no further request`() =
        runTest(dispatcher) {
            val vm = viewModel()
            type(vm, "android")
            vm.submitSearch()
            advanceUntilIdle()
            vm.postResults.ids()
            val before = remote.searchRequests.size

            vm.submitSearch()
            advanceUntilIdle()
            vm.postResults.ids()

            assertEquals(before, remote.searchRequests.size)
        }

    @Test
    fun `changing the board re-runs the search against the new one`() =
        runTest(dispatcher) {
            val vm = viewModel()
            type(vm, "android")
            vm.submitSearch()
            advanceUntilIdle()
            vm.postResults.ids()

            vm.selectBoard("tech")
            advanceUntilIdle()
            vm.postResults.ids()

            assertEquals(listOf(null, "tech"), remote.searchRequests.map { it.categorySlug })
        }

    @Test
    fun `editing the box drops the results it no longer describes`() =
        runTest(dispatcher) {
            val vm = viewModel()
            type(vm, "android")
            vm.submitSearch()
            advanceUntilIdle()
            vm.postResults.ids()

            type(vm, "androi")

            assertEquals(null, vm.uiState.value.submittedQuery)
            assertEquals(emptyList<Long>(), vm.postResults.ids())
        }

    /** A posts search must not spend a request for the users tab the reader may never open. */
    @Test
    fun `searching users asks the site for no posts`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.selectTarget(SearchTarget.USERS)
            type(vm, "android")

            vm.submitSearch()
            advanceUntilIdle()

            assertEquals(0, remote.searchRequests.size)
        }

    @Test
    fun `history remembers the single board the search was scoped to`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.selectBoard("tech")
            type(vm, "android")

            vm.submitSearch()
            advanceUntilIdle()

            // The DataStore write lands off the test scheduler, so the assertion waits for the
            // state to carry it rather than assuming one advanceUntilIdle covered the file.
            val entry = vm.uiState.first { it.searchHistory.isNotEmpty() }.searchHistory.single()
            assertEquals("android", entry.query)
            assertEquals("tech", entry.categorySlug)
        }
}

private object NoUsersSearchRepository : SearchRepository {
    override suspend fun searchUsers(query: String): List<UserSearchResult> = emptyList()

    override suspend fun resolveMemberUid(name: String): Long? = null
}
