package io.github.nodyssey.data.session

import io.github.plaza.core.net.SessionCookies
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the app knows about its own session: a read model over the shared cookie store.
 *
 * Cookies *are* the session. NodeSeek has no token endpoint, and the WebView and OkHttp already read
 * the same [android.webkit.CookieManager], so there is nothing here to store — only something to
 * notice. That was the whole gap: the cookies were being collected and nobody ever looked.
 *
 * [CookieManager] has no change notification, which is why [sync] is explicit rather than a
 * background observer. Only the WebView screen calls it, and that is deliberate: a generation that
 * moves solely when the user has been through the WebView is a signal the feed can safely reload on.
 * One that moved every time Cloudflare re-issued a cookie would reload the list under the user's
 * thumb mid-scroll.
 */
class SessionRepository(
    private val cookies: SessionCookies,
) {
    private val _state = MutableStateFlow(read())

    val state: StateFlow<SessionState> = _state.asStateFlow()

    /**
     * Reads the cookie store without publishing anything.
     *
     * The WebView polls with this rather than [sync], and that distinction is load-bearing. Publishing
     * bumps [SessionState.generation], which makes the feed drop its caches and start fetching. Doing
     * that twice a second while the user is still ticking Cloudflare's checkbox aims a burst of
     * non-browser traffic at a challenge in progress — which is one way to turn a challenge that would
     * have passed into one that never does.
     */
    fun peek(): SessionState = read().copy(generation = _state.value.generation)

    /**
     * Tells the site which of its two themes this app is drawing, by writing the cookie its own front
     * end writes.
     *
     * On this layer because the cookie jar is: see [SessionCookies.applyColorScheme] for what it is
     * really for, which is not the colour. Idempotent, and cheap enough to call on every theme
     * change — the value it writes is the one the store already holds in every case but the change.
     */
    fun applyColorScheme(dark: Boolean) {
        cookies.applyColorScheme(dark)
    }

    /** Re-reads the cookie store, publishes what it says, and persists it. */
    fun sync(): SessionState {
        val snapshot = read()
        val current = _state.value
        // Anything the app can observe, not the fingerprint alone. Keying this on the fingerprint
        // was a narrower test than the value it guards: a store that starts saying signed in without
        // otherwise changing — which is what reading a second origin now makes possible, see
        // [SessionCookies.snapshot] — published nothing, and every screen keyed on `generation` went
        // on drawing a signed-out app over a signed-in jar. The returned value was stale for the
        // same reason, which is worse: this function is also how the caller *asks*.
        if (snapshot.fingerprint == current.fingerprint &&
            snapshot.isSignedIn == current.isSignedIn &&
            snapshot.hasClearance == current.hasClearance
        ) {
            return current
        }
        // Persist immediately: the cookie that just arrived is the entire point of the WebView,
        // and it usually came from an XHR, so no page load will flush it for us.
        cookies.flush()
        _state.value = snapshot.copy(generation = current.generation + 1)
        return _state.value
    }

    /**
     * [sync], for the one caller that has just been *told* a session exists.
     *
     * The store underneath is the platform's, and on Android that is `CookieManager`, whose write
     * has a completion callback the jar does not wait on — so a read taken on the next line can miss
     * a cookie that is still on its way in. Everywhere else that costs nothing, because nothing else
     * asks the question the instant a `Set-Cookie` lands. Here it decides whether the user is told
     * their sign-in did not take, and being wrong about that sends someone who *is* signed in to go
     * do it again somewhere else.
     *
     * So: ask, and let a write that is merely late catch up before believing it did not happen. A
     * tolerance, not a guarantee — the platform documents no ordering here, and this deliberately
     * does not pretend otherwise. What it does promise is that [SESSION_SETTLE_ATTEMPTS] misses in a
     * row is no longer a plausible slow write.
     */
    suspend fun syncAwaitingSession(): SessionState {
        repeat(SESSION_SETTLE_ATTEMPTS - 1) {
            val state = sync()
            if (state.isSignedIn) return state
            delay(SESSION_SETTLE_MILLIS)
        }
        return sync()
    }

    // Suspend because [SessionCookies.clearSession] only returns once the store is actually empty —
    // the [sync] on the next line is a re-read, and re-reading before the removal lands published
    // nothing (the fingerprint had not changed yet), which looked like a sign-out that did not take.
    suspend fun signOut() {
        cookies.clearSession()
        sync()
    }

    private fun read(): SessionState {
        val snapshot = cookies.snapshot()
        return SessionState(
            isSignedIn = snapshot.isSignedIn,
            hasClearance = snapshot.hasClearance,
            fingerprint = snapshot.fingerprint,
        )
    }

    private companion object {
        /** Enough that a write which is merely slow has landed; few enough to stay imperceptible. */
        const val SESSION_SETTLE_ATTEMPTS = 4

        const val SESSION_SETTLE_MILLIS = 120L
    }
}

data class SessionState(
    val isSignedIn: Boolean = false,
    val hasClearance: Boolean = false,
    /** Opaque; only equality matters. See [SessionCookies.snapshot]. */
    val fingerprint: Int = 0,
    /**
     * Bumped every time the deciding cookies change.
     *
     * Reloads key on this rather than on [isSignedIn], because clearing a Cloudflare challenge
     * changes what the site will serve without changing who we are.
     */
    val generation: Int = 0,
)
