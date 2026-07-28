package io.github.nodyssey.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class RoleBadgeLogicTest {

    @Test
    fun `the six documented labels map to their styles and the rest fall back`() {
        assertEquals(RoleBadgeStyle.ORIGINAL_POSTER, roleBadgeStyleOf("楼主"))
        assertEquals(RoleBadgeStyle.STAFF, roleBadgeStyleOf("服主"))
        assertEquals(RoleBadgeStyle.STAFF, roleBadgeStyleOf("管理"))
        assertEquals(RoleBadgeStyle.RETIRED, roleBadgeStyleOf("管理(退休)"))
        // Full-width parentheses are what a Chinese page is likely to ship.
        assertEquals(RoleBadgeStyle.RETIRED, roleBadgeStyleOf("管理（退休）"))
        assertEquals(RoleBadgeStyle.BANNED, roleBadgeStyleOf("违规禁止"))
        assertEquals(RoleBadgeStyle.SCAMMER, roleBadgeStyleOf("骗子"))
        // The site ships badges outside the documented six (`Dev` is real) — they must not crash.
        assertEquals(RoleBadgeStyle.NEUTRAL, roleBadgeStyleOf("Dev"))
    }

    @Test
    fun `equal-rank badges keep the site's order`() {
        val (shown, folded) = visibleRoleBadges(listOf("楼主", "服主", "管理"))
        assertEquals(listOf("楼主", "服主", "管理"), shown)
        assertEquals(0, folded)
    }

    @Test
    fun `truncation folds the tail into a count but never drops a punishment badge`() {
        val (shown, folded) = visibleRoleBadges(listOf("服主", "管理", "Dev", "骗子"))
        assertEquals(listOf("骗子", "服主", "管理"), shown)
        assertEquals(1, folded)
    }
}
