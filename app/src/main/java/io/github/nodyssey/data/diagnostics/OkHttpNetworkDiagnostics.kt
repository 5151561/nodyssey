package io.github.nodyssey.data.diagnostics

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.dns.DohConfig
import io.github.nodyssey.data.proxy.ProxyConfig
import io.github.nodyssey.data.proxy.ProxyScope
import io.github.nodyssey.data.proxy.toProxyConnectionFailure
import io.github.nodyssey.platform.NetworkSnapshot
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Connection
import okhttp3.ConnectionPool
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okio.blackholeSink
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * The two packages 网络自检 names — see [AppIdentity], where the reason they are named is written
 * down. Read together so the pair describes one moment, for the reason [NetworkSnapshot] is one
 * object.
 */
data class BrowserIdentities(
    val customTabs: AppIdentity?,
    val default: AppIdentity?,
)

/**
 * 网络自检 measured off OkHttp's own [EventListener].
 *
 * Timing the call from the outside — a clock either side of `execute` — was the obvious
 * implementation and it answers the wrong question. It produces one number, and one number cannot
 * tell a resolver that took eight seconds from a connection that never got above 5 KB/s, which are
 * the two reports this screen exists to separate. The event listener is where the client already
 * announces each boundary it crosses, so the split costs a callback rather than a second request.
 *
 * Each probe runs on a client derived from the real one with [Companion.probeConnectionPool] swapped
 * in. Everything that decides how a request travels is inherited — 代理, 加密 DNS, the cookie jar,
 * the browser headers Cloudflare is checking for — so what is measured is the path the app's own
 * traffic takes, which is the entire point. What is *not* inherited is the pool of connections that
 * are already open: a probe that reused one would report no DNS, no handshake and a first byte in
 * twelve milliseconds, describing a connection the reader's next cold request will not get.
 */
class OkHttpNetworkDiagnostics(
    private val forumClient: () -> OkHttpClient,
    private val updatesClient: () -> OkHttpClient,
    private val updatesUrl: String,
    private val proxyConfig: Flow<ProxyConfig>,
    private val dohConfig: Flow<DohConfig>,
    private val device: DeviceIdentity,
    private val appVersion: String,
    private val network: () -> NetworkSnapshot,
    private val browsers: () -> BrowserIdentities,
    private val dispatchers: AppDispatchers,
) : NetworkDiagnostics {
    override suspend fun environment(): NetworkEnvironment {
        val proxy = proxyConfig.first()
        val doh = dohConfig.first()
        val snapshot = network()
        val installed = browsers()
        return NetworkEnvironment(
            device = device,
            appVersion = appVersion,
            transport = snapshot.transport,
            vpnActive = snapshot.vpnActive,
            metered = snapshot.metered,
            proxy = proxy.takeIf { it.enabled && it.isUsable }?.summarise(),
            dohProvider = doh.provider.takeIf { doh.enabled && doh.isUsable },
            customTabsProvider = installed.customTabs,
            defaultBrowser = installed.default,
        )
    }

    override suspend fun probe(target: ProbeTarget): ProbeResult =
        withContext(dispatchers.io) {
            val client =
                when (target) {
                    ProbeTarget.FORUM -> forumClient()
                    ProbeTarget.UPDATES -> updatesClient()
                }
            val url =
                when (target) {
                    ProbeTarget.FORUM -> "${NodeSeekSite.BASE_URL}/"
                    ProbeTarget.UPDATES -> updatesUrl
                }
            val recorder = ProbeRecorder()
            val probeClient =
                client
                    .newBuilder()
                    .connectionPool(probeConnectionPool())
                    .eventListener(recorder)
                    .build()
            val request =
                Request
                    .Builder()
                    .url(url)
                    // A body served out of a cache arrives at the speed of the filesystem, and the
                    // row would read as a fast network. Nothing in this graph caches a page today;
                    // this is here so that a client that grows a cache later cannot quietly turn
                    // this screen into a liar.
                    .cacheControl(CacheControl.FORCE_NETWORK)
                    .build()

            runCatchingExceptCancellation {
                probeClient.newCall(request).execute().use { response ->
                    // Read to the end before asking for the timing: `bodyBytesPerSecond` divides by
                    // the gap between the first byte and the last, and the last one has not been
                    // received until the stream is drained.
                    val bytes = response.body.source().readAll(blackholeSink())
                    ProbeResult.Answered(
                        statusCode = response.code,
                        timing = recorder.timing(bytes),
                    )
                }
            }.getOrElse { failure -> ProbeResult.Failed(failure.toProxyConnectionFailure()) }
        }

    private fun ProxyConfig.summarise(): ProxySummary =
        ProxySummary(
            type = type,
            loopback = host.isLoopbackLiteral(),
            port = port,
            forumOnly = scope == ProxyScope.FORUM_ONLY,
        )

    private companion object {
        /**
         * A pool of its own per probe, holding nothing afterwards.
         *
         * Zero idle connections rather than `evictAll` on the shared pool, which would have been the
         * other way to force a cold path and would also have thrown away every connection the app is
         * mid-request on. A diagnostic must not degrade the thing it is diagnosing.
         */
        fun probeConnectionPool() = ConnectionPool(0, 1, TimeUnit.NANOSECONDS)

        /**
         * Loopback by inspection rather than by [InetAddress.isLoopbackAddress], which resolves a
         * name — a DNS lookup on the main path of a screen whose whole subject is slow DNS, for a
         * question about a field the reader typed. The three literals below are what anybody typing
         * a local Clash or mihomo port actually enters.
         */
        fun String.isLoopbackLiteral(): Boolean =
            trim().lowercase() in setOf("127.0.0.1", "localhost", "::1", "[::1]")
    }
}

