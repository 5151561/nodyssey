package io.github.nodyssey.data.security

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

    /**
     * Empty when [stored] cannot be read back — see `KeystoreSecretCipher` for when that happens.
     *
     * That implementation lives in `platform/` rather than beside this interface: a keystore is a
     * device, and what this layer needs to know about encryption is only that it can fail.
     */
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
