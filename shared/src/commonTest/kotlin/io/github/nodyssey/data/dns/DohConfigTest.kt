package io.github.nodyssey.data.dns

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What 加密 DNS will and will not accept as a server, and what it hands the resolver once it has one.
 *
 * The validation is the part worth pinning down: a URL that reaches storage half-typed is a resolver
 * the app cannot use and cannot explain, and everything below it — the clients, the fallback switch —
 * is written as if `serverUrl` is either usable or off.
 */
class DohConfigTest {
    private val custom = DohConfig(enabled = true, provider = DohProvider.CUSTOM)

    @Test
    fun `a preset answers with its own server and bootstrap addresses`() {
        val config = DohConfig(enabled = true, provider = DohProvider.ALIDNS, customUrl = "https://typed.example/x")

        assertEquals(DohProvider.ALIDNS.url, config.serverUrl)
        assertEquals(DohProvider.ALIDNS.bootstrap, config.bootstrap)
        assertNull(config.problem())
    }

    /** Every preset has to be reachable without a resolver, which is the whole reason they are presets. */
    @Test
    fun `every preset carries a usable url and at least one bootstrap address`() {
        DohProvider.entries.filter { it != DohProvider.CUSTOM }.forEach { provider ->
            assertTrue(DohConfig(enabled = true, provider = provider).isUsable, "${provider.name} url")
            assertTrue(provider.bootstrap.isNotEmpty(), "${provider.name} bootstrap")
            assertTrue(provider.bootstrap.all(::isIpLiteral), "${provider.name} bootstrap is addresses")
        }
    }

    @Test
    fun `a switched-off config is never wrong however it is typed`() {
        assertNull(custom.copy(enabled = false, customUrl = "not a url").problem())
    }

    @Test
    fun `a custom server has to be typed before it can be saved`() {
        assertEquals(DohConfigProblem.MISSING_URL, custom.copy(customUrl = "   ").problem())
    }

    /** Plain HTTP is the arrangement this setting exists to leave, so it is refused rather than warned about. */
    @Test
    fun `http and nonsense are both refused`() {
        assertEquals(DohConfigProblem.INVALID_URL, custom.copy(customUrl = "http://doh.example/dns-query").problem())
        assertEquals(DohConfigProblem.INVALID_URL, custom.copy(customUrl = "doh.example/dns-query").problem())
        assertEquals(DohConfigProblem.INVALID_URL, custom.copy(customUrl = "https://").problem())
    }

    @Test
    fun `a custom server without a bootstrap address is allowed`() {
        val config = custom.copy(customUrl = "https://doh.example/dns-query")

        assertNull(config.problem())
        assertEquals(emptyList(), config.bootstrap)
    }

    @Test
    fun `bootstrap addresses are split on commas and spaces`() {
        val config = custom.copy(
            customUrl = "https://doh.example/dns-query",
            customBootstrap = "223.5.5.5, 223.6.6.6 2400:3200::1",
        )

        assertNull(config.problem())
        assertEquals(listOf("223.5.5.5", "223.6.6.6", "2400:3200::1"), config.bootstrap)
    }

    /**
     * A typo dropped silently would be a server that quietly needs the system resolver after all, on
     * the one screen whose whole subject is not needing it.
     */
    @Test
    fun `a bootstrap entry that is not an address fails the whole field`() {
        val config = custom.copy(customUrl = "https://doh.example/dns-query", customBootstrap = "223.5.5.5, doh.pub")

        assertEquals(DohConfigProblem.INVALID_BOOTSTRAP, config.problem())
        assertEquals(emptyList(), config.bootstrap)
    }

    @Test
    fun `resolving over https needs both the switch and a usable server`() {
        assertFalse(DohConfig(enabled = false, provider = DohProvider.ALIDNS).resolvesOverHttps())
        assertFalse(custom.copy(customUrl = "").resolvesOverHttps())
        assertTrue(DohConfig(enabled = true, provider = DohProvider.ALIDNS).resolvesOverHttps())
    }

    /**
     * The half of this that is load-bearing outside the screen: `AppDns` asks it before sending a name
     * to a DoH server, because a server asked to resolve `127.0.0.1` answers that no such name exists.
     */
    @Test
    fun `an address is recognised as an address`() {
        listOf("127.0.0.1", "8.8.8.8", "223.5.5.5", "::1", "2400:3200::1", "::ffff:192.168.0.1", "fe80::1%wlan0")
            .forEach { assertTrue(isIpLiteral(it), it) }

        listOf("", "doh.pub", "www.nodeseek.com", "1.2.3", "1.2.3.4.5", "256.1.1.1", "localhost", "1.2.3.x")
            .forEach { assertFalse(isIpLiteral(it), it) }
    }
}
