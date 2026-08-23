package io.github.nodyssey.data.dns

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
 * worth asserting is the encoding: which key holds what, and what a store nothing has written reads
 * as — the state every installed device is in the first time this screen is opened.
 */
class DohSettingsTest {
    @get:Rule
    val store = PreferenceStoreScope("dns")

    private val settings by lazy { DataStoreDohSettings(store.dataStore) }

    private val saved = DohConfig(
        enabled = true,
        provider = DohProvider.CUSTOM,
        customUrl = "https://doh.example/dns-query",
        customBootstrap = "10.0.0.53",
        includeIPv6 = false,
        fallbackToSystem = true,
    )

    /** A fresh install resolves the way it always did, through a preset nobody has had to pick yet. */
    @Test
    fun `a store nothing has written reads as the defaults`() = runTest {
        val config = settings.config.first()

        assertEquals(DohConfig(), config)
        assertEquals(false, config.enabled)
        // Absent is not false: the default asks for both record types, the way a system resolver does.
        assertEquals(true, config.includeIPv6)
    }

    @Test
    fun `the whole configuration survives a round trip`() = runTest {
        settings.save(saved)

        assertEquals(saved, settings.config.first())
    }

    /** 主开关 writes as it is tapped, so it has to leave the server beside it alone. */
    @Test
    fun `setEnabled flips the flag and nothing else`() = runTest {
        settings.save(saved)

        settings.setEnabled(false)

        assertEquals(saved.copy(enabled = false), settings.config.first())
    }

    /** A provider this build no longer knows about reads as the default rather than as a crash. */
    @Test
    fun `an unknown provider falls back to the default one`() = runTest {
        settings.save(saved.copy(provider = DohProvider.GOOGLE))
        store.dataStore.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                set(androidx.datastore.preferences.core.stringPreferencesKey("provider"), "OPENDNS")
            }
        }

        assertEquals(DohConfig().provider, settings.config.first().provider)
    }
}
