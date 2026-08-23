package io.github.nodyssey.core.net

import io.github.nodyssey.data.dns.DohConfig
import io.github.nodyssey.data.dns.isIpLiteral
import io.github.nodyssey.data.dns.resolvesOverHttps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Every hostname the app's own clients look up, answered by 加密 DNS when it is on and by the
 * platform when it is not.
 *
 * The shape is `LiveProxyConfig`'s and for the same reason: [Dns.lookup] is called synchronously on
 * the thread OkHttp is connecting on, so it cannot collect a [Flow] itself. This collects once, on
 * [scope], and leaves the lookup a volatile read.
 *
 * What it adds over handing the client a `DnsOverHttps` directly is the three things that would
 * otherwise each be a bug:
 *
 * - **An address is not a question.** OkHttp calls this for whatever host it is about to connect to,
 *   an IP literal included, and a DoH server asked to resolve `127.0.0.1` answers that no such name
 *   exists. Turning this setting on would break every proxy configured by address — which, since a
 *   local Clash listener is the usual one, is most of them.
 * - **A rebuilt resolver is a rebuilt cache.** The DoH client is rebuilt only when the *server*
 *   changes, so toggling IPv6 or the fallback switch does not throw away a warm connection to it.
 * - **Pooled connections outlive a change.** Same as the proxy's: a connection already open to an
 *   address the old resolver gave keeps carrying requests, so [onResolverChanged] empties the pool.
 */
class AppDns(
    scope: CoroutineScope,
    config: Flow<DohConfig>,
    /**
     * Builds the resolver for a config, or answers null when it cannot — a URL that no longer parses,
     * for instance. Injected so a test can drive the switching without a network.
     */
    private val resolvers: (DohConfig) -> Dns?,
    /** What answers when 加密 DNS is off, and what [DohConfig.fallbackToSystem] falls back to. */
    private val system: Dns = Dns.SYSTEM,
    /** Called when the resolver behind new connections changes. See the class KDoc. */
    private val onResolverChanged: () -> Unit = {},
) : Dns {

    private class Active(val key: ResolverKey, val dns: Dns, val fallbackToSystem: Boolean)

    /** The fields a built resolver was made from; the rest can change without rebuilding one. */
    private data class ResolverKey(val url: String, val bootstrap: List<String>, val includeIPv6: Boolean)

    @Volatile
    private var active: Active? = null

    init {
        config.onEach(::apply).launchIn(scope)
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val active = active ?: return system.lookup(hostname)
        if (!isResolvableName(hostname)) return system.lookup(hostname)
        return try {
            active.dns.lookup(hostname)
        } catch (e: UnknownHostException) {
            if (!active.fallbackToSystem) throw e
            system.lookup(hostname)
        }
    }

    private fun apply(config: DohConfig) {
        val previous = active
        val next =
            if (!config.resolvesOverHttps()) {
                null
            } else {
                val key = ResolverKey(config.serverUrl, config.bootstrap, config.includeIPv6)
                if (previous != null && previous.key == key) {
                    Active(key, previous.dns, config.fallbackToSystem)
                } else {
                    resolvers(config)?.let { Active(key, it, config.fallbackToSystem) }
                }
            }
        active = next
        if (previous?.key != next?.key) onResolverChanged()
    }

    private companion object {
        /**
         * Whether a DoH server is the right thing to ask about [hostname].
         *
         * An IP literal is already the answer, and a single-label name — `localhost`, a router's own
         * name, whatever the LAN's DHCP hands out — is not a name any public resolver has. Both go to
         * the platform, which answers the first without a network and the second from the resolver
         * that knows about this network.
         */
        fun isResolvableName(hostname: String): Boolean =
            !isIpLiteral(hostname) && hostname.contains('.') && !hostname.endsWith(".local", ignoreCase = true)
    }
}

/**
 * The real resolver behind [AppDns]: OkHttp's own DoH implementation, pointed at the saved server.
 *
 * [client] is the client the DoH requests themselves go out on, and it must not be one of the app's:
 * `DnsOverHttps.Builder.build` replaces the DNS of whatever client it is handed, so recursion is not
 * the risk — the dispatcher is. A lookup blocks the thread it was called on until the query returns,
 * and a query queued behind the very calls that are waiting on it is a deadlock. See the container,
 * where that client is built with a dispatcher of its own.
 *
 * `bootstrapDnsHosts` is only set when there is something to set it to. Left null, OkHttp resolves
 * the DoH server's own hostname with the system resolver — the right behaviour for a custom server
 * typed without addresses, and one the screen says out loud rather than papering over.
 */
fun dnsOverHttpsResolvers(client: () -> OkHttpClient): (DohConfig) -> Dns? = { config ->
    config.serverUrl.toHttpUrlOrNull()?.let { url ->
        DnsOverHttps.Builder()
            .client(client())
            .url(url)
            .includeIPv6(config.includeIPv6)
            .apply {
                val bootstrap = config.bootstrap.mapNotNull { address ->
                    // A literal, so this parses rather than resolves; anything the shared validator
                    // let through that this rejects is simply not a bootstrap address.
                    runCatching { InetAddress.getByName(address) }.getOrNull()
                }
                if (bootstrap.isNotEmpty()) bootstrapDnsHosts(bootstrap)
            }.build()
    }
}
