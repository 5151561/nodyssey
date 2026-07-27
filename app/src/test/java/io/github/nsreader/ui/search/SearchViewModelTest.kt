package io.github.nsreader.ui.search

import io.github.nsreader.core.net.JsonSource
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.data.CategoryRepository
import io.github.nsreader.data.MutableClock
import io.github.nsreader.data.PostSearchResults
import io.github.nsreader.data.SearchRepository
import io.github.nsreader.data.UserSearchResult
import io.github.nsreader.data.inMemoryDatabase
import io.github.nsreader.data.local.NodeSeekDatabase
import io.github.nsreader.data.testSettingsRepository
import io.github.nsreader.model.PostSummary
import io.github.nsreader.model.SearchSort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var database: NodeSeekDatabase

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

    @Test
    fun `append scans consecutive duplicate-only pages until it finds a new post`() =
        runTest(dispatcher) {
            val repository = FakeSearchRepository { page ->
                when (page) {
                    1 -> page(page = 1, ids = listOf(1), totalPages = 4)
                    2, 3 -> page(page = page, ids = listOf(1), totalPages = 4)
                    else -> page(page = 4, ids = listOf(4), totalPages = 4, hasNext = false)
                }
            }
            val viewModel = viewModel(repository)

            viewModel.search("paging")
            advanceUntilIdle()
            viewModel.loadMorePosts()
            advanceUntilIdle()

            assertEquals(listOf(1, 2, 3, 4), repository.requestedPages)
            assertEquals(listOf(1L, 4L), viewModel.uiState.value.postResults.map { it.summary.postId })
            assertEquals(4, viewModel.uiState.value.postPage)
            assertEquals(4, viewModel.uiState.value.postTotalPages)
            assertFalse(viewModel.uiState.value.postHasNext)
        }

    @Test
    fun `one append is capped after three duplicate-only pages`() =
        runTest(dispatcher) {
            val repository = FakeSearchRepository { page -> page(page, listOf(1), totalPages = 20) }
            val viewModel = viewModel(repository)

            viewModel.search("bounded")
            advanceUntilIdle()
            viewModel.loadMorePosts()
            advanceUntilIdle()

            assertEquals(listOf(1, 2, 3, 4), repository.requestedPages)
            assertEquals(listOf(1L), viewModel.uiState.value.postResults.map { it.summary.postId })
            assertEquals(4, viewModel.uiState.value.postPage)
            assertTrue(viewModel.uiState.value.postHasNext)
            assertFalse(viewModel.uiState.value.isAppendingPosts)
        }

    @Test
    fun `append failure keeps loaded rows and exposes a retry state`() =
        runTest(dispatcher) {
            var failPageTwo = true
            val repository = FakeSearchRepository { page ->
                when {
                    page == 1 -> page(page = 1, ids = listOf(1), totalPages = 2)
                    failPageTwo -> throw NodeSeekException(NodeSeekError.Network)
                    else -> page(page = 2, ids = listOf(2), totalPages = 2, hasNext = false)
                }
            }
            val viewModel = viewModel(repository)

            viewModel.search("retry")
            advanceUntilIdle()
            viewModel.loadMorePosts()
            advanceUntilIdle()

            assertEquals(listOf(1L), viewModel.uiState.value.postResults.map { it.summary.postId })
            assertTrue(viewModel.uiState.value.postAppendFailed)
            assertEquals(SearchLoadState.Success, viewModel.uiState.value.postLoadState)

            failPageTwo = false
            viewModel.loadMorePosts()
            advanceUntilIdle()

            assertEquals(listOf(1L, 2L), viewModel.uiState.value.postResults.map { it.summary.postId })
            assertFalse(viewModel.uiState.value.postAppendFailed)
        }

    private fun TestScope.viewModel(repository: SearchRepository): SearchViewModel =
        SearchViewModel(
            searchRepository = repository,
            categoryRepository =
            CategoryRepository(
                client = FailingJsonSource,
                boardDao = database.boardDao(),
                clock = MutableClock(),
            ),
            settings = testSettingsRepository(backgroundScope),
        )

    private fun SearchViewModel.search(query: String) {
        updateQuery(query)
        submitSearch()
    }
}

private class FakeSearchRepository(
    private val result: (Int) -> PostSearchResults,
) : SearchRepository {
    val requestedPages = mutableListOf<Int>()

    override suspend fun searchPosts(
        query: String,
        page: Int,
        categorySlugs: Set<String>,
        sort: SearchSort,
    ): PostSearchResults {
        requestedPages += page
        return result(page)
    }

    override suspend fun searchUsers(query: String): List<UserSearchResult> = emptyList()
}

private object FailingJsonSource : JsonSource {
    override suspend fun getJson(
        path: String,
        referer: String,
    ): String = throw NodeSeekException(NodeSeekError.Network)
}

private fun page(
    page: Int,
    ids: List<Long>,
    totalPages: Int,
    hasNext: Boolean = page < totalPages,
): PostSearchResults =
    PostSearchResults(
        posts = ids.map(::post),
        page = page,
        totalPages = totalPages,
        hasNextPage = hasNext,
    )

private fun post(id: Long): PostSummary =
    PostSummary(
        postId = id,
        title = "post $id",
        authorName = "tester",
        authorUid = id,
        avatarUrl = null,
        categoryTitle = "技术",
        categorySlug = "tech",
        viewCount = null,
        commentCount = null,
        lastActiveText = null,
        lastActiveTitle = null,
    )
