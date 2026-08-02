package io.github.nodyssey.core.net

import io.github.nodyssey.core.html.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChallengeDetectorTest {

    @Test
    fun `a real page is not a challenge`() {
        assertNull(ChallengeDetector.detect(Fixtures.load("page-1.html"), 200, emptyMap()))
        assertNull(ChallengeDetector.detect(Fixtures.load("post-703863-1.html"), 200, emptyMap()))
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
        assertNull(ChallengeDetector.detect(html, 200, emptyMap()))
    }

    @Test
    fun `a cloudflare interstitial is detected`() {
        assertEquals(
            NodeSeekError.Cloudflare,
            ChallengeDetector.detect(Fixtures.load("cloudflare-challenge.html"), 403, emptyMap()),
        )
    }

    @Test
    fun `the cf-mitigated header alone is enough`() {
        assertEquals(
            NodeSeekError.Cloudflare,
            ChallengeDetector.detect("<html></html>", 200, mapOf("CF-Mitigated" to "challenge")),
        )
    }

    @Test
    fun `a plain cloudflare server header is not a challenge`() {
        assertNull(
            ChallengeDetector.detect(
                Fixtures.load("page-1.html"),
                200,
                mapOf("server" to "cloudflare"),
            ),
        )
    }

    @Test
    fun `a login wall is reported separately so the UI can offer sign-in`() {
        assertEquals(
            NodeSeekError.LoginRequired,
            ChallengeDetector.detect(Fixtures.load("post-login-required.html"), 200, emptyMap()),
        )
    }

    @Test
    fun `an unexpected status is reported as blocked`() {
        assertEquals(
            NodeSeekError.Http(500),
            ChallengeDetector.detect("<html>oops</html>", 500, emptyMap()),
        )
    }
}
