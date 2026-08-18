package io.github.nodyssey.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NodeSeekSiteTest {
    @Test
    fun `search paths encode query range page and sort`() {
        assertEquals(
            "/search?q=Android%20TV&page=2&category=tech&sortBy=postTime",
            NodeSeekSite.postSearchPath("Android TV", page = 2, categorySlug = "tech", sort = io.github.nodyssey.model.FeedSort.POST_TIME),
        )
        assertEquals("/member?q=%E8%8A%B1%E7%94%B0", NodeSeekSite.userSearchPath("花田"))
        assertEquals("/api/account/find/%E8%8A%B1%E7%94%B0", NodeSeekSite.userSearchApiPath("花田"))
    }

    @Test
    fun `authentication WebView accepts only HTTPS NodeSeek hosts`() {
        assertTrue(NodeSeekSite.isTrustedWebViewUrl("https://www.nodeseek.com/signIn.html"))
        assertTrue(NodeSeekSite.isTrustedWebViewUrl("https://nodeseek.com/"))

        assertFalse(NodeSeekSite.isTrustedWebViewUrl("http://www.nodeseek.com/signIn.html"))
        assertFalse(NodeSeekSite.isTrustedWebViewUrl("https://www.nodeseek.com.evil.example/"))
        assertFalse(NodeSeekSite.isTrustedWebViewUrl("https://www.nodeseek.com@evil.example/"))
        assertFalse(NodeSeekSite.isTrustedWebViewUrl("javascript:alert(1)"))
        assertFalse(NodeSeekSite.isTrustedWebViewUrl("content://www.nodeseek.com/private"))
    }

    @Test
    fun `ordinary web links can leave the app but non-web schemes cannot`() {
        assertTrue(NodeSeekSite.isExternalWebUrl("https://example.com/article"))
        assertTrue(NodeSeekSite.isExternalWebUrl("http://legacy.example.com/article"))

        assertFalse(NodeSeekSite.isExternalWebUrl("javascript:alert(1)"))
        assertFalse(NodeSeekSite.isExternalWebUrl("file:///data/data/io.github.nodyssey/file"))
        assertFalse(NodeSeekSite.isExternalWebUrl("content://example.provider/item"))
    }

    /**
     * `rank² × 100`, read off the site's own `/progress` bundle — the 400 that used to be treated as
     * the only published threshold is just this at Lv1.
     */
    @Test
    fun `level spans follow the site's published squares`() {
        assertEquals(LevelSpan(barRank = 1, floor = 100, next = 400), NodeSeekSite.levelChickenSpan(1))
        assertEquals(LevelSpan(barRank = 2, floor = 400, next = 900), NodeSeekSite.levelChickenSpan(2))
        assertEquals(LevelSpan(barRank = 3, floor = 900, next = 1600), NodeSeekSite.levelChickenSpan(3))
        assertEquals(LevelSpan(barRank = 4, floor = 1600, next = 2500), NodeSeekSite.levelChickenSpan(4))
    }

    /**
     * A floor is the only address a notification gives, so the page has to come out of the number.
     * Ten floors a page, #0 being the opening post and #1 the first floor under it.
     */
    @Test
    fun `a floor resolves to the page it is rendered on`() {
        assertEquals(1, NodeSeekSite.pageOfFloor(0))
        assertEquals(1, NodeSeekSite.pageOfFloor(1))
        assertEquals(1, NodeSeekSite.pageOfFloor(10))
        assertEquals(2, NodeSeekSite.pageOfFloor(11))
        assertEquals(13, NodeSeekSite.pageOfFloor(127))
    }

    @Test
    fun `floor labels are read with or without the site's hash`() {
        assertEquals(127, NodeSeekSite.parseFloorNumber("#127"))
        assertEquals(127, NodeSeekSite.parseFloorNumber(" 127 "))
        assertNull(NodeSeekSite.parseFloorNumber(null))
        assertNull(NodeSeekSite.parseFloorNumber("#"))
        assertNull(NodeSeekSite.parseFloorNumber("楼主"))
    }

    /** The site clamps its bar at Lv5 (`Math.min(user.rank, 5)`); nothing beyond it is published. */
    @Test
    fun `level spans stop advancing past Lv5`() {
        val fifth = LevelSpan(barRank = 5, floor = 2500, next = 3600)
        assertEquals(fifth, NodeSeekSite.levelChickenSpan(5))
        assertEquals(fifth, NodeSeekSite.levelChickenSpan(6))
        assertEquals(fifth, NodeSeekSite.levelChickenSpan(99))
    }
}
