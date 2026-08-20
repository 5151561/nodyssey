package io.github.plaza.core.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLCredentialPersistence
import platform.Foundation.NSURLSession
import platform.Foundation.credentialWithUser
import kotlin.time.Duration.Companion.seconds

/** How a proxy is reached, with nothing in it about who configured it or which screen stored it. */
enum class ProxyRouteType { HTTP, SOCKS }

/**
 * One proxy, in the terms `NSURLSession` takes them.
 *
 * Deliberately not `ProxyConfig`. That type is the *setting* — it has an on/off switch, a scope, and
 * a notion of being half-typed, all of which are 代理设置's business and none of which mean anything
 * once a route has been decided on. Whoever assembles the app answers those questions and hands this
 * down; see `IosAppContainer`.
 */
data class ProxyRoute(
    val type: ProxyRouteType,
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
)

/**
 * [ProxyRoute] as `NSURLSessionConfiguration.connectionProxyDictionary` wants it.
 *
 * **The keys are string literals rather than the `kCFNetworkProxies…` constants, and that is not
 * laziness.** Half of those constants are declared `macos`-only in CFNetwork — `kCFNetworkProxies`
 * `HTTPSEnable` among them — so a Kotlin/Native iOS binary cannot name them at all, while the
 * dictionary they go into still reads the same strings on both platforms. The values are the stable
 * half of that API; the symbols are not.
 *
 * Everything below was measured on the iOS 27 simulator against a pair of throwaway proxies, because
 * Apple documents this dictionary by pointing at CFNetwork and CFNetwork documents it for streams
 * rather than for `NSURLSession`. Three things came out of it, and the third is why the SOCKS branch
 * is longer than it looks like it should be.
 *
 * **One.** The HTTP keys are honoured for both schemes: an `https://` request produces a `CONNECT`
 * at the proxy, an `http://` one produces an absolute-URI `GET`, and pointing either at a closed
 * port fails the request rather than falling back.
 *
 * **Two.** Both pairs are set from one address even though CFNetwork treats HTTP and HTTPS proxies as
 * two separate settings. This app's traffic is `https://` almost to the last request, so setting only
 * the pair matching the scheme would leave it unrouted — which is the failure that looks exactly like
 * "the proxy setting does nothing".
 *
 * **Three, and this is the one worth knowing: `NSURLSession` honours the SOCKS keys for `https://`
 * and silently ignores them for `http://`.** Measured both ways round: an `https://` request tunnels
 * through the SOCKS server and fails when that server's port is closed, while an `http://` request
 * succeeds *with the SOCKS port closed* — it never went there. A plaintext request would therefore
 * leave the device in the clear while 代理设置 said 已开启, and the only `http://` URL this app can be
 * given is a self-hosted image host the user typed, which is carrying their API key.
 *
 * So a SOCKS route also names itself under the HTTP keys. A plaintext request then reaches the SOCKS
 * port speaking HTTP, the SOCKS server rejects it, and the request *fails* — which is Android's rule
 * for a proxy that cannot carry a request, rather than a silent direct connection that skips it. It
 * does not disturb the `https://` path: with both sets present, a secure request still arrives as a
 * SOCKS `CONNECT` (verified, same run).
 */
internal fun ProxyRoute.connectionProxyDictionary(): Map<Any?, Any?> =
    when (type) {
        ProxyRouteType.HTTP ->
            mapOf<Any?, Any?>(
                "HTTPEnable" to 1,
                "HTTPProxy" to host,
                "HTTPPort" to port,
                "HTTPSEnable" to 1,
                "HTTPSProxy" to host,
                "HTTPSPort" to port,
            )

        ProxyRouteType.SOCKS ->
            buildMap<Any?, Any?> {
                put("SOCKSEnable", 1)
                put("SOCKSProxy", host)
                put("SOCKSPort", port)
                // 5, not 4: the app's own setting offers a username and a password, which is a
                // SOCKS5 handshake.
                put("SOCKSVersion", 5)
                if (username.isNotBlank()) {
                    put("SOCKSUser", username)
                    put("SOCKSPassword", password)
                }
                // Point three above: not a second proxy, a refusal. Plaintext ignores the SOCKS keys,
                // and this is what stops it from quietly going direct instead.
                put("HTTPEnable", 1)
                put("HTTPProxy", host)
                put("HTTPPort", port)
            }
    }

/**
 * The credential an HTTP proxy's `407` is answered with, or null when there is nothing to answer it.
 *
 * SOCKS does not come through here — its credential is in the dictionary above, because the
 * handshake happens below the URL loading system and never surfaces as a challenge.
 */
internal fun ProxyRoute.credential(): NSURLCredential? =
    if (type != ProxyRouteType.HTTP || username.isBlank()) {
        null
    } else {
        NSURLCredential.credentialWithUser(username, password, NSURLCredentialPersistence.NSURLCredentialPersistenceForSession)
    }

/**
 * The session to use *right now*, rebuilt whenever the route under it changes.
 *
 * This is the shape the proxy takes on Apple, and it is not the shape it takes on Android. There, a
 * `ProxySelector` is asked on every request, so one `OkHttpClient` outlives every edit the user
 * makes. Here the proxy is part of `NSURLSessionConfiguration`, which a session copies when it is
 * created — `session.configuration` hands back a copy, and writing to it changes nothing. So the
 * only way for a saved edit to take effect is a new session, and the only way for the code above to
 * not care is for it to ask for the session per call rather than hold one. [NSUrlSessionTransport]
 * takes a `() -> NSURLSession` for that reason.
 *
 * @param build called with null for a direct connection. Everything else a session is configured
 *   with — headers, cookie storage, timeouts — belongs to the caller and is closed over here.
 */
class ProxiedUrlSession(
    scope: CoroutineScope,
    route: Flow<ProxyRoute?>,
    private val build: (ProxyRoute?) -> NSURLSession,
) {
    private val state = MutableStateFlow(Routed(null, build(null)))

    /**
     * Read per request, and racy in exactly the way Android's is: a call that read this an instant
     * before the user saved an edit goes out the old way. `LiveProxyConfig` has the same window and
     * for the same reason — a setting that lands between two lines of a request cannot land in the
     * middle of it.
     */
    val current: NSURLSession get() = state.value.session

    init {
        route
            .distinctUntilChanged()
            .onEach { updated ->
                if (state.value.through == updated) return@onEach
                val previous = state.value.session
                state.value = Routed(updated, build(updated))
                // Invalidation releases the delegate the session holds — without it every edit in
                // 代理设置 leaks one. It is delayed rather than immediate because a task created on
                // an already-invalidated session does not fail, it does *nothing*: the completion
                // handler is never called and the coroutine that made the request waits forever.
                // This window is what covers a caller that read [current] just before the swap.
                delay(HANDOVER)
                previous.finishTasksAndInvalidate()
            }.launchIn(scope)
    }

    private data class Routed(val through: ProxyRoute?, val session: NSURLSession)

    private companion object {
        val HANDOVER = 5.seconds
    }
}
