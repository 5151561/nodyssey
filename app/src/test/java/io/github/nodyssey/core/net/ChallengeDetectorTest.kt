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

    /**
     * The level wall says 权限不足 too, which is one of the login markers — so this pins the order as
     * much as the marker: read as a login wall, it offers 登录 to a reader who is already signed in.
     */
    @Test
    fun `a level wall is its own state and carries the level`() {
        assertEquals(
            SiteError.LevelRequired(requiredLevel = 5),
            detector.detect(Fixtures.load("post-level-required.html"), 200, emptyMap()),
        )
    }

    /**
     * The captured page keeps the sentence in one text node, so this is the shape we do *not* have:
     * a re-render that wraps the number still has to classify, level or no level. Losing 「Lv5」 off
     * a title is a worse screen; falling back to the login wall would be a wrong one.
     */
    @Test
    fun `a level wall whose number is wrapped in markup classifies without one`() {
        val html =
            """
            <html><body><div id="nsk-body"><h1>查看本帖需要<b>Lv5</b>，您的权限不足😑，
            请赚取🍗升级您的用户等级</h1></div></body></html>
            """.trimIndent()
        assertEquals(SiteError.LevelRequired(requiredLevel = null), detector.detect(html, 200, emptyMap()))
    }

    /**
     * 私有 shares no phrase with either wall — 阅读权限 is not 权限不足 — so what this pins is that the
     * sentence is consulted at all, and before the usable-page markers: the captured page carries
     * `id="nsk-body"`, which classifies as real content and would send an empty thread to the parsers.
     *
     * The 404 is the live status, and passing it in is the second half of the same point: a check
     * placed after the status handling would report HTTP 404 and lose the reason.
     */
    @Test
    fun `a private thread is its own state rather than an empty page`() {
        assertEquals(
            SiteError.PrivatePost,
            detector.detect(Fixtures.load("post-private.html"), 404, emptyMap()),
        )
    }

    /** The wording is a whole clause because a thread *about* 私有 must still open. */
    @Test
    fun `a thread whose body discusses the feature is not a private thread`() {
        val html =
            """
            <html><body><div id="nsk-body"><div class="post-content">
            发帖的时候可以把阅读权限设为私有，只有自己能看
            </div></div></body></html>
            """.trimIndent()
        assertNull(detector.detect(html, 200, emptyMap()))
    }

    @Test
    fun `an unexpected status is reported as blocked`() {
        assertEquals(
            SiteError.Http(500),
            detector.detect("<html>oops</html>", 500, emptyMap()),
        )
    }
}
