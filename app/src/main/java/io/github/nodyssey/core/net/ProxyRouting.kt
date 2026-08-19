package io.github.nodyssey.core.net

import io.github.nodyssey.data.proxy.ProxyConfig
import io.github.nodyssey.data.proxy.ProxyScope
import io.github.nodyssey.data.proxy.ProxyType
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
 * Which side of [ProxyScope] a client sits on.
 *
 * [FORUM] is the client that carries the NodeSeek session — the one the proxy exists for, and the one
 * [ProxyScope.FORUM_ONLY] keeps routed. [THIRD_PARTY] is the image host and the update check: same
 * setting, same address, but the user is allowed to leave them direct.
 */
enum class ProxyClientKind { FORUM, THIRD_PARTY }

/** Whether a client of this [kind] should be routed through this config right now. */
fun ProxyConfig.routes(kind: ProxyClientKind): Boolean =
    enabled && isUsable && (kind == ProxyClientKind.FORUM || scope == ProxyScope.EVERYTHING)

/**
 * The proxy setting's current value, kept in sync with [io.github.nodyssey.data.proxy.ProxySettings]
 * and readable from OkHttp's own threads without suspending.
 *
 * [ProxySelector.select] and [okhttp3.Authenticator.authenticate] both run synchronously on a call
 * OkHttp is actively routing, so they cannot collect a [Flow] themselves — this collects it once, on
 * [scope], and hands the two of them a plain volatile read instead.
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

    /** The fields a live connection was opened against; the rest can change without disturbing one. */
    private fun ProxyConfig.routing() = listOf(enabled, type, host, port, scope)
}

/**
 * Routes one of the app's OkHttp clients through [live], per call rather than once.
 *
 * A [ProxySelector] is consulted on every request, unlike `OkHttpClient.Builder.proxy(Proxy)` which
 * bakes one address into the client forever — so flipping the setting or editing the address takes
 * effect on the next request, no client rebuild and no restart.
 */
class AppProxySelector(
    private val live: LiveProxyConfig,
    private val kind: ProxyClientKind,
) : ProxySelector() {
    override fun select(uri: URI): List<Proxy> {
        val config = live.value
        if (!config.routes(kind)) return listOf(Proxy.NO_PROXY)
        val type = when (config.type) {
            ProxyType.HTTP -> Proxy.Type.HTTP
            ProxyType.SOCKS -> Proxy.Type.SOCKS
        }
        // Unresolved: resolving the host here would block this call, which OkHttp makes on the
        // thread doing the connecting, and a SOCKS proxy does its own DNS lookups anyway.
        return listOf(Proxy(type, InetSocketAddress.createUnresolved(config.host, config.port)))
    }

    // Nothing to fall back to — the alternative to a broken proxy is a broken request, not a silent
    // direct connection that skips it.
    override fun connectFailed(uri: URI, socketAddress: SocketAddress, exception: IOException) = Unit
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
