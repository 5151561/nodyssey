package io.github.nodyssey.data.session

import io.github.nodyssey.core.NodeSeekSite
import io.github.plaza.core.net.SessionCookieStore
import io.github.plaza.core.net.SessionCookies
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The layer the login bug lived in.
 *
 * Cookies were arriving in the shared store the whole time; what was missing was anything that read
 * them back and noticed. These tests are about *noticing* — the generation counter the feed reloads
 * on, and the two name checks that used to be a `contains("session=")`.
 */
class SessionRepositoryTest {
    private val cookies = FakeSessionCookieStore()
    private lateinit var repository: SessionRepository

    @Before
    fun setUp() {
        repository = SessionRepository(SessionCookies(NodeSeekSite.CONFIG, cookies))
    }

    private fun setCookie(raw: String) = cookies.setCookie(NodeSeekSite.BASE_URL, raw)

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

    /**
     * The web view is allowed onto both hosts, and a sign-in finished on the bare one leaves a
     * host-only cookie there. Reading only `www` reported that reader as signed out while the page
     * in front of them said otherwise.
     */
    @Test
    fun `a session left on the bare domain is still a session`() {
        cookies.setCookie("https://nodeseek.com", "session=abc123")

        assertTrue(repository.sync().isSignedIn)
    }

    /**
     * The fingerprint is deliberately read at one origin only, so this session arrives without
     * moving it — which is precisely the case the old `if (fingerprint != …)` published nothing for.
     */
    @Test
    fun `a session that does not move the fingerprint is still published`() {
        val before = repository.state.value
        cookies.setCookie("https://nodeseek.com", "session=abc123")

        val after = repository.sync()

        assertEquals(before.fingerprint, after.fingerprint)
        assertTrue(after.isSignedIn)
        assertEquals(before.generation + 1, after.generation)
        assertTrue("the published state is what screens read", repository.state.value.isSignedIn)
    }

    /** `sync` is how callers *ask*, so it must never hand back the state it just failed to update. */
    @Test
    fun `sync answers with what the store says, not with what was last published`() {
        setCookie("session=abc123")

        assertTrue(repository.sync().isSignedIn)
        assertEquals(repository.state.value, repository.sync())
    }

    @Test
    fun `a session that only becomes visible on a later read still counts`() = runTest {
        val late = InFlightCookieStore(cookies)
        val repository = SessionRepository(SessionCookies(NodeSeekSite.CONFIG, late))
        cookies.setCookie(NodeSeekSite.BASE_URL, "session=abc123")

        // The one read a plain `sync` takes lands inside the window the write has not left.
        assertFalse("still in flight", repository.sync().isSignedIn)

        // Lands after the first settle beat and before the last.
        launch {
            delay(150)
            late.land()
        }

        assertTrue(repository.syncAwaitingSession().isSignedIn)
    }

    @Test
    fun `a session that never arrives is still reported as absent`() = runTest {
        val repository =
            SessionRepository(SessionCookies(NodeSeekSite.CONFIG, InFlightCookieStore(cookies)))
        cookies.setCookie(NodeSeekSite.BASE_URL, "session=abc123")

        // Waiting is a tolerance, not a promise that something will turn up.
        assertFalse(repository.syncAwaitingSession().isSignedIn)
    }

    /**
     * 网络自检 puts this list on a screen built to be screenshotted into a public thread. A value
     * here is the session itself — the one thing on the device worth stealing — so this is a guard,
     * not a formatting test: it fails the day someone reaches for the pairs instead of the names.
     */
    @Test
    fun `cookie names carry no values`() {
        setCookie("session=abc123")
        setCookie("cf_clearance=deadbeef")
        cookies.setCookie("https://nodeseek.com", "token=secret-value")

        val names = SessionCookies(NodeSeekSite.CONFIG, cookies).cookieNames()

        assertEquals(listOf("session", "cf_clearance", "token"), names)
        names.forEach { name ->
            assertFalse("a name may not carry its value: $name", name.contains("="))
        }
        listOf("abc123", "deadbeef", "secret-value").forEach { value ->
            assertFalse("$value reached the report", names.any { it.contains(value) })
        }
    }

    @Test
    fun `signing out clears the session and reports it`() = runTest {
        setCookie("session=abc123")
        val signedIn = repository.sync()

        repository.signOut()

        assertFalse(repository.state.value.isSignedIn)
        assertEquals(signedIn.generation + 1, repository.state.value.generation)
    }
}

/**
 * A store whose writes take a while to become readable.
 *
 * Android's `CookieManager` announces a completed write through a callback the jar does not wait on,
 * so a read taken on the next line can miss a cookie that is on its way in. This is that window,
 * held open until the test says otherwise — [land] is the moment the write becomes visible.
 *
 * Held open by hand rather than by counting reads: how many times a snapshot consults the store is
 * an implementation detail of [SessionCookies], and a test that encodes it fails the next time a
 * second origin is added to the read rather than when the behaviour it is about breaks.
 */
private class InFlightCookieStore(
    private val delegate: SessionCookieStore,
) : SessionCookieStore by delegate {
    private var landed = false

    fun land() {
        landed = true
    }

    override fun cookieHeader(url: String): String? =
        if (landed) delegate.cookieHeader(url) else null
}
