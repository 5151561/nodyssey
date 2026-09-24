package io.github.nodyssey.ui.tools

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.github.nodyssey.model.PostListPage
import io.github.nodyssey.model.PostSummary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AwardPagingSourceTest {
    private val source = AwardPagingSource(fetch = { page -> sitePage(page) })

    @Test
    fun `a jump into the middle reads on both ways from the page it landed on`() =
        runTest {
            val page = source.load(refresh(key = 5)) as PagingSource.LoadResult.Page

            assertEquals(4, page.prevKey)
            assertEquals(6, page.nextKey)
            assertEquals(listOf(5), page.data.map { it.page }.distinct())
        }

    @Test
    fun `nothing is asked for before page 1 or after the last page`() =
        runTest {
            val first = source.load(refresh(key = 1)) as PagingSource.LoadResult.Page
            val last = source.load(refresh(key = LAST_PAGE)) as PagingSource.LoadResult.Page

            assertNull(first.prevKey)
            assertEquals(2, first.nextKey)
            assertEquals(LAST_PAGE - 1, last.prevKey)
            assertNull(last.nextKey)
        }

    @Test
    fun `a retry comes back to the page the reader was looking at`() =
        runTest {
            val six = source.load(refresh(key = 6)) as PagingSource.LoadResult.Page
            val seven = source.load(refresh(key = 7)) as PagingSource.LoadResult.Page
            val state =
                PagingState(
                    pages = listOf(six, seven),
                    // The third row of page 7, past all of page 6.
                    anchorPosition = six.data.size + 2,
                    config = PagingConfig(pageSize = PER_PAGE),
                    leadingPlaceholderCount = 0,
                )

            assertEquals(7, source.getRefreshKey(state))
        }

    private fun refresh(key: Int) =
        PagingSource.LoadParams.Refresh(key = key, loadSize = PER_PAGE, placeholdersEnabled = false)

    private fun sitePage(page: Int) =
        PostListPage(
            posts = List(PER_PAGE) { index -> summary(page * 100L + index) },
            page = page,
            hasNextPage = page < LAST_PAGE,
            totalPages = LAST_PAGE,
        )

    private fun summary(id: Long) =
        PostSummary(
            postId = id,
            title = "thread $id",
            authorName = "author",
            authorUid = id,
            avatarUrl = null,
            categoryTitle = null,
            categorySlug = null,
            viewCount = null,
            commentCount = null,
            lastActiveText = null,
            lastActiveTitle = null,
        )

    private companion object {
        const val LAST_PAGE = 18
        const val PER_PAGE = 20
    }
}
