package io.github.nodyssey.core.net

import io.github.nodyssey.data.proxy.ProxyConfig
import io.github.nodyssey.data.proxy.ProxyScope
import io.github.nodyssey.data.proxy.ProxyType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

private val FORUM = URI("https://www.nodeseek.com/categories/daily")
private val IMAGE_HOST = URI("https://api.nodeimage.com/api/upload")

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
    @Test
    fun `everything is direct while the proxy is off`() = runTest {
        val selector = selectorFor(CONFIGURED.copy(enabled = false), ProxyClientKind.FORUM)

        assertEquals(listOf(Proxy.NO_PROXY), selector.select(FORUM))
    }

    /** An address that is still being typed is not an address to send requests to. */
    @Test
    fun `a half-typed address is direct even with the switch on`() = runTest {
        val selector = selectorFor(CONFIGURED.copy(port = 0), ProxyClientKind.FORUM)

        assertEquals(listOf(Proxy.NO_PROXY), selector.select(FORUM))
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
    fun `forum-only leaves the third-party clients direct and the forum proxied`() = runTest {
        val config = CONFIGURED.copy(scope = ProxyScope.FORUM_ONLY)

        assertEquals(listOf(Proxy.NO_PROXY), selectorFor(config, ProxyClientKind.THIRD_PARTY).select(IMAGE_HOST))
        assertTrue(selectorFor(config, ProxyClientKind.FORUM).select(FORUM).single() != Proxy.NO_PROXY)
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
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.selectorFor(
    config: ProxyConfig,
    kind: ProxyClientKind,
): AppProxySelector {
    val live = LiveProxyConfig(backgroundScope, MutableStateFlow(config))
    runCurrent()
    return AppProxySelector(live, kind)
}
