package io.github.plaza.core.net

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RetryingTransportTest {
    private val page = httpResponse("<html><div id=\"nsk-body\"></div></html>")
    private val unavailable = httpResponse("<html><title>503 Service Temporarily Unavailable</title></html>", code = 503)
    private val get = HttpRequest(url = "https://www.nodeseek.com/categories/daily?sortBy=replyTime")

    private fun retrying(vararg answers: HttpResponse, retries: Int = 2): Pair<RetryingTransport, RecordingTransport> {
        val inner = RecordingTransport(*answers)
        return RetryingTransport(inner, maxRetries = retries, pauseMillis = { 0L }) to inner
    }

    @Test
    fun `a 503 that clears on the second try is never seen by the caller`() = runTest {
        val (transport, inner) = retrying(unavailable, page)

        assertEquals(page, transport.execute(get))
        assertEquals(2, inner.requests.size)
    }

    @Test
    fun `a 503 that never clears is reported after the last retry`() = runTest {
        val (transport, inner) = retrying(unavailable, unavailable, unavailable, page)

        assertEquals(503, transport.execute(get).code)
        assertEquals(3, inner.requests.size, "one try and two retries")
    }

    /** Repeating a write that may already have landed is how a comment gets posted twice. */
    @Test
    fun `a write is sent once whatever it answers`() = runTest {
        val (transport, inner) = retrying(unavailable, page)

        val post = get.copy(method = "POST", body = HttpBody.Text("{}"))
        assertEquals(503, transport.execute(post).code)
        assertEquals(1, inner.requests.size)
    }

    /** Cloudflare asking for a browser is not the edge's mood; asking again supplies no browser. */
    @Test
    fun `a challenge is not retried even on a gateway status`() = runTest {
        val challenged = unavailable.copy(headers = mapOf("cf-mitigated" to "challenge"))
        val (transport, inner) = retrying(challenged, page)

        assertEquals(503, transport.execute(get).code)
        assertEquals(1, inner.requests.size)
    }

    /** A 4xx is the site's answer, and the site does not change its mind on a repeat. */
    @Test
    fun `a refusal is not retried`() = runTest {
        val blocked = httpResponse("<html>Sorry, you have been blocked</html>", code = 403)
        val (transport, inner) = retrying(blocked, page)

        assertEquals(403, transport.execute(get).code)
        assertEquals(1, inner.requests.size)
    }
}
