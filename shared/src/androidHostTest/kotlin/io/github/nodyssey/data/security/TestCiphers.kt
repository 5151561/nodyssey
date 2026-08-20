package io.github.nodyssey.data.security

/**
 * Ciphers whose behaviour is stated rather than provisioned.
 *
 * The real one is [KeystoreSecretCipher], and a JVM test has no platform keystore to give it — it
 * fails every call and stores nothing, which would make these tests assert the wrong thing about the
 * wrong code. These four each pin down one behaviour the storage code has to handle: a value that
 * round-trips, a device that cannot encrypt, a value that cannot be read back, and — for looking at
 * what actually landed on disk — no encryption at all.
 */
internal object ReversingCipher : SecretCipher {
    override fun encrypt(plaintext: String) =
        if (plaintext.isEmpty()) "" else SecretCipher.MARKER + plaintext.reversed()

    override fun decrypt(stored: String) =
        if (SecretCipher.isEncrypted(stored)) stored.removePrefix(SecretCipher.MARKER).reversed() else ""
}

/** Stores what it is given, unmarked — which is exactly the shape of a value written before this. */
internal object PlainCipher : SecretCipher {
    override fun encrypt(plaintext: String) = plaintext

    override fun decrypt(stored: String) = stored
}

/** A restored backup, or a keystore the system reset: the marker is there, the key is not. */
internal object UnreadableCipher : SecretCipher {
    override fun encrypt(plaintext: String) = SecretCipher.MARKER + plaintext

    override fun decrypt(stored: String) = ""
}

internal object UnencryptableCipher : SecretCipher {
    override fun encrypt(plaintext: String): String? = null

    override fun decrypt(stored: String) = stored
}
