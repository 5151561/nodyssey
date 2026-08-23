package io.github.nodyssey.data.dns

import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.withContext
import okhttp3.Dns

/**
 * 测试解析 on the resolver the app is actually using — `AppDns`, not a `DnsOverHttps` built for the
 * occasion.
 *
 * That is the whole point of the test: it answers "what will the next request get", including the
 * parts of the arrangement that are not the DoH server itself — the fallback switch, a proxy the DoH
 * requests travel through, the connection to the server being warm or cold.
 *
 * [dns] is a supplier rather than a value because the resolver lives on the graph beside the clients
 * and building it eagerly would start the DoH machinery for a screen that may never be opened.
 */
class OkHttpDnsResolutionTester(
    private val dns: () -> Dns,
    private val host: String,
    private val dispatchers: AppDispatchers,
    private val clock: AppClock,
) : DnsResolutionTester {
    override suspend fun resolve(): Result<DnsResolution> = withContext(dispatchers.io) {
        runCatchingExceptCancellation {
            val started = clock.nowMillis()
            val addresses = dns().lookup(host)
            DnsResolution(
                host = host,
                // `hostAddress` is the numeric form; the `InetAddress` itself prints as `host/address`,
                // which is the hostname asked about and the answer glued together.
                addresses = addresses.mapNotNull { it.hostAddress },
                elapsedMillis = clock.nowMillis() - started,
            )
        }
    }
}