/**
 * Records the boundaries of one call.
 *
 * One instance per call and never reused: OkHttp calls these back on the connection's thread, and an
 * `EventListener` installed on a client is shared by every call that client makes. The listener is
 * therefore attached to a client built for this one probe, which is the only way the fields below can
 * be plain `var`s describing a single request.
 */
private class ProbeRecorder : EventListener() {
    private var callStart = 0L
    private var dnsStart = 0L
    private var dnsEnd = 0L
    private var connectStart = 0L
    private var secureStart = 0L
    private var secureEnd = 0L
    private var connectEnd = 0L
    private var headersEnd = 0L
    private var bodyEnd = 0L

    override fun callStart(call: Call) {
        callStart = System.nanoTime()
    }

    override fun dnsStart(call: Call, domainName: String) {
        dnsStart = System.nanoTime()
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        dnsEnd = System.nanoTime()
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        connectStart = System.nanoTime()
    }

    override fun secureConnectStart(call: Call) {
        secureStart = System.nanoTime()
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        secureEnd = System.nanoTime()
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
    ) {
        connectEnd = System.nanoTime()
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException,
    ) {
        connectEnd = System.nanoTime()
    }

    override fun connectionAcquired(call: Call, connection: Connection) = Unit

    override fun responseHeadersEnd(call: Call, response: Response) {
        headersEnd = System.nanoTime()
    }

    /**
     * The last byte of the body — but only where OkHttp reaches it itself. The probe drains the
     * stream by hand, so [timing] falls back to the clock at that moment; this is here for the
     * shapes where the body ends without the caller doing the draining.
     */
    override fun responseBodyEnd(call: Call, byteCount: Long) {
        bodyEnd = System.nanoTime()
    }

    fun timing(bytes: Long): ProbeTiming {
        val end = if (bodyEnd != 0L) bodyEnd else System.nanoTime()
        return ProbeTiming(
            dnsMillis = span(dnsStart, dnsEnd),
            // TCP alone. `connectEnd` is after the TLS handshake where there is one, so the secure
            // half is subtracted rather than reported twice under two names.
            connectMillis = span(connectStart, connectEnd)?.let { total ->
                val secure = span(secureStart, secureEnd) ?: 0L
                (total - secure).coerceAtLeast(0L)
            },
            tlsMillis = span(secureStart, secureEnd),
            firstByteMillis = span(callStart, headersEnd) ?: 0L,
            totalMillis = span(callStart, end) ?: 0L,
            bytes = bytes,
        )
    }

    /** Null where the pair never fired — a reused connection, or a call that failed before reaching it. */
    private fun span(from: Long, to: Long): Long? =
        if (from == 0L || to == 0L || to < from) null else (to - from) / NANOS_PER_MILLI

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
