package io.github.nodyssey.core.net

import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.RecordingTransport
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.net.httpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the client does with an answer that is not JSON.
 *
 * The bug these hold the line against: an HTML body used to be classified as a Cloudflare challenge
 * outright, on the theory that only Cloudflare could be intercepting the call. NodeSeek's front end
 * is a single-page app, so an `/api` path its router does not recognise is answered with
 * `index.html` — the home page — and every screen that called it put 需要验证 over content that was
 * never behind a wall. 验证 then opened a web view on a site with no challenge in it, which showed
 * the reader the home page. 首页, 我的 and 通知 all did this at once, because all three ask an
 * endpoint through here.
 */
class NodeSeekJsonClientTest {
    private val dispatchers =
        AppDispatchers(io = Dispatchers.Unconfined, default = Dispatchers.Unconfined)

    private fun client(vararg answers: io.github.plaza.core.net.HttpResponse) =
        NodeSeekJsonClient(RecordingTransport(*answers), dispatchers)

    @Test
    fun `passes a JSON answer through`() = runTest {
        val body = client(httpResponse("""{"success":true}""")).getJson(PATH)

        assertEquals("""{"success":true}""", body)
    }

    @Test
    fun `the tagged header is a challenge`() = runTest {
        val error = errorFrom(
            httpResponse(
                body = """{"success":false}""",
                code = 403,
                headers = mapOf("cf-mitigated" to "challenge"),
            ),
        )

        assertTrue(error.error is SiteError.Cloudflare)
    }

    /** The regression: the site's own shell, on a 200, with nothing of Cloudflare's about it. */
    @Test
    fun `the single-page shell is reported as itself rather than as a wall`() = runTest {
        val error = errorFrom(
            httpResponse("""<!DOCTYPE html><html><body><div id="nsk-body"></div></body></html>"""),
        )

        assertEquals(SiteError.Unparsable, error.error)
        assertTrue(error.detail?.contains(PATH) == true, "the endpoint is the diagnosis")
    }

    /**
     * The second half of the same bug, and the one that survived the first fix: Cloudflare injects
     * `/cdn-cgi/challenge-platform/` into ordinary 200s wherever JS detection is on, so the challenge
     * markers match pages that are not challenges. The site's own markers have to win — which is the
     * order `ChallengeDetector` has always used on the HTML path.
     */
    @Test
    fun `a site page carrying Cloudflare's detection script is still not a challenge`() = runTest {
        val error = errorFrom(
            httpResponse(
                """
                <!DOCTYPE html><html><head><title>NodeSeek</title>
                <script src="/cdn-cgi/challenge-platform/scripts/jsd/main.js"></script>
                </head><body><div id="nsk-body"></div></body></html>
                """.trimIndent(),
            ),
        )

        assertEquals(SiteError.Unparsable, error.error)
        assertTrue(error.detail?.contains("NodeSeek") == true, "the page names itself")
    }

    /**
     * The page the guard cannot save, because there is nothing of the site's in it: the origin's own
     * 503, which Cloudflare proxies with its detection script injected like any other body. Only a
     * marker list that names the interstitial's own paths tells the two apart — and a 503 is then
     * the status the caller should hear, with a 重试 that can actually work.
     */
    @Test
    fun `an origin 503 carrying the detection script is a 503 and not a challenge`() = runTest {
        val error = errorFrom(
            httpResponse(
                body = """<html><head><title>503 Service Temporarily Unavailable</title></head>
                    <body><center>nginx</center>
                    <script>a.src='/cdn-cgi/challenge-platform/scripts/jsd/main.js';</script></body></html>""",
                code = 503,
            ),
        )

        assertEquals(SiteError.Http(503), error.error)
    }

    /** The real challenge has no site markers in it, so the guard never reaches it. */
    @Test
    fun `the challenge page is still a challenge with the guard in place`() = runTest {
        val error = errorFrom(
            httpResponse(
                body = """<html><head><title>Just a moment...</title></head>
                    <script src="/cdn-cgi/challenge-platform/h/b.js"></script></html>""",
                code = 403,
            ),
        )

        assertTrue(error.error is SiteError.Cloudflare)
    }

    private suspend fun errorFrom(answer: io.github.plaza.core.net.HttpResponse): SiteException =
        try {
            client(answer).getJson(PATH)
            throw AssertionError("expected the call to fail")
        } catch (exception: SiteException) {
            exception
        }

    private companion object {
        /** Not one of the session-scoped families, whose 500 means something else entirely. */
        const val PATH = "/api/content/list-categories"
    }
}
