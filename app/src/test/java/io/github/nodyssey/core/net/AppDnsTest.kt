package io.github.nodyssey.core.net

import io.github.nodyssey.data.dns.DohConfig
import io.github.nodyssey.data.dns.DohProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

private val SYSTEM_ANSWER = listOf(InetAddress.getByName("10.0.0.1"))
private val DOH_ANSWER = listOf(InetAddress.getByName("104.21.32.1"))

private val ON = DohConfig(enabled = true, provider = DohProvider.ALIDNS)

/** Records what it was asked, and answers whatever it was built with. */
private class FakeDns(
    private val answer: List<InetAddress>?,
) : Dns {
    val asked = mutableListOf<String>()

    override fun lookup(hostname: String): List<InetAddress> {
        asked += hostname
        return answer ?: throw UnknownHostException(hostname)
    }
}

/**
 * Which resolver answers, and when it is rebuilt.
 *
 * The switching is the feature: a wrong answer here is either a name resolved by the resolver the
 * user turned off, or an app that resolves nothing at all — and the third case, an *address* sent to
 * a DoH server, is the one that would break every proxy configured by IP the moment this was enabled.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppDnsTest {
    private val system = FakeDns(SYSTEM_ANSWER)
    private val doh = FakeDns(DOH_ANSWER)
    private var built = 0
    private var evictions = 0

    private fun TestScope.appDns(configs: MutableStateFlow<DohConfig>, resolver: Dns = doh) =
        AppDns(
            scope = backgroundScope,
            config = configs,
            resolvers = {
                built++
                resolver
            },
            system = system,
            onResolverChanged = { evictions++ },
        ).also { runCurrent() }

    @Test
    fun `with 加密 DNS off every name goes to the platform`() = runTest {
        val dns = appDns(MutableStateFlow(DohConfig()))

        assertEquals(SYSTEM_ANSWER, dns.lookup("www.nodeseek.com"))
        assertEquals(listOf("www.nodeseek.com"), system.asked)
        assertEquals(0, built)
    }

    @Test
    fun `with it on the name goes to the DoH server`() = runTest {
        val dns = appDns(MutableStateFlow(ON))

        assertEquals(DOH_ANSWER, dns.lookup("www.nodeseek.com"))
        assertEquals(listOf("www.nodeseek.com"), doh.asked)
        assertEquals(emptyList<String>(), system.asked)
    }

    /**
     * The case this class exists for. A DoH server asked about `127.0.0.1` answers that no such name
     * exists, and a local Clash listener is the most common proxy address there is — so turning this
     * setting on would have stopped the app reaching its own proxy.
     */
    @Test
    fun `an address is never sent to a DoH server`() = runTest {
        val dns = appDns(MutableStateFlow(ON))

        assertEquals(SYSTEM_ANSWER, dns.lookup("127.0.0.1"))
        assertEquals(SYSTEM_ANSWER, dns.lookup("2400:3200::1"))
        assertEquals(emptyList<String>(), doh.asked)
    }

    /** A name no public resolver has ever heard of belongs to the resolver that knows this network. */
    @Test
    fun `a single-label name goes to the platform`() = runTest {
        val dns = appDns(MutableStateFlow(ON))

        assertEquals(SYSTEM_ANSWER, dns.lookup("localhost"))
        assertEquals(SYSTEM_ANSWER, dns.lookup("printer.local"))
        assertEquals(emptyList<String>(), doh.asked)
    }

    /**
     * The strict default, and the reason it is the default: a resolver that quietly hands the question
     * back to the one being bypassed answers with exactly the poisoned record this was turned on to
     * get away from.
     */
    @Test
    fun `a failed lookup stays failed while the fallback is off`() = runTest {
        val dns = appDns(MutableStateFlow(ON), resolver = FakeDns(answer = null))

        assertThrows(UnknownHostException::class.java) { dns.lookup("www.nodeseek.com") }
        assertEquals(emptyList<String>(), system.asked)
    }

    @Test
    fun `with the fallback on a failed lookup asks the platform instead`() = runTest {
        val dns = appDns(MutableStateFlow(ON.copy(fallbackToSystem = true)), resolver = FakeDns(answer = null))

        assertEquals(SYSTEM_ANSWER, dns.lookup("www.nodeseek.com"))
        assertEquals(listOf("www.nodeseek.com"), system.asked)
    }

    /**
     * Rebuilding the resolver throws away the connection to the DoH server and everything it has
     * cached, so only the fields the resolver was actually built from may do it.
     */
    @Test
    fun `only a change of server rebuilds the resolver`() = runTest {
        val configs = MutableStateFlow(ON)
        appDns(configs)
        assertEquals(1, built)

        configs.value = ON.copy(fallbackToSystem = true)
        runCurrent()
        assertEquals(1, built)

        configs.value = ON.copy(fallbackToSystem = true, includeIPv6 = false)
        runCurrent()
        assertEquals(2, built)

        configs.value = ON.copy(fallbackToSystem = true, includeIPv6 = false, provider = DohProvider.GOOGLE)
        runCurrent()
        assertEquals(3, built)
    }

    /** The fallback switch is read per lookup, so flipping it takes effect without a rebuild. */
    @Test
    fun `turning the fallback on reaches a resolver that was already built`() = runTest {
        val configs = MutableStateFlow(ON)
        val dns = appDns(configs, resolver = FakeDns(answer = null))

        configs.value = ON.copy(fallbackToSystem = true)
        runCurrent()

        assertEquals(SYSTEM_ANSWER, dns.lookup("www.nodeseek.com"))
        assertEquals(1, built)
    }

    /**
     * Pooled connections outlive the setting that opened them — the same reason the proxy empties the
     * pool. A connection open to an address the old resolver gave would otherwise keep carrying
     * requests, and the change would look like it had done nothing.
     */
    @Test
    fun `a change of resolver empties the connection pool and a cosmetic one does not`() = runTest {
        val configs = MutableStateFlow(DohConfig())
        appDns(configs)
        val afterFirstValue = evictions

        configs.value = ON
        runCurrent()
        assertEquals(afterFirstValue + 1, evictions)

        configs.value = ON.copy(fallbackToSystem = true)
        runCurrent()
        assertEquals(afterFirstValue + 1, evictions)

        configs.value = ON.copy(enabled = false)
        runCurrent()
        assertEquals(afterFirstValue + 2, evictions)
    }

    /** A server the platform cannot make a resolver out of leaves the platform answering. */
    @Test
    fun `an unbuildable resolver falls back to the platform rather than failing every lookup`() = runTest {
        val dns = AppDns(
            scope = backgroundScope,
            config = MutableStateFlow(ON),
            resolvers = { null },
            system = system,
        ).also { runCurrent() }

        assertEquals(SYSTEM_ANSWER, dns.lookup("www.nodeseek.com"))
    }
}
