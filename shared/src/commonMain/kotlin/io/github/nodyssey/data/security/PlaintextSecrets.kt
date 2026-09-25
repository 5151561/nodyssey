package io.github.nodyssey.data.security

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * A [SecretCipher] that keeps the secret as it is, for a store the platform keeps out of backups
 * instead.
 *
 * The exposure [SecretCipher] exists for is the backup, not the disk: a DataStore file sits where only
 * this app can read it. Encryption answers that on Android. On iOS the answer used to be the Keychain,
 * and the Keychain needs a signing identity that a self-signed install of this app does not have (see
 * `KeychainLegacySecrets` in `:iosapp`), so there the store holding the secret is put in a directory
 * excluded from backup and the secret goes into it as typed.
 *
 * [readLegacy] is what an earlier build left behind: a marked value names a secret some other cipher
 * holds, and is handed to it. An unmarked value is the secret itself.
 */
class PlaintextSecretCipher(
    private val readLegacy: (stored: String) -> String = { "" },
) : SecretCipher {
    override fun encrypt(plaintext: String): String = plaintext

    override fun decrypt(stored: String): String = if (SecretCipher.isEncrypted(stored)) readLegacy(stored) else stored
}

/**
 * Replaces each marked value in a store with the secret it names, once.
 *
 * The counterpart of a store moving to [PlaintextSecretCipher]. [PlaintextSecretCipher] can already
 * read a marked value back, so this is not what keeps an install working; it is what lets the old
 * cipher be retired, rather than consulted on every read for as long as the app is installed.
 *
 * A marked value [readLegacy] cannot resolve is left where it is. On iOS that is either an item the
 * Keychain has not got — a re-signed install whose access group changed, which will never resolve —
 * or a device that was still locked when a background task opened the store, which will resolve on
 * the next launch. Dropping it would lose the second to spare a Keychain query for the first. The next
 * save writes over it either way.
 */
class LegacySecretMigration(
    private val readLegacy: (stored: String) -> String,
) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData.markedSecrets().isNotEmpty()

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply {
            for ((name, stored) in currentData.markedSecrets()) {
                val secret = readLegacy(stored)
                if (secret.isNotEmpty()) set(stringPreferencesKey(name), secret)
            }
        }

    override suspend fun cleanUp() = Unit

    // Every string that carries the marker, whatever its key: only a cipher ever writes one, so the
    // marker is a complete list of the secrets in a store without this class having to be told them.
    private fun Preferences.markedSecrets(): List<Pair<String, String>> =
        asMap().mapNotNull { (key, value) ->
            (value as? String)?.takeIf(SecretCipher::isEncrypted)?.let { key.name to it }
        }
}
