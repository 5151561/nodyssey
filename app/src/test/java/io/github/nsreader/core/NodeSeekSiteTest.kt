package io.github.nsreader.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeSeekSiteTest {
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
