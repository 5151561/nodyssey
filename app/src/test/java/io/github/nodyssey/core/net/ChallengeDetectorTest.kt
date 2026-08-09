package io.github.nodyssey.core.net

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.html.Fixtures
import io.github.plaza.core.net.ChallengeDetector
import io.github.plaza.core.net.SiteError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The detector itself lives in `:core` and knows nothing about NodeSeek. What this pins is the pair:
 * [NodeSeekSite.CONFIG]'s markers against pages captured from the site they were read off.
 */
class ChallengeDetectorTest {

    private val detector = ChallengeDetector(NodeSeekSite.CONFIG.markers)

    @Test
    fun `a real page is not a challenge`() {
        assertNull(detector.detect(Fixtures.load("page-1.html"), 200, emptyMap()))
        assertNull(detector.detect(Fixtures.load("post-703863-1.html"), 200, emptyMap()))
    }

    /**
     * The shape `/setting` actually comes back as — captured off the device on 2026-08-02: a 200
     * carrying the bootstrap, no content markup, and Cloudflare's own script inlined into it. It used
     * to be read as a challenge, which is why 联系方式 could never show an email.
     */
    @Test
    fun `a settings page carrying the bootstrap is not a challenge`() {
        val html =
            """
            <!DOCTYPE html> <html data-server-rendered="true"><head>
            <script src="/cdn-cgi/challenge-platform/scripts/jsd/main.js"></script>
            </head><body><div id="app"></div>
            <script id="temp-script" type="text/json">eyJ1c2VyIjp7fX0=</script>
            </body></html>
            """.trimIndent()
        assertNull(detector.detect(html, 200, emptyMap()))
    }

    @Test
    fun `a cloudflare interstitial is detected`() {
        assertEquals(
            SiteError.Cloudflare,
            detector.detect(Fixtures.load("cloudflare-challenge.html"), 403, emptyMap()),
        )
    }

    @Test
    fun `the cf-mitigated header alone is enough`() {
        assertEquals(
            SiteError.Cloudflare,
            detector.detect("<html></html>", 200, mapOf("CF-Mitigated" to "challenge")),
        )
    }

    @Test
    fun `a plain cloudflare server header is not a challenge`() {
        assertNull(
            detector.detect(
                Fixtures.load("page-1.html"),
                200,
                mapOf("server" to "cloudflare"),
            ),
        )
    }

    @Test
    fun `a login wall is reported separately so the UI can offer sign-in`() {
        assertEquals(
            SiteError.LoginRequired,
            detector.detect(Fixtures.load("post-login-required.html"), 200, emptyMap()),
        )
    }

    @Test
    fun `an unexpected status is reported as blocked`() {
        assertEquals(
            SiteError.Http(500),
            detector.detect("<html>oops</html>", 500, emptyMap()),
        )
    }
}
