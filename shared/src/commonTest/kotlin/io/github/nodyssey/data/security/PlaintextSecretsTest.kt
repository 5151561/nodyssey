package io.github.nodyssey.data.security

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The iOS stores' move off the Keychain: the secrets an earlier build saved have to survive it, and
 * nothing that was not a secret may be touched on the way.
 */
class PlaintextSecretsTest {
    private val token = stringPreferencesKey("nodeimage.token")
    private val password = stringPreferencesKey("password")
    private val host = stringPreferencesKey("host")
    private val port = intPreferencesKey("port")

    /** What the Keychain held: one handle it can answer, and nothing for any other. */
    private val keychain = mapOf("${SecretCipher.MARKER}handle-a" to "tok-123")

    private fun readLegacy(stored: String) = keychain[stored].orEmpty()

    @Test
    fun `a secret the old store can read is written back as typed`() = runTest {
        val before = preferencesOf(token to "${SecretCipher.MARKER}handle-a", host to "proxy.example", port to 7890)

        val after = LegacySecretMigration(::readLegacy).migrate(before)

        assertEquals("tok-123", after[token])
        assertEquals("proxy.example", after[host])
        assertEquals(7890, after[port])
    }

    // A handle the Keychain cannot answer now may answer later — the items were `WhenUnlocked`, so a
    // background launch on a locked phone reads nothing — and the migration must not erase it.
    @Test
    fun `a handle the old store cannot read is kept for the next launch`() = runTest {
        val before = preferencesOf(password to "${SecretCipher.MARKER}handle-gone")

        val after = LegacySecretMigration(::readLegacy).migrate(before)

        assertEquals("${SecretCipher.MARKER}handle-gone", after[password])
    }

    @Test
    fun `a store with no handles in it is left alone`() = runTest {
        val plain = preferencesOf(token to "tok-123", host to "proxy.example")

        assertFalse(LegacySecretMigration(::readLegacy).shouldMigrate(plain))
    }

    // The proxy store calls `decrypt` on every value, marked or not, and a handle that came back as
    // itself would be sent to the proxy as the password.
    @Test
    fun `the cipher resolves a handle and hands a typed secret back unchanged`() {
        val cipher = PlaintextSecretCipher(::readLegacy)

        assertEquals("tok-123", cipher.decrypt("${SecretCipher.MARKER}handle-a"))
        assertEquals("", cipher.decrypt("${SecretCipher.MARKER}handle-gone"))
        assertEquals("hunter2", cipher.decrypt(cipher.encrypt("hunter2")))
    }
}
