package io.github.nodyssey.ui.space

import androidx.paging.PagingSource
import io.github.nodyssey.data.SpacePage
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserSpacePagingSourceTest {
    @Test
    fun `page keys follow the repository response`() =
        runTest {
            val requested = mutableListOf<Int>()
            val source =
                UserSpacePagingSource { page ->
                    requested += page
                    SpacePage(items = listOf("item-$page"), page = page, hasNextPage = page < 2)
                }

            val first = source.load(refresh()) as PagingSource.LoadResult.Page
            val second = source.load(append(first.nextKey!!)) as PagingSource.LoadResult.Page

            assertEquals(listOf(1, 2), requested)
            assertEquals(listOf("item-1"), first.data)
            assertEquals(2, first.nextKey)
            assertEquals(listOf("item-2"), second.data)
            assertEquals(null, second.nextKey)
        }

    @Test
    fun `repository failure is exposed as paging error`() =
        runTest {
            val source =
                UserSpacePagingSource<String> {
                    throw SiteException(SiteError.Network)
                }

            assertTrue(source.load(refresh()) is PagingSource.LoadResult.Error)
        }

    private fun refresh() =
        PagingSource.LoadParams.Refresh<Int>(key = null, loadSize = 20, placeholdersEnabled = false)

    private fun append(page: Int) =
        PagingSource.LoadParams.Append(key = page, loadSize = 20, placeholdersEnabled = false)
}
