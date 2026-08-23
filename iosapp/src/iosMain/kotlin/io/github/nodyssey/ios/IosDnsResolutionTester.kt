package io.github.nodyssey.ios

import io.github.nodyssey.data.dns.DnsResolution
import io.github.nodyssey.data.dns.DnsResolutionTester
import io.github.plaza.core.AppClock
import io.github.plaza.core.net.remoteAddressOf
import io.github.plaza.core.runCatchingExceptCancellation
import platform.Foundation.NSURLSession

/**
 * 测试解析 on the resolver the app is actually using — which on this platform is the system's,
 * pointed at a DoH server through the process's default privacy context.
 *
 * **Why this is a request rather than a lookup.** The Android tester calls `Dns.lookup` because there
 * the resolver is an object the app owns. Here there is nothing to call: `nw_privacy_context_…`
 * configures resolution, it does not perform it, and no public API hands back the addresses the
 * system resolved. What is observable is where a request *landed* — `remoteAddress` on the task's
 * transaction metrics — so this makes one request through the forum's own session and reports that.
 *
 * It is the stricter test of the two, and usefully so. With 加密 DNS on, this platform blocks
 * cleartext resolution outright, so a DoH server that cannot be reached fails the request rather than
 * quietly answering from somewhere else — which is exactly what this button is pressed to find out.
 *
 * The address may be absent (see [remoteAddressOf]); the elapsed time never is, and the screen words
 * both.
 */
class IosDnsResolutionTester(
    private val session: () -> NSURLSession,
    private val url: String,
    private val host: String,
    private val clock: AppClock,
) : DnsResolutionTester {
    override suspend fun resolve(): Result<DnsResolution> =
        runCatchingExceptCancellation {
            val started = clock.nowMillis()
            val address = session().remoteAddressOf(url)
            DnsResolution(
                host = host,
                addresses = listOfNotNull(address),
                elapsedMillis = clock.nowMillis() - started,
            )
        }
}
