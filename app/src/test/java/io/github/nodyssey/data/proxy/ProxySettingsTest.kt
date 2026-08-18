package io.github.nodyssey.data.proxy

import io.github.nodyssey.data.PreferenceStoreScope
import io.github.nodyssey.data.security.PlainCipher
import io.github.nodyssey.data.security.ReversingCipher
import io.github.nodyssey.data.security.SecretCipher
import io.github.nodyssey.data.security.UnencryptableCipher
import io.github.nodyssey.data.security.UnreadableCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The stored half of 代理设置, and in particular what the password looks like on disk.
 *
 * The real cipher is the platform keystore, which no JVM test has — so these drive the same code with
 * ciphers whose behaviour is stated rather than provisioned: one that transforms, one that cannot
 * encrypt, one that cannot decrypt. Where a test builds a second settings object, it is on the same
 * store: reading a file back with a different cipher is how "what actually landed on disk" becomes
 * something a test can assert on.
 */
class ProxySettingsTest {
    @get:Rule
    val store = PreferenceStoreScope("proxy")

    private val settings = { cipher: SecretCipher -> DataStoreProxySettings(store.dataStore, cipher) }

    private val saved = ProxyConfig(
        enabled = true,
        type = ProxyType.SOCKS,
        host = "127.0.0.1",
        port = 7890,
        username = "someone",
        password = "hunter2",
    )

    /** Nothing written is not the same as something written empty; this is what a fresh install reads. */
    @Test
    fun `a store nothing has written reads as the defaults`() = runTest {
        assertEquals(ProxyConfig(), settings(ReversingCipher).config.first())
    }

    @Test
    fun `the whole configuration survives a round trip`() = runTest {
        settings(ReversingCipher).save(saved.copy(scope = ProxyScope.FORUM_ONLY))

        assertEquals(saved.copy(scope = ProxyScope.FORUM_ONLY), settings(ReversingCipher).config.first())
    }

    /** 主开关 writes as it is tapped, so it has to leave the address and the ciphertext beside it alone. */
    @Test
    fun `setEnabled flips the flag and nothing else`() = runTest {
        val settings = DataStoreProxySettings(store.dataStore, ReversingCipher)
        settings.save(saved)

        settings.setEnabled(false)

        assertEquals(saved.copy(enabled = false), settings.config.first())
    }

    /** The point of the cipher: what a backup or a file browser would find is not the password. */
    @Test
    fun `the password is stored encrypted and everything else is stored as typed`() = runTest {
        settings(ReversingCipher).save(saved)

        // Reading the same file back with a cipher that does nothing shows what actually landed on disk.
        val onDisk = settings(PlainCipher).config.first()
        assertTrue("the stored value must say it is ciphertext", SecretCipher.isEncrypted(onDisk.password))
        assertEquals(SecretCipher.MARKER + "2retnuh", onDisk.password)
        assertEquals("127.0.0.1", onDisk.host)
        assertEquals("someone", onDisk.username)
    }

    /**
     * A restored backup, or a keystore the system reset, leaves a value nothing on this device can
     * read. The address and the port are still worth having; the password is simply gone.
     */
    @Test
    fun `an unreadable password reads as no password, and the rest still loads`() = runTest {
        settings(ReversingCipher).save(saved)

        val recovered = settings(UnreadableCipher).config.first()
        assertEquals("", recovered.password)
        assertEquals(saved.copy(password = ""), recovered)
    }

    /** Encryption failing is not a reason to fall back to plaintext. */
    @Test
    fun `a device that cannot encrypt stores no password at all`() = runTest {
        settings(UnencryptableCipher).save(saved)

        assertEquals("", settings(PlainCipher).config.first().password)
    }
}
