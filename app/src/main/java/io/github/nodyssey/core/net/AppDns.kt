package io.github.nodyssey.core.net

import io.github.nodyssey.data.dns.DohConfig
import io.github.nodyssey.data.dns.isIpLiteral
import io.github.nodyssey.data.dns.resolvesOverHttps
import io.github.plaza.core.AppClock
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
 * What it adds over handing the client a `DnsOverHttps` directly is the four things that would
 * otherwise each be a bug:
 *
 * - **An address is not a question.** OkHttp calls this for whatever host it is about to connect to,
 *   an IP literal included, and a DoH server asked to resolve `127.0.0.1` answers that no such name
 *   exists. Turning this setting on would break every proxy configured by address — which, since a
 *   local Clash listener is the usual one, is most of them.
 * - **The servers are a list.** Each ticked server is asked in turn, and the next only when the one
 *   before it failed — see [lookup].
 * - **A rebuilt resolver is a rebuilt cache.** Each server's resolver is rebuilt only when *that
 *   server* changes, so reordering the list, adding a server or flipping the fallback switch keeps
 *   the ones that stayed.
 * - **Pooled connections outlive a change.** Same as the proxy's: a connection already open to an
 *   address the old resolvers gave keeps carrying requests, so [onResolverChanged] empties the pool.
 */
class AppDns(
    scope: CoroutineScope,
    config: Flow<DohConfig>,
    /**
     * Builds the resolver for one server, or answers null when it cannot — a URL that no longer
     * parses, for instance. Injected so a test can drive the switching without a network.
     */
    private val resolvers: (DohResolverSpec) -> Dns?,
    /** What answers when 加密 DNS is off, and what [DohConfig.fallbackToSystem] falls back to. */
    private val system: Dns = Dns.SYSTEM,
    /** Called when the resolvers behind new connections change. See the class KDoc. */
    private val onResolverChanged: () -> Unit = {},
    /** Times [RECENT_FAILURE_MILLIS]. */
    private val clock: AppClock = AppClock.System,
) : Dns {

    private class Built(val spec: DohResolverSpec, val dns: Dns) {
        /**
         * When a lookup last had to move past this server to get its answer, or [NEVER]. Written from
         * whichever thread asked.
         */
        @Volatile
        var passedOverAt: Long = NEVER
    }

    private class Active(val chain: List<Built>, val fallbackToSystem: Boolean)

    @Volatile
    private var active: Active? = null

    init {
        config.onEach(::apply).launchIn(scope)
    }

    /**
     * The first answer from the ticked servers, top to bottom.
     *
     * Any failure moves on to the next server, a "no such name" included. The names this app looks up
     * — the forum, its image hosts, the update server — all exist, so an answer saying otherwise is
     * worth a second opinion, and the cost of one for a name that really is gone is one more query.
     * When every server has failed the first failure is thrown, carrying the rest as suppressed.
     *
     * A server that was passed over within the last [RECENT_FAILURE_MILLIS] is asked after the
     * others rather than first. An unreachable server fails by timing out — the DoH client's connect
     * timeout is ten seconds — and without this every new hostname would sit through that wait again
     * before reaching the server that works. It is still asked, last, so a 首选 that comes back is
     * used again as soon as the minute is up.
     *
     * "Passed over" means it failed and a server after it answered. A failure nobody could answer —
     * a name that does not exist, a network that is down — says nothing about which server is the
     * problem, so it marks none of them; counting it would put the unreachable one back in front.
     * The minute runs from the answer, not from the start of the wait.
     */
    override fun lookup(hostname: String): List<InetAddress> {
        val active = active ?: return system.lookup(hostname)
        if (!isResolvableName(hostname)) return system.lookup(hostname)
        val now = clock.nowMillis()
        // Stable, so the list's own order holds within each half.
        val order = active.chain.sortedBy { (now - it.passedOverAt) in 0 until RECENT_FAILURE_MILLIS }
        val failed = mutableListOf<Built>()
        var failure: UnknownHostException? = null
        for (server in order) {
            try {
                val answer = server.dns.lookup(hostname)
                val answeredAt = clock.nowMillis()
                failed.forEach { it.passedOverAt = answeredAt }
                server.passedOverAt = NEVER
                return answer
            } catch (e: UnknownHostException) {
                failed += server
                val first = failure
                if (first == null) failure = e else first.addSuppressed(e)
            }
        }
        if (!active.fallbackToSystem) throw failure ?: UnknownHostException(hostname)
        return system.lookup(hostname)
    }

    private fun apply(config: DohConfig) {
        val previous = active
        val next =
            if (!config.resolvesOverHttps()) {
                null
            } else {
                val warm = previous?.chain.orEmpty().associateBy { it.spec }
                val chain = config.chain.mapNotNull { server ->
                    val spec = DohResolverSpec(server.url, server.bootstrap, config.includeIPv6)
                    warm[spec] ?: resolvers(spec)?.let { Built(spec, it) }
                }
                // Every server unbuildable is the same as none: the platform answers, rather than
                // every lookup failing against an empty list.
                chain.takeIf { it.isNotEmpty() }?.let { Active(it, config.fallbackToSystem) }
            }
        active = next
        if (previous?.chain?.map { it.spec } != next?.chain?.map { it.spec }) onResolverChanged()
    }

    private companion object {
        const val NEVER = Long.MIN_VALUE / 2

        const val RECENT_FAILURE_MILLIS = 60_000L

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

/** The fields one server's resolver is built from; anything else can change without rebuilding it. */
data class DohResolverSpec(
    val url: String,
    val bootstrap: List<String>,
    val includeIPv6: Boolean,
)

/**
 * The real resolvers behind [AppDns]: OkHttp's own DoH implementation, pointed at one saved server.
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
fun dnsOverHttpsResolvers(client: () -> OkHttpClient): (DohResolverSpec) -> Dns? = { spec ->
    spec.url.toHttpUrlOrNull()?.let { url ->
        DnsOverHttps.Builder()
            .client(client())
            .url(url)
            .includeIPv6(spec.includeIPv6)
            .apply {
                val bootstrap = spec.bootstrap.mapNotNull { address ->
                    // A literal, so this parses rather than resolves; anything the shared validator
                    // let through that this rejects is simply not a bootstrap address.
                    runCatching { InetAddress.getByName(address) }.getOrNull()
                }
                if (bootstrap.isNotEmpty()) bootstrapDnsHosts(bootstrap)
            }.build()
    }
}
