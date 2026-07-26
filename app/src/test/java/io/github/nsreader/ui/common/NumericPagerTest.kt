package io.github.nsreader.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pager has to stay one row wide at 104 pages while always offering page 1, the neighbours of the
 * current page, and the last page. `null` is the ellipsis.
 */
class NumericPagerTest {
    @Test
    fun `shows every page when they fit`() {
        assertEquals(listOf(1, 2, 3, 4, 5), pageWindow(page = 2, totalPages = 5))
    }

    @Test
    fun `collapses the middle on the first page`() {
        assertEquals(listOf(1, 2, 3, null, 104), pageWindow(page = 1, totalPages = 104))
    }

    @Test
    fun `keeps the head, the neighbours and the tail`() {
        assertEquals(listOf(1, 2, 3, null, 49, 50, 51, null, 104), pageWindow(page = 50, totalPages = 104))
    }

    @Test
    fun `has no gap when the current page adjoins the head`() {
        assertEquals(listOf(1, 2, 3, 4, null, 104), pageWindow(page = 3, totalPages = 104))
    }

    @Test
    fun `has no trailing gap on the last page`() {
        assertEquals(listOf(1, 2, 3, null, 103, 104), pageWindow(page = 104, totalPages = 104))
    }

    /** A page number from a restored state can be out of range; it must not drop the pager's tail. */
    @Test
    fun `clamps a page outside the range`() {
        assertEquals(listOf(1, 2, 3, null, 17, 18), pageWindow(page = 999, totalPages = 18))
    }
}
