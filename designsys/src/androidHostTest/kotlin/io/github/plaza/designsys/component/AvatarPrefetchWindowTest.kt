package io.github.plaza.designsys.component

import org.junit.Assert.assertEquals
import org.junit.Test

/** The arithmetic behind [PrefetchAvatars]: which rows are warmed, and where it stops. */
class AvatarPrefetchWindowTest {
    @Test
    fun `warms the rows after the fold, not the ones already on screen`() {
        assertEquals(8..17, avatarPrefetchWindow(lastVisibleIndex = 7, itemCount = 50, rowsAhead = 10))
    }

    @Test
    fun `stops at the last loaded row rather than running past it`() {
        assertEquals(46..49, avatarPrefetchWindow(lastVisibleIndex = 45, itemCount = 50, rowsAhead = 10))
    }

    /** The reader is at the end of what is loaded — there is nothing ahead to warm. */
    @Test
    fun `nothing to warm once the last row is visible`() {
        assertEquals(true, avatarPrefetchWindow(lastVisibleIndex = 49, itemCount = 50, rowsAhead = 10).isEmpty())
    }
}
