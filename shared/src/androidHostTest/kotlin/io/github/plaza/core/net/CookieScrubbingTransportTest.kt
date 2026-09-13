package io.github.plaza.core.net

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.session.FakeSessionCookieStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The cookie under test is real: NodeSeek's `sortBy`, which a signed-out request must not carry —
 * see `NodeSeekSite`'s config for the measurement. The store is the shared one, so what these pin is
 * that it is gone from the store, not merely left off one request.
 */
class CookieScrubbingTransportTest {
    private val config = NodeSeekSite.CONFIG
    private val store = FakeSessionCookieStore()
    private val cookies = SessionCookies(config, store)
    private val get = HttpRequest(url = config.baseUrl + "/?sortBy=replyTime")

    private fun header(): String = store.cookieHeader(config.baseUrl).orEmpty()

    @Test
    fun `an answer that sets the list-order cookie has it expired from the store`() = runTest {
        // What a platform store does on its own before the decorator ever sees the answer.
        store.setCookie(config.baseUrl, "session=abc")
        store.setCookie(config.baseUrl, "sortBy=replyTime")
        val transport =
            CookieScrubbingTransport(
                RecordingTransport(
                    httpResponse("<html/>", headers = mapOf("set-cookie" to "sortBy=replyTime; Max-Age=63072000; Path=/")),
                ),
                cookies,
            )

        transport.execute(get)

        assertFalse("sortBy=replyTime" in header(), "the store still replays it: ${header()}")
        assertTrue("session=abc" in header(), "the session must survive the scrub")
    }

    @Test
    fun `an answer that sets nothing leaves the store alone`() = runTest {
        store.setCookie(config.baseUrl, "sortBy=replyTime")
        val flushesBefore = store.flushes
        val transport = CookieScrubbingTransport(RecordingTransport(httpResponse("<html/>")), cookies)

        transport.execute(get)

        assertEquals(flushesBefore, store.flushes, "nothing to expire, nothing to flush")
    }

    @Test
    fun `a session sync expires what the in-app browser deposited`() {
        store.setCookie(config.baseUrl, "sortBy=postTime")
        store.setCookie(config.baseUrl, "colorscheme=dark")

        cookies.dropUnreplayable()

        assertFalse("sortBy=postTime" in header())
        assertTrue("colorscheme=dark" in header())
    }

    @Test
    fun `the header test reads names, not substrings`() {
        assertTrue(cookies.setsUnreplayable("sortBy=replyTime; Path=/"))
        assertTrue(cookies.setsUnreplayable("colorscheme=dark; Path=/,sortBy=postTime; Path=/"))
        assertFalse(cookies.setsUnreplayable("xsortBy=1; Path=/"))
        assertFalse(cookies.setsUnreplayable(null))
    }
}
