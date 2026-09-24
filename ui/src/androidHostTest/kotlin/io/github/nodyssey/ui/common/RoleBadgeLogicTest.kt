package io.github.nodyssey.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class RoleBadgeLogicTest {

    @Test
    fun `truncation folds the tail into a count but never drops a punishment badge`() {
        val (shown, folded) = visibleRoleBadges(listOf("服主", "管理", "Dev", "骗子"))
        assertEquals(listOf("骗子", "服主", "管理"), shown)
        assertEquals(1, folded)
    }
}
