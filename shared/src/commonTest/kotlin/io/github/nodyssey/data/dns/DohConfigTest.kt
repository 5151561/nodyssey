package io.github.nodyssey.data.dns

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What 加密 DNS will and will not accept as a server, and which servers it hands the resolver.
 *
 * The validation is the part worth pinning down: a URL that reaches storage half-typed is a resolver
 * the app cannot use and cannot explain, and everything below it — the clients, the fallback switch —
 * is written as if every server in [DohConfig.chain] is usable.
 */
class DohConfigTest {
    private fun custom(
        id: String,
        url: String,
        checked: Boolean = true,
        bootstrap: String = "",
    ) = DohServer(id = id, customUrl = url, bootstrapOverride = bootstrap, checked = checked)

    @Test
    fun `a preset answers with its own server and bootstrap addresses until they are overridden`() {
        val preset = DohServer.preset(DohProvider.ALIDNS)

        assertEquals(DohProvider.ALIDNS.url, preset.url)
        assertEquals(DohProvider.ALIDNS.bootstrap, preset.bootstrap)
        assertEquals(listOf("223.5.5.5"), preset.copy(bootstrapOverride = "223.5.5.5").bootstrap)
    }

    /** Every preset has to be reachable without a resolver, which is the whole reason they are presets. */
    @Test
    fun `every preset carries a usable url and at least one bootstrap address`() {
        DohProvider.entries.forEach { provider ->
            assertTrue(DohServer.preset(provider).isUsable, "${provider.name} url")
            assertTrue(provider.bootstrap.isNotEmpty(), "${provider.name} bootstrap")
            assertTrue(provider.bootstrap.all(::isIpLiteral), "${provider.name} bootstrap is addresses")
        }
    }

    /**
     * The chain is what `AppDns` walks and what Apple takes the head of: the order is the list's, an
     * unticked server is never asked, and neither is one whose address cannot be used.
     */
    @Test
    fun `the chain is the ticked usable servers in list order`() {
        val config = DohConfig(
            enabled = true,
            servers = listOf(
                DohServer.preset(DohProvider.GOOGLE, checked = true),
                DohServer.preset(DohProvider.ALIDNS, checked = false),
                custom("custom-1", "https://doh.example/dns-query"),
                custom("custom-2", "http://plain.example/dns-query"),
                DohServer.preset(DohProvider.DNSPOD, checked = true),
            ),
        )

        assertEquals(listOf("GOOGLE", "custom-1", "DNSPOD"), config.chain.map { it.id })
    }

    @Test
    fun `a switched-off config is never wrong however it is set`() {
        val nothingTicked = DohConfig(enabled = false, servers = DefaultDohServers.map { it.copy(checked = false) })

        assertNull(nothingTicked.problem())
    }

    @Test
    fun `an enabled config needs at least one server it can ask`() {
        val nothingTicked = DohConfig(enabled = true, servers = DefaultDohServers.map { it.copy(checked = false) })

        assertEquals(DohConfigProblem.NO_SERVER, nothingTicked.problem())
        assertFalse(nothingTicked.resolvesOverHttps())
        assertTrue(DohConfig(enabled = true).resolvesOverHttps())
        assertFalse(DohConfig(enabled = false).resolvesOverHttps())
    }

    @Test
    fun `a custom server has to be typed before it can be kept`() {
        assertEquals(DohServerProblem.MISSING_URL, dohServerProblem(url = "   ", bootstrap = ""))
    }

    /** Plain HTTP is the arrangement this setting exists to leave, so it is refused rather than warned about. */
    @Test
    fun `http and nonsense are both refused`() {
        assertEquals(DohServerProblem.INVALID_URL, dohServerProblem(url = "http://doh.example/dns-query", bootstrap = ""))
        assertEquals(DohServerProblem.INVALID_URL, dohServerProblem(url = "doh.example/dns-query", bootstrap = ""))
        assertEquals(DohServerProblem.INVALID_URL, dohServerProblem(url = "https://", bootstrap = ""))
    }

    @Test
    fun `a custom server without a bootstrap address is allowed`() {
        assertNull(dohServerProblem(url = "https://doh.example/dns-query", bootstrap = ""))
        assertEquals(emptyList(), custom("custom-1", "https://doh.example/dns-query").bootstrap)
    }

    @Test
    fun `bootstrap addresses are split on commas and spaces`() {
        val server = custom("custom-1", "https://doh.example/dns-query", bootstrap = "223.5.5.5, 223.6.6.6 2400:3200::1")

        assertEquals(listOf("223.5.5.5", "223.6.6.6", "2400:3200::1"), server.bootstrap)
    }

    /**
     * A typo dropped silently would be a server that quietly needs the system resolver after all, on
     * the one screen whose whole subject is not needing it. A preset's addresses are checked the same
     * way, since they can be typed over too.
     */
    @Test
    fun `a bootstrap entry that is not an address fails the whole field`() {
        assertEquals(
            DohServerProblem.INVALID_BOOTSTRAP,
            dohServerProblem(url = "https://doh.example/dns-query", bootstrap = "223.5.5.5, doh.pub"),
        )
        assertEquals(DohServerProblem.INVALID_BOOTSTRAP, dohServerProblem(url = null, bootstrap = "doh.pub"))
    }

    /** Two custom servers sharing an id would be edited, reordered and deleted as one. */
    @Test
    fun `a new custom server never reuses an id`() {
        assertEquals("custom-1", DefaultDohServers.nextCustomId())
        assertEquals(
            "custom-4",
            listOf(custom("custom-1", "https://a.example/q"), custom("custom-3", "https://b.example/q")).nextCustomId(),
        )
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
