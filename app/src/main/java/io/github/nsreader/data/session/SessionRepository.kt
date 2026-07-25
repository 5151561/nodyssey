package io.github.nsreader.data.session

import io.github.nsreader.core.net.WebViewCookieJar
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
    private val cookieJar: WebViewCookieJar,
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

    /** Re-reads the cookie store, publishes what it says, and persists it. */
    fun sync(): SessionState {
        val snapshot = read()
        val current = _state.value
        if (snapshot.fingerprint != current.fingerprint) {
            // Persist immediately: the cookie that just arrived is the entire point of the WebView,
            // and it usually came from an XHR, so no page load will flush it for us.
            cookieJar.flush()
            _state.value = snapshot.copy(generation = current.generation + 1)
        }
        return _state.value
    }

    fun signOut() {
        cookieJar.clearSession()
        sync()
    }

    private fun read(): SessionState {
        val snapshot = cookieJar.snapshot()
        return SessionState(
            isSignedIn = snapshot.isSignedIn,
            hasClearance = snapshot.hasClearance,
            fingerprint = snapshot.fingerprint,
        )
    }
}

data class SessionState(
    val isSignedIn: Boolean = false,
    val hasClearance: Boolean = false,
    /** Opaque; only equality matters. See [WebViewCookieJar.snapshot]. */
    val fingerprint: Int = 0,
    /**
     * Bumped every time the deciding cookies change.
     *
     * Reloads key on this rather than on [isSignedIn], because clearing a Cloudflare challenge
     * changes what the site will serve without changing who we are.
     */
    val generation: Int = 0,
)
