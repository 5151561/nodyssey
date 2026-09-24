package io.github.nodyssey.data.dns

import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nodyssey.data.PreferenceStoreScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The stored half of 加密 DNS.
 *
 * Nothing here is a secret, so unlike the proxy's password there is no cipher in the way and what is
 * worth asserting is the encoding: that the list keeps its order and its edits, what a store nothing
 * has written reads as, and what a store written by the single-server version reads as — the state
 * every device that had this turned on is in the first time the new screen opens.
 */
class DohSettingsTest {
    @get:Rule
    val store = PreferenceStoreScope("dns")

    private val settings by lazy { DataStoreDohSettings(store.dataStore) }

    private suspend fun writeRaw(vararg entries: Pair<String, String>) {
        store.dataStore.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                entries.forEach { (key, value) -> set(stringPreferencesKey(key), value) }
            }
        }
    }

    @Test
    fun `a store nothing has written reads as the defaults`() = runTest {
        val config = settings.config.first()

        assertEquals(DohConfig(), config)
        // Absent is not false: the default asks for both record types, the way a system resolver does.
        assertEquals(true, config.includeIPv6)
    }

    /** Order, ticks, a preset's overridden addresses and a typed server all come back as they went in. */
    @Test
    fun `the whole configuration survives a round trip`() = runTest {
        val saved = DohConfig(
            enabled = true,
            servers = listOf(
                DohServer(id = "custom-2", customUrl = "https://doh.example/dns-query", bootstrapOverride = "10.0.0.53", checked = true),
                DohServer.preset(DohProvider.GOOGLE, checked = true).copy(bootstrapOverride = "8.8.8.8"),
                DohServer.preset(DohProvider.ALIDNS),
                DohServer.preset(DohProvider.DNSPOD),
                DohServer.preset(DohProvider.CLOUDFLARE),
            ),
            includeIPv6 = false,
            fallbackToSystem = true,
        )

        settings.save(saved)

        assertEquals(saved, settings.config.first())
    }

    /**
     * A preset this build no longer ships reads as gone rather than as a crash, and one it ships that
     * the stored list has never seen is offered unticked at the end rather than silently asked.
     */
    @Test
    fun `a stored list drops presets this build lacks and gains ones it has never seen`() = runTest {
        writeRaw(
            "servers" to
                """[{"id":"OPENDNS","preset":"OPENDNS","checked":true},{"id":"DNSPOD","preset":"DNSPOD","checked":true}]""",
        )

        val servers = settings.config.first().servers

        assertEquals(listOf("DNSPOD", "ALIDNS", "CLOUDFLARE", "GOOGLE"), servers.map { it.id })
        assertEquals(listOf("DNSPOD"), servers.filter { it.checked }.map { it.id })
    }

    /** Someone who picked Google in the single-server version is still asking Google, and only Google. */
    @Test
    fun `a preset chosen before the list existed stays the one that is asked`() = runTest {
        writeRaw("provider" to "GOOGLE")

        val config = settings.config.first()

        assertEquals(listOf("GOOGLE"), config.chain.map { it.id })
        assertEquals("GOOGLE", config.servers.first().id)
    }

    @Test
    fun `a custom server typed before the list existed becomes a row of its own`() = runTest {
        writeRaw("provider" to "CUSTOM", "custom_url" to "https://doh.example/dns-query", "custom_bootstrap" to "10.0.0.53")

        val chain = settings.config.first().chain

        assertEquals(listOf("https://doh.example/dns-query"), chain.map { it.url })
        assertEquals(listOf("10.0.0.53"), chain.single().bootstrap)
    }

    /** Typed but not chosen is still typed: it stays in the list, unticked, rather than being lost. */
    @Test
    fun `a custom address that was not the chosen server is kept unticked`() = runTest {
        writeRaw("provider" to "ALIDNS", "custom_url" to "https://doh.example/dns-query")

        val servers = settings.config.first().servers

        assertEquals(listOf("ALIDNS"), servers.filter { it.checked }.map { it.id })
        assertEquals(false, servers.single { it.preset == null }.checked)
    }

    @Test
    fun `an unknown provider from before the list existed reads as the defaults`() = runTest {
        writeRaw("provider" to "OPENDNS")

        assertEquals(DefaultDohServers, settings.config.first().servers)
    }
}
