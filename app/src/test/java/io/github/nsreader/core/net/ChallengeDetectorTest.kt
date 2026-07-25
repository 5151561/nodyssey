package io.github.nsreader.core.net

import io.github.nsreader.core.html.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChallengeDetectorTest {

    @Test
    fun `a real page is not a challenge`() {
        assertNull(ChallengeDetector.detect(Fixtures.load("page-1.html"), 200, emptyMap()))
        assertNull(ChallengeDetector.detect(Fixtures.load("post-703863-1.html"), 200, emptyMap()))
    }

    @Test
    fun `a cloudflare interstitial is detected`() {
        assertEquals(
            ChallengeDetector.Challenge.Cloudflare,
            ChallengeDetector.detect(Fixtures.load("cloudflare-challenge.html"), 403, emptyMap()),
        )
    }

    @Test
    fun `the cf-mitigated header alone is enough`() {
        assertEquals(
            ChallengeDetector.Challenge.Cloudflare,
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
            ChallengeDetector.Challenge.LoginRequired,
            ChallengeDetector.detect(Fixtures.load("post-login-required.html"), 200, emptyMap()),
        )
    }

    @Test
    fun `an unexpected status is reported as blocked`() {
        assertEquals(
            ChallengeDetector.Challenge.Blocked(500),
            ChallengeDetector.detect("<html>oops</html>", 500, emptyMap()),
        )
    }
}
