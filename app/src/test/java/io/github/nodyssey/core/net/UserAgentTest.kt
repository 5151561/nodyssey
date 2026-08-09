package io.github.nodyssey.core.net

import android.content.Context
import android.webkit.WebSettings
import androidx.test.core.app.ApplicationProvider
import io.github.nodyssey.core.NodeSeekSite
import io.github.plaza.core.net.resolveUserAgent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The UA is the fix for the infinite Cloudflare challenge, so what it must never do again is *lie*.
 *
 * A hardcoded "Chrome 126 on a Pixel 8" contradicted the UA client hints the WebView keeps sending
 * from its real Chromium version, and a managed challenge answers a contradiction with another
 * challenge. These tests pin the two properties that matter: it comes from the WebView, and the WebView
 * is then left alone.
 */
@RunWith(RobolectricTestRunner::class)
class UserAgentTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `reads the user agent off the WebView`() {
        val resolved = resolveUserAgent(context, NodeSeekSite.CONFIG)

        assertEquals(WebSettings.getDefaultUserAgent(context), resolved.value)
    }

    /**
     * `setUserAgentString` marks the UA overridden in Chromium, which changes what it reports through
     * client hints. Setting it to the value it already had is therefore not a no-op — so the normal
     * path must not set it at all.
     */
    @Test
    fun `the WebView needs no override when the UA came from it`() {
        assertTrue(resolveUserAgent(context, NodeSeekSite.CONFIG).isWebViewDefault)
    }

    /** The spoof is gone from the normal path; the hardcoded string is a last resort and nothing else. */
    @Test
    fun `does not send the hardcoded fallback when a WebView can be asked`() {
        assertNotEquals(NodeSeekSite.FALLBACK_USER_AGENT, resolveUserAgent(context, NodeSeekSite.CONFIG).value)
    }
}
