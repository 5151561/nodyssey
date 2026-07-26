package io.github.nsreader.data.session

import android.webkit.CookieManager
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.net.WebViewCookieJar
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The layer the login bug lived in.
 *
 * Cookies were arriving in the shared store the whole time; what was missing was anything that read
 * them back and noticed. These tests are about *noticing* — the generation counter the feed reloads
 * on, and the two name checks that used to be a `contains("session=")`.
 */
@RunWith(RobolectricTestRunner::class)
class SessionRepositoryTest {
    private val cookieManager = CookieManager.getInstance()
    private lateinit var repository: SessionRepository

    @Before
    fun setUp() {
        cookieManager.removeAllCookies(null)
        repository = SessionRepository(WebViewCookieJar(cookieManager))
    }

    @After
    fun tearDown() {
        cookieManager.removeAllCookies(null)
    }

    private fun setCookie(raw: String) = cookieManager.setCookie(NodeSeekSite.BASE_URL, raw)

    @Test
    fun `starts signed out when the store is empty`() {
        assertFalse(repository.state.value.isSignedIn)
        assertFalse(repository.state.value.hasClearance)
        assertEquals(0, repository.state.value.generation)
    }

    @Test
    fun `notices the session cookie the WebView collected`() {
        setCookie("session=abc123")

        val state = repository.sync()

        assertTrue(state.isSignedIn)
        assertEquals(1, state.generation)
        assertEquals(state, repository.state.value)
    }

    /** The reload signal has to be idempotent, because the WebView polls twice a second. */
    @Test
    fun `syncing an unchanged store does not bump the generation`() {
        setCookie("session=abc123")
        val first = repository.sync()

        val second = repository.sync()

        assertEquals(first.generation, second.generation)
    }

    /**
     * Clearing a challenge is a session change even though nobody signed in: the site will answer
     * differently, which is exactly what the feed needs to know.
     */
    @Test
    fun `a renewed clearance cookie counts as a change`() {
        setCookie("cf_clearance=first")
        val before = repository.sync()
        assertTrue(before.hasClearance)

        setCookie("cf_clearance=second")
        val after = repository.sync()

        assertEquals(before.generation + 1, after.generation)
    }

    /** `contains("session=")` also matched this, and reported a signed-in user who was not one. */
    @Test
    fun `a cookie whose name merely ends in session is not a session`() {
        setCookie("xsession=abc123")

        assertFalse(repository.sync().isSignedIn)
    }

    @Test
    fun `an empty session cookie is a signed-out user`() {
        setCookie("session=abc123")
        assertTrue(repository.sync().isSignedIn)

        // How a sign-out looks on the wire.
        setCookie("session=")

        assertFalse(repository.sync().isSignedIn)
    }

    @Test
    fun `cookies Cloudflare rotates on its own do not move the generation`() {
        setCookie("session=abc123")
        val before = repository.sync()

        // These rotate on Cloudflare's schedule. Reloading the list on them would yank the feed out
        // from under a scrolling user for no reason they could see.
        setCookie("__cf_bm=noise")
        setCookie("_cfuvid=noise")

        assertEquals(before.generation, repository.sync().generation)
    }

    /**
     * The regression that broke login: a live challenge rewrites `cf_chl_*` on every tick. Counting
     * those as session changes made the feed drop its caches and fetch while the user was still
     * ticking the checkbox — requests aimed at Cloudflare at the worst possible moment.
     */
    @Test
    fun `a challenge in progress does not look like a session change`() {
        val before = repository.sync()

        setCookie("cf_chl_rc_m=1")
        setCookie("cf_chl_seq_abc=2")
        setCookie("__cf_bm=tick")

        assertEquals(before.generation, repository.sync().generation)
    }

    /** Clearing the challenge, on the other hand, is exactly what we are waiting for. */
    @Test
    fun `the clearance cookie is not treated as challenge noise`() {
        val before = repository.sync()

        setCookie("cf_clearance=solved")
        val after = repository.sync()

        assertTrue(after.hasClearance)
        assertEquals(before.generation + 1, after.generation)
    }

    /**
     * `peek` is what the WebView polls with. It must see the new cookie and still publish nothing, or
     * the feed starts fetching mid-challenge.
     */
    @Test
    fun `peek observes without publishing`() {
        val before = repository.state.value

        setCookie("session=abc123")
        val peeked = repository.peek()

        assertTrue("peek should see the cookie", peeked.isSignedIn)
        assertTrue("peek should see a new fingerprint", peeked.fingerprint != before.fingerprint)
        // Nothing downstream has been told anything.
        assertEquals(before, repository.state.value)
        assertEquals(before.generation, peeked.generation)
    }

    /**
     * The name allowlist is a guess about NodeSeek, so the signal that *reloads content* must not
     * depend on it. Get the name wrong and `isSignedIn` is wrong; the feed still refreshes.
     */
    @Test
    fun `a session cookie under an unexpected name still reports a change`() {
        val before = repository.sync()

        setCookie("ns_auth=abc123")
        val after = repository.sync()

        assertEquals(before.generation + 1, after.generation)
        // Honest about what it does not know.
        assertFalse(after.isSignedIn)
    }

    @Test
    fun `signing out clears the session and reports it`() {
        setCookie("session=abc123")
        val signedIn = repository.sync()

        repository.signOut()

        assertFalse(repository.state.value.isSignedIn)
        assertEquals(signedIn.generation + 1, repository.state.value.generation)
    }
}
