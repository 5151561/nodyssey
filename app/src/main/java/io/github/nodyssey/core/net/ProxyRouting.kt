package io.github.nodyssey.core.net

import io.github.nodyssey.data.proxy.ProxyClientKind
import io.github.nodyssey.data.proxy.ProxyConfig
import io.github.nodyssey.data.proxy.ProxyScope
import io.github.nodyssey.data.proxy.ProxyType
import io.github.nodyssey.data.proxy.routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * The proxy setting's current value, kept in sync with [io.github.nodyssey.data.proxy.ProxySettings]
 * and readable from OkHttp's own threads without suspending.
 *
 * [ProxySelector.select] and [okhttp3.Authenticator.authenticate] both run synchronously on a call
 * OkHttp is actively routing, so they cannot collect a [Flow] themselves — this collects it once, on
 * [scope], and hands the two of them a plain volatile read instead.
 *
 * Android-only, and it is the *asking* that makes it so. `NSURLSession` cannot be asked at all: its
 * proxy is fixed when a session is created, so the Apple side reacts to the same flow by building a
 * new session rather than by keeping a value something can read — see `ProxiedUrlSession`. What the
 * two do share is [io.github.nodyssey.data.proxy.routing], the tuple that decides whether a saved
 * edit is worth acting on.
 */
class LiveProxyConfig(
    scope: CoroutineScope,
    config: Flow<ProxyConfig>,
    /**
     * Called when a change lands that pooled connections would otherwise outlive.
     *
     * A [ProxySelector] decides where a *new* connection goes; a connection already in the pool keeps
     * carrying requests to the host it was opened to. Without this, turning the proxy on would leave
     * the forum riding the direct connection it was already using, and the setting would look like it
     * had done nothing until that connection idled out.
     */
    private val onRoutingChanged: () -> Unit = {},
) {
    @Volatile
    var value: ProxyConfig = ProxyConfig()
        private set

    init {
        config
            .onEach { updated ->
                val previous = value
                value = updated
                if (previous.routing() != updated.routing()) onRoutingChanged()
            }.launchIn(scope)
    }

    /**
     * The fields a live connection was opened against; the rest can change without disturbing one.
     *
     * The credentials are deliberately *not* here. OkHttp answers a `407` per request, so a re-typed
     * password reaches the next call whatever the pool holds — and emptying the pool for one would
     * drop working connections to punish a change that cost them nothing. The Apple side has to be
     * stricter, because a SOCKS credential is baked into the session it was built with; it compares
     * whole `ProxyRoute` values rather than reusing this.
     */
    private fun ProxyConfig.routing() = listOf(enabled, type, host, port, scope)
}

/**
 * Routes one of the app's OkHttp clients through [live], per call rather than once.
 *
 * A [ProxySelector] is consulted on every request, unlike `OkHttpClient.Builder.proxy(Proxy)` which
 * bakes one address into the client forever — so flipping the setting or editing the address takes
 * effect on the next request, no client rebuild and no restart.
 *
 * When 代理设置 is off this defers to [platform] rather than answering `NO_PROXY`, because "the user
 * has not configured a proxy" is not the same statement as "there is no proxy". A VPN can declare one
 * for the network it puts up — `VpnService.Builder.setHttpProxy` — and Android publishes it to every
 * app through the `https.proxyHost`/`http.nonProxyHosts` system properties that the platform selector
 * reads. That is the proxy the browser and the WebView use, and it is why they keep working when the
 * app does not: a request handed to an HTTP proxy carries the *hostname*, so nothing on the device
 * has to resolve it. Answering `NO_PROXY` opted out of that and made every request depend on the
 * system resolver — which, with 私人 DNS 设成主机名 and that DoT server unreachable, resolves nothing
 * at all (measured 2026-08-20 on Clash Meta + `PrivateDnsBroken`: direct `UnknownHostException`, the
 * same request through the declared proxy fine).
 */
class AppProxySelector(
    private val live: LiveProxyConfig,
    private val kind: ProxyClientKind,
    /**
     * Where an unrouted call goes: the selector Android installs, which is the one that knows about
     * proxies the app did not configure. Injected so a test can hand over its own; never null in the
     * app, but [ProxySelector.getDefault] is declared nullable and a null there means only that there
     * is nothing to defer to.
     *
     * Captured once instead of read per call. The instance is stable — it is the properties behind it
     * that change as networks come and go, and it re-reads them on every [select].
     */
    private val platform: ProxySelector? = getDefault(),
) : ProxySelector() {
    override fun select(uri: URI): List<Proxy> {
        val config = live.value
        if (!config.routes(kind)) return platform?.select(uri) ?: listOf(Proxy.NO_PROXY)
        val type = when (config.type) {
            ProxyType.HTTP -> Proxy.Type.HTTP
            ProxyType.SOCKS -> Proxy.Type.SOCKS
        }
        // Unresolved: resolving the host here would block this call, which OkHttp makes on the
        // thread doing the connecting, and a SOCKS proxy does its own DNS lookups anyway.
        return listOf(Proxy(type, InetSocketAddress.createUnresolved(config.host, config.port)))
    }

    /**
     * Reported onward only for the routes [platform] chose.
     *
     * A failure on the app's own proxy has nothing to fall back to — the alternative to a broken proxy
     * is a broken request, not a silent direct connection that skips it. One on a route the platform
     * handed out is the platform's to know about, since it is the half that keeps that bookkeeping.
     */
    override fun connectFailed(uri: URI, socketAddress: SocketAddress, exception: IOException) {
        if (!live.value.routes(kind)) platform?.connectFailed(uri, socketAddress, exception)
    }
}

/**
 * Answers an HTTP proxy's `407 Proxy Authentication Required`.
 *
 * Shared by every client: a 407 can only reach one that is actually routed through the proxy, so the
 * client that [ProxyScope.FORUM_ONLY] left direct never asks this anything.
 *
 * SOCKS auth is handled separately, by [AppSocksAuthenticator] — this half of the protocol only
 * exists for the CONNECT/plain-proxy path, which is what a `Proxy.Type.HTTP` route takes.
 */
class AppProxyAuthenticator(private val live: LiveProxyConfig) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val config = live.value
        if (config.username.isBlank()) return null
        // A credential already on the request means it was tried and rejected — offering the same
        // one again would just retry forever.
        if (response.request.header("Proxy-Authorization") != null) return null
        return response.request.newBuilder()
            .header("Proxy-Authorization", Credentials.basic(config.username, config.password))
            .build()
    }
}

/**
 * Answers a SOCKS5 proxy's username/password challenge.
 *
 * The JDK's own socket implementation runs the SOCKS handshake below OkHttp, and the only hook it
 * offers for a credential is the process-wide [java.net.Authenticator.setDefault] — there is no
 * per-`Proxy` or per-client equivalent. That is safe to install here because every proxied client in
 * the app routes through this one config. Android's `SocksSocketImpl` uses the six-argument
 * `requestPasswordAuthentication` overload, which reports `RequestorType.SERVER`, so requestor type
 * cannot identify this callback. The `SOCKS5` protocol can: it keeps this process-wide authenticator
 * from offering the proxy credential to ordinary HTTP server authentication.
 */
class AppSocksAuthenticator(private val live: LiveProxyConfig) : java.net.Authenticator() {
    override fun getPasswordAuthentication(): PasswordAuthentication? {
        val config = live.value
        if (config.type != ProxyType.SOCKS || config.username.isBlank()) return null
        if (!requestingProtocol.equals("SOCKS5", ignoreCase = true)) return null
        return PasswordAuthentication(config.username, config.password.toCharArray())
    }
}
