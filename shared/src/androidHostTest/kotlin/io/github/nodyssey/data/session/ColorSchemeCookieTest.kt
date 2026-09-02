package io.github.nodyssey.data.session

import io.github.nodyssey.core.NodeSeekSite
import io.github.plaza.core.net.SessionCookies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cookie that is not about colour.
 *
 * NodeSeek's account endpoint drops `readme` from its answer for any request that does not carry a
 * `colorscheme` cookie — every other field arrives, so nothing fails, and every profile in the app
 * renders the site's own 「没有找到readme」empty state instead. The iOS shell did exactly that on a
 * fresh install, where the cookie jar starts empty; Android hid it, because the sign-in WebView's jar
 * is the same jar and the site's front end writes this cookie itself.
 *
 * So these tests are about a cookie *existing*, and only secondly about it saying the right thing.
 */
class ColorSchemeCookieTest {
    private val store = FakeSessionCookieStore()
    private val cookies = SessionCookies(NodeSeekSite.CONFIG, store)

    private fun cookieValue(name: String): String? =
        store
            .cookieHeader(NodeSeekSite.BASE_URL)
            .orEmpty()
            .split(';')
            .map { it.trim() }
            .firstOrNull { it.substringBefore('=') == name }
            ?.substringAfter('=')

    @Test
    fun `a light theme writes the value the site's own switch writes`() {
        cookies.applyColorScheme(dark = false)

        assertEquals("light", cookieValue("colorscheme"))
    }

    @Test
    fun `a dark theme writes the other one`() {
        cookies.applyColorScheme(dark = true)

        assertEquals("dark", cookieValue("colorscheme"))
    }

    /** Switching themes replaces the cookie; two of them would be two answers to one question. */
    @Test
    fun `switching the theme rewrites the same cookie`() {
        cookies.applyColorScheme(dark = false)
        cookies.applyColorScheme(dark = true)

        assertEquals("dark", cookieValue("colorscheme"))
        assertEquals(listOf("colorscheme"), cookies.cookieNames())
    }

    /**
     * The write is flushed by the class rather than by its caller: on Android the store batches, and
     * the request this cookie exists for can be the next thing a cold start does.
     */
    @Test
    fun `the write is flushed`() {
        cookies.applyColorScheme(dark = false)

        assertTrue(store.flushes > 0)
    }

    /**
     * A theme switch is not news about the session. If it moved the fingerprint, the next `sync` —
     * which the sign-in WebView calls — would bump the generation and drop every cached list on the
     * way back, for a change the site knows nothing about.
     */
    @Test
    fun `the theme cookie does not move the fingerprint`() {
        store.setCookie(NodeSeekSite.BASE_URL, "session=abc123")
        val before = cookies.snapshot()

        cookies.applyColorScheme(dark = true)

        val after = cookies.snapshot()
        assertEquals(before.fingerprint, after.fingerprint)
        assertTrue(after.isSignedIn)
    }

    /** A site that has no such cookie is not given one. */
    @Test
    fun `a site without a theme cookie writes nothing`() {
        val plain = SessionCookies(NodeSeekSite.CONFIG.copy(colorSchemeCookie = null), store)

        plain.applyColorScheme(dark = true)

        assertNull(store.cookieHeader(NodeSeekSite.BASE_URL))
    }
}
