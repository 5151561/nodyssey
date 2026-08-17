package io.github.nodyssey.core.net

import io.github.nodyssey.data.proxy.ProxyConfig
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
 * The forum proxy's current setting, kept in sync with [io.github.nodyssey.data.proxy.ProxySettings]
 * and readable from OkHttp's own threads without suspending.
 *
 * [ProxySelector.select] and [okhttp3.Authenticator.authenticate] both run synchronously on a call
 * OkHttp is actively routing, so they cannot collect a [Flow] themselves — this collects it once, on
 * [scope], and hands the two of them a plain volatile read instead.
 */
class LiveProxyConfig(scope: CoroutineScope, config: Flow<ProxyConfig>) {
    @Volatile
    var value: ProxyConfig = ProxyConfig()
        private set

    init {
        config.onEach { value = it }.launchIn(scope)
    }
}

/**
 * Routes [io.github.nodyssey.di.AppContainer.okHttpClient] through [live], per call rather than once.
 *
 * A [ProxySelector] is consulted on every request, unlike `OkHttpClient.Builder.proxy(Proxy)` which
 * bakes one address into the client forever — so flipping the setting or editing the address takes
 * effect on the next request, no client rebuild and no restart.
 */
class ForumProxySelector(private val live: LiveProxyConfig) : ProxySelector() {
    override fun select(uri: URI): List<Proxy> {
        val config = live.value
        if (!config.enabled || !config.isUsable) return listOf(Proxy.NO_PROXY)
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
 * SOCKS auth is handled separately, by [ForumSocksAuthenticator] — this half of the protocol only
 * exists for the CONNECT/plain-proxy path, which is what a `Proxy.Type.HTTP` route takes.
 */
class ForumProxyAuthenticator(private val live: LiveProxyConfig) : Authenticator {
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
 * per-`Proxy` or per-client equivalent. That is safe to install here because nothing else in the app
 * ever routes through a proxy: a `RequestorType.PROXY` challenge has nowhere else to come from, so
 * gating on [ProxyConfig.type] being SOCKS is enough — no separate host/port check, which would be
 * comparing against whatever hostname-or-address form the JDK happens to report for this handshake,
 * a form this class has no control over.
 */
class ForumSocksAuthenticator(private val live: LiveProxyConfig) : java.net.Authenticator() {
    override fun getPasswordAuthentication(): PasswordAuthentication? {
        if (requestorType != RequestorType.PROXY) return null
        val config = live.value
        if (config.type != ProxyType.SOCKS || config.username.isBlank()) return null
        return PasswordAuthentication(config.username, config.password.toCharArray())
    }
}
