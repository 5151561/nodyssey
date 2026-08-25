package io.github.nodyssey.core.net

import io.github.nodyssey.data.proxy.ProxyClientKind
import io.github.nodyssey.data.proxy.ProxyConfig
import io.github.nodyssey.data.proxy.ProxyScope
import io.github.nodyssey.data.proxy.ProxyType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.Authenticator
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

private val FORUM = URI("https://www.nodeseek.com/categories/daily")
private val IMAGE_HOST = URI("https://api.nodeimage.com/api/upload")

/** Whatever the platform would have answered — a VPN's declared proxy, in the case that matters. */
private val PLATFORM_PROXY = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("127.40.186.43", 40461))

private val CONFIGURED = ProxyConfig(
    enabled = true,
    type = ProxyType.SOCKS,
    host = "127.0.0.1",
    port = 7890,
)

/**
 * Which client goes through the proxy and which does not.
 *
 * The routing decision is the whole feature: a wrong answer here is a request that leaves the device
 * by a route the user did not pick, in either direction.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProxyRoutingTest {
    /**
     * "The user has not configured a proxy" is not "there is no proxy".
     *
     * A VPN can declare one for its network and Android hands it to every app through the platform
     * selector — it is the proxy the browser uses, and a request given to it carries the hostname, so
     * nothing on the device has to resolve it. Answering `NO_PROXY` here opted out of that and made
     * every request depend on the system resolver, which 私人 DNS 指到一台连不上的 DoT 服务器 stops
     * answering entirely.
     */
    @Test
    fun `with the app's proxy off the platform's answer is the one used`() = runTest {
        val selector = selectorFor(CONFIGURED.copy(enabled = false), ProxyClientKind.FORUM)

        assertEquals(listOf(PLATFORM_PROXY), selector.select(FORUM))
    }

    /** An address that is still being typed is not an address to send requests to. */
    @Test
    fun `a half-typed address falls through rather than routing through it`() = runTest {
        val selector = selectorFor(CONFIGURED.copy(port = 0), ProxyClientKind.FORUM)

        assertEquals(listOf(PLATFORM_PROXY), selector.select(FORUM))
    }

    /** Deferring needs something to defer to; without it, direct is the only answer left. */
    @Test
    fun `with no platform selector an unrouted call is direct`() = runTest {
        val selector = selectorFor(CONFIGURED.copy(enabled = false), ProxyClientKind.FORUM, platform = null)

        assertEquals(listOf(Proxy.NO_PROXY), selector.select(FORUM))
    }

    /**
     * A failure on the app's own proxy has nothing to fall back to. One on a route the platform chose
     * is the platform's to know about, since it is the half keeping that bookkeeping.
     */
    @Test
    fun `only the platform's own routes report their failures back to it`() = runTest {
        val platform = RecordingProxySelector()
        val failure = IOException("refused")

        selectorFor(CONFIGURED.copy(enabled = false), ProxyClientKind.FORUM, platform)
            .connectFailed(FORUM, PLATFORM_PROXY.address(), failure)
        assertEquals(1, platform.failures)

        selectorFor(CONFIGURED, ProxyClientKind.FORUM, platform)
            .connectFailed(FORUM, InetSocketAddress.createUnresolved("127.0.0.1", 7890), failure)
        assertEquals(1, platform.failures)
    }

    @Test
    fun `by default the image host and the update check go the same way as the forum`() = runTest {
        val thirdParty = selectorFor(CONFIGURED, ProxyClientKind.THIRD_PARTY)

        assertEquals(
            listOf(Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", 7890))),
            thirdParty.select(IMAGE_HOST),
        )
    }

    @Test
    fun `forum-only leaves the third-party clients off the app's proxy and the forum on it`() = runTest {
        val config = CONFIGURED.copy(scope = ProxyScope.FORUM_ONLY)

        assertEquals(listOf(PLATFORM_PROXY), selectorFor(config, ProxyClientKind.THIRD_PARTY).select(IMAGE_HOST))
        assertEquals(
            listOf(Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", 7890))),
            selectorFor(config, ProxyClientKind.FORUM).select(FORUM),
        )
    }

    /** The host is handed over unresolved: a DNS lookup on OkHttp's connecting thread would block it. */
    @Test
    fun `the proxy address is not resolved here`() = runTest {
        val selector = selectorFor(CONFIGURED.copy(host = "proxy.example.com"), ProxyClientKind.FORUM)

        val address = selector.select(FORUM).single().address() as InetSocketAddress
        assertTrue(address.isUnresolved)
        assertEquals("proxy.example.com", address.hostName)
    }

    /**
     * Pooled connections outlive the setting that opened them, so a change has to empty the pool —
     * otherwise turning the proxy on leaves the forum riding the direct connection it already had.
     */
    @Test
    fun `a routing change empties the connection pool and a cosmetic one does not`() = runTest {
        var evictions = 0
        val configs = MutableStateFlow(CONFIGURED)
        LiveProxyConfig(backgroundScope, configs) { evictions++ }
        runCurrent()
        val afterFirstValue = evictions

        configs.value = CONFIGURED.copy(username = "someone", password = "secret")
        runCurrent()
        assertEquals(afterFirstValue, evictions)

        configs.value = CONFIGURED.copy(host = "10.0.0.2")
        runCurrent()
        assertEquals(afterFirstValue + 1, evictions)

        configs.value = CONFIGURED.copy(host = "10.0.0.2", scope = ProxyScope.FORUM_ONLY)
        runCurrent()
        assertEquals(afterFirstValue + 2, evictions)
    }

    /** Android's SOCKS5 stack uses this SERVER-typed overload rather than a PROXY-typed callback. */
    @Test
    fun `the six-argument SOCKS5 callback receives the configured credential`() = runTest {
        val live = LiveProxyConfig(
            backgroundScope,
            MutableStateFlow(CONFIGURED.copy(username = "someone", password = "secret")),
        )
        runCurrent()
        try {
            Authenticator.setDefault(AppSocksAuthenticator(live))

            val credential = Authenticator.requestPasswordAuthentication(
                "127.0.0.1",
                InetAddress.getLoopbackAddress(),
                7890,
                "SOCKS5",
                "SOCKS authentication",
                null,
            )
            assertEquals("someone", credential?.userName)
            assertArrayEquals("secret".toCharArray(), credential?.password)

            val unrelated = Authenticator.requestPasswordAuthentication(
                "127.0.0.1",
                InetAddress.getLoopbackAddress(),
                7890,
                "HTTP",
                "Server authentication",
                "Basic",
            )
            assertNull(unrelated)
        } finally {
            Authenticator.setDefault(null)
        }
    }

    /**
     * The authenticator is process-wide, so a SOCKS5 handshake with a proxy the app did *not*
     * configure — a VPN's declared one, say — also lands here. The credential belongs to the
     * configured proxy alone; any other asker, or one that does not name itself, gets nothing.
     */
    @Test
    fun `the SOCKS5 credential is only offered to the configured proxy`() = runTest {
        val live = LiveProxyConfig(
            backgroundScope,
            MutableStateFlow(CONFIGURED.copy(username = "someone", password = "secret")),
        )
        runCurrent()
        try {
            Authenticator.setDefault(AppSocksAuthenticator(live))

            val otherHost = Authenticator.requestPasswordAuthentication(
                "10.0.0.9",
                InetAddress.getLoopbackAddress(),
                7890,
                "SOCKS5",
                "SOCKS authentication",
                null,
            )
            assertNull(otherHost)

            val otherPort = Authenticator.requestPasswordAuthentication(
                "127.0.0.1",
                InetAddress.getLoopbackAddress(),
                1080,
                "SOCKS5",
                "SOCKS authentication",
                null,
            )
            assertNull(otherPort)

            val anonymous = Authenticator.requestPasswordAuthentication(
                null,
                InetAddress.getLoopbackAddress(),
                7890,
                "SOCKS5",
                "SOCKS authentication",
                null,
            )
            assertNull(anonymous)
        } finally {
            Authenticator.setDefault(null)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.selectorFor(
    config: ProxyConfig,
    kind: ProxyClientKind,
    platform: ProxySelector? = RecordingProxySelector(),
): AppProxySelector {
    val live = LiveProxyConfig(backgroundScope, MutableStateFlow(config))
    runCurrent()
    return AppProxySelector(live, kind, platform)
}

/**
 * Stands in for the selector Android installs.
 *
 * Handed over explicitly rather than left to [ProxySelector.getDefault], which on the JVM these tests
 * run on answers for the build machine's network — that would make "fell through to the platform" and
 * "answered direct" the same assertion, which is the one distinction here worth keeping.
 */
private class RecordingProxySelector : ProxySelector() {
    var failures = 0
        private set

    override fun select(uri: URI): List<Proxy> = listOf(PLATFORM_PROXY)

    override fun connectFailed(uri: URI, socketAddress: SocketAddress, exception: IOException) {
        failures++
    }
}
