package io.github.nodyssey.data.proxy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.nodyssey.data.security.PlainCipher
import io.github.nodyssey.data.security.ReversingCipher
import io.github.nodyssey.data.security.SecretCipher
import io.github.nodyssey.data.security.UnencryptableCipher
import io.github.nodyssey.data.security.UnreadableCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The stored half of 代理设置, and in particular what the password looks like on disk.
 *
 * The real cipher is the platform keystore, which no JVM test has — so these drive the same code with
 * ciphers whose behaviour is stated rather than provisioned: one that transforms, one that cannot
 * encrypt, one that cannot decrypt. Every test writes what it needs before reading it: DataStore is a
 * process singleton keyed by file name, and Robolectric hands every test in this class the same
 * application context.
 */
@RunWith(RobolectricTestRunner::class)
class ProxySettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val saved = ProxyConfig(
        enabled = true,
        type = ProxyType.SOCKS,
        host = "127.0.0.1",
        port = 7890,
        username = "someone",
        password = "hunter2",
    )

    @Test
    fun `the whole configuration survives a round trip`() = runTest {
        val settings = DataStoreProxySettings(context, ReversingCipher)
        settings.save(saved.copy(scope = ProxyScope.FORUM_ONLY))

        assertEquals(saved.copy(scope = ProxyScope.FORUM_ONLY), settings.config.first())
    }

    /** 主开关 writes as it is tapped, so it has to leave the address and the ciphertext beside it alone. */
    @Test
    fun `setEnabled flips the flag and nothing else`() = runTest {
        val settings = DataStoreProxySettings(context, ReversingCipher)
        settings.save(saved)

        settings.setEnabled(false)

        assertEquals(saved.copy(enabled = false), settings.config.first())
    }

    /** The point of the cipher: what a backup or a file browser would find is not the password. */
    @Test
    fun `the password is stored encrypted and everything else is stored as typed`() = runTest {
        DataStoreProxySettings(context, ReversingCipher).save(saved)

        // Reading the same file back with a cipher that does nothing shows what actually landed on disk.
        val onDisk = DataStoreProxySettings(context, PlainCipher).config.first()
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
        DataStoreProxySettings(context, ReversingCipher).save(saved)

        val recovered = DataStoreProxySettings(context, UnreadableCipher).config.first()
        assertEquals("", recovered.password)
        assertEquals(saved.copy(password = ""), recovered)
    }

    /** Encryption failing is not a reason to fall back to plaintext. */
    @Test
    fun `a device that cannot encrypt stores no password at all`() = runTest {
        DataStoreProxySettings(context, UnencryptableCipher).save(saved)

        assertEquals("", DataStoreProxySettings(context, PlainCipher).config.first().password)
    }
}
