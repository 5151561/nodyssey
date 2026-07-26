package io.github.nsreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeSeekSiteTest {
    @Test
    fun `search paths encode query range page and sort`() {
        assertEquals(
            "/search?q=Android%20TV&page=2&category=tech&sortBy=postTime",
            NodeSeekSite.postSearchPath("Android TV", page = 2, categorySlug = "tech", sort = io.github.nsreader.model.FeedSort.POST_TIME),
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
        assertFalse(NodeSeekSite.isExternalWebUrl("file:///data/data/io.github.nsreader/file"))
        assertFalse(NodeSeekSite.isExternalWebUrl("content://example.provider/item"))
    }
}
