package io.github.nodyssey.ui.search

import androidx.paging.PagingSource
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException
import io.github.nodyssey.data.PostSearchResults
import io.github.nodyssey.data.SearchRepository
import io.github.nodyssey.data.UserSearchResult
import io.github.nodyssey.model.PostSummary
import io.github.nodyssey.model.SearchSort
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchViewModelTest {
    @Test
    fun `append scans consecutive duplicate-only pages until it finds a new post`() =
        runTest {
            val repository = FakeSearchRepository { page ->
                when (page) {
                    1 -> page(page = 1, ids = listOf(1), totalPages = 4)
                    2, 3 -> page(page = page, ids = listOf(1), totalPages = 4)
                    else -> page(page = 4, ids = listOf(4), totalPages = 4, hasNext = false)
                }
            }
            val source = pagingSource(repository)

            val first = source.load(refresh()) as PagingSource.LoadResult.Page
            val second = source.load(append(2)) as PagingSource.LoadResult.Page

            assertEquals(listOf(1, 2, 3, 4), repository.requestedPages)
            assertEquals(listOf(1L), first.data.map { it.summary.postId })
            assertEquals(listOf(4L), second.data.map { it.summary.postId })
            assertEquals(null, second.nextKey)
        }

    @Test
    fun `one append is capped after three duplicate-only pages`() =
        runTest {
            val repository = FakeSearchRepository { page -> page(page, listOf(1), totalPages = 20) }
            val source = pagingSource(repository)

            source.load(refresh())
            val append = source.load(append(2)) as PagingSource.LoadResult.Page

            assertEquals(listOf(1, 2, 3, 4), repository.requestedPages)
            assertTrue(append.data.isEmpty())
            assertEquals(5, append.nextKey)
        }

    @Test
    fun `append failure keeps loaded rows and exposes a retry state`() =
        runTest {
            var failPageTwo = true
            val repository = FakeSearchRepository { page ->
                when {
                    page == 1 -> page(page = 1, ids = listOf(1), totalPages = 2)
                    failPageTwo -> throw NodeSeekException(NodeSeekError.Network)
                    else -> page(page = 2, ids = listOf(2), totalPages = 2, hasNext = false)
                }
            }
            val source = pagingSource(repository)

            val first = source.load(refresh()) as PagingSource.LoadResult.Page
            val failure = source.load(append(2))

            assertEquals(listOf(1L), first.data.map { it.summary.postId })
            assertTrue(failure is PagingSource.LoadResult.Error)

            failPageTwo = false
            val retry = source.load(append(2)) as PagingSource.LoadResult.Page

            assertEquals(listOf(2L), retry.data.map { it.summary.postId })
            assertEquals(null, retry.nextKey)
        }

    private fun pagingSource(repository: SearchRepository) =
        SearchPostsPagingSource(
            repository = repository,
            request = PostSearchRequest("paging", emptySet(), SearchSort.RELEVANCE, generation = 1),
        )

    private fun refresh() =
        PagingSource.LoadParams.Refresh<Int>(key = null, loadSize = 20, placeholdersEnabled = false)

    private fun append(page: Int) =
        PagingSource.LoadParams.Append(key = page, loadSize = 20, placeholdersEnabled = false)
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
