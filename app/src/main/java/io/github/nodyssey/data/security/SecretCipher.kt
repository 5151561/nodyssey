package io.github.nodyssey.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts one short secret at a time, so a credential can live in DataStore without living there in
 * plaintext.
 *
 * DataStore files sit in the app's own directory, which nothing but this app and root can read — but
 * they are also inside the cloud backup, which `data_extraction_rules.xml` only excludes the Room
 * database and the WebView cookie store from. A proxy password left in the clear would therefore be
 * copied off the device the moment the user's phone backs itself up.
 */
interface SecretCipher {
    /** Empty for empty input; `null` when this device could not encrypt, which callers store as nothing. */
    fun encrypt(plaintext: String): String?

    /** Empty when [stored] cannot be read back — see [KeystoreSecretCipher] for when that happens. */
    fun decrypt(stored: String): String

    companion object {
        /**
         * Stamped on everything [encrypt] produces, so a stored value can say which of the two it is.
         *
         * Ciphertext here is base64, and so is many a plain API key — telling them apart by trying to
         * decrypt would mean reading one failure as both "an old plaintext value" and "ciphertext this
         * device cannot read". Those two need opposite handling: the first is a credential to keep,
         * the second is one to forget. A marker is what makes the question answerable at all.
         */
        const val MARKER = "enc1:"

        fun isEncrypted(stored: String): Boolean = stored.startsWith(MARKER)
    }
}

/**
 * AES-GCM under a key held by the platform keystore, where the key material itself is never readable
 * by this process and never leaves the device.
 *
 * That last part is the reason [decrypt] answers an unreadable value with an empty string rather than
 * an exception: a restored backup carries the ciphertext to a phone whose keystore has no key for it,
 * and so does a keystore the system reset. Both are ordinary events, and both mean the same thing to
 * the user — the address and port are still there, the password has to be typed again.
 */
class KeystoreSecretCipher(private val alias: String = DEFAULT_ALIAS) : SecretCipher {
    override fun encrypt(plaintext: String): String? {
        if (plaintext.isEmpty()) return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val ciphertext = cipher.doFinal(plaintext.toByteArray())
            // The IV is generated per encryption by the cipher itself and is not a secret; prefixing it
            // keeps a stored value self-describing, so decryption needs nothing but the key.
            SecretCipher.MARKER + Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
        }.getOrNull()
    }

    override fun decrypt(stored: String): String {
        if (!SecretCipher.isEncrypted(stored)) return ""
        return runCatching {
            val bytes = Base64.decode(stored.removePrefix(SecretCipher.MARKER), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
        }.getOrDefault("")
    }

    /** Created on first use and kept by the keystore from then on; nothing here holds it in a field. */
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // No user-authentication requirement: the forum's own requests run while the screen is
                // off, and a proxy the app cannot reach without an unlock is a proxy that breaks
                // background refreshes.
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val DEFAULT_ALIAS = "nodyssey.secret.v1"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
