package io.github.nodyssey.data.imagehost

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nodyssey.data.security.SecretCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import okio.IOException

/**
 * Carries a pre-existing NodeImage key into the multi-host store, once.
 *
 * Before this feature there was one host and one key, under `api-key` in its own file. An upgrade
 * that ignored it would silently disconnect everybody who had already set the app up, and the first
 * they would hear of it is a failed attachment halfway through writing a post.
 */
class LegacyNodeImageKeyMigration(
    /** Reads the old store. A parameter so this can be exercised without a DataStore at all. */
    private val readLegacyKey: suspend () -> String?,
) : DataMigration<Preferences> {
    // The selected-provider key is the marker: it is written by the migration and by every save, so
    // its absence means this store has never been used, and its presence means the copy already ran.
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[ImageHostKeys.SELECTED] == null

    override suspend fun migrate(currentData: Preferences): Preferences {
        val legacyKey = runCatching { readLegacyKey() }.getOrNull()?.trim()?.ifBlank { null }

        return currentData.toMutablePreferences().apply {
            set(ImageHostKeys.SELECTED, ImageHostProvider.DEFAULT.id)
            if (legacyKey != null) set(ImageHostKeys.token(ImageHostProvider.NODE_IMAGE), legacyKey)
        }
    }

    // The old file is left in place rather than deleted: it costs a few hundred bytes, and a
    // downgrade to the previous version then still finds the key it expects.
    override suspend fun cleanUp() = Unit
}

/**
 * Where the NodeImage key lived when nodeimage.com was the only host the app could talk to.
 *
 * Named here rather than at the store that still holds it, because [LegacyNodeImageKeyMigration] is
 * the only reader left and this is the file that says why the read happens at all.
 */
val LEGACY_NODE_IMAGE_KEY = stringPreferencesKey("api-key")

// Public rather than `internal` only because the test that pins it is still in `:app`: the
// fakes it shares with the ViewModel tests are one file, and two copies of a fake drift. Step
// D1 brings `ui/` down here and the whole test tree with it.
internal object ImageHostKeys {
    val SELECTED = stringPreferencesKey("selected")

    fun token(provider: ImageHostProvider) = stringPreferencesKey("${provider.id}.token")

    fun siteUrl(provider: ImageHostProvider) = stringPreferencesKey("${provider.id}.site-url")

    val CUSTOM_FILE_FIELD = stringPreferencesKey("custom.file-field")
    val CUSTOM_HEADER_NAME = stringPreferencesKey("custom.header-name")
    val CUSTOM_HEADER_VALUE = stringPreferencesKey("custom.header-value")
    val CUSTOM_FORM_FIELDS = stringPreferencesKey("custom.form-fields")
    val CUSTOM_URL_PATH = stringPreferencesKey("custom.url-path")
    val CUSTOM_URL_PREFIX = stringPreferencesKey("custom.url-prefix")
}

/**
 * The values in this store that are credentials rather than configuration.
 *
 * An address, a field name and a JSON path are all worth reading in plain sight when something is
 * misconfigured; a token is worth reading to nobody. The form fields are in here because the app
 * already treats them as a credential: a custom host's secret goes either in the header value or in a
 * `token=…` line among these, which is why 断开 clears both.
 */
internal object ImageHostSecretKeys {
    val all: List<Preferences.Key<String>> =
        ImageHostProvider.entries.map(ImageHostKeys::token) +
            listOf(ImageHostKeys.CUSTOM_HEADER_VALUE, ImageHostKeys.CUSTOM_FORM_FIELDS)
}

/**
 * Encrypts the credentials an existing install already has, once.
 *
 * Reading plaintext back is handled anyway — [DataStoreImageHostSettings] accepts an unmarked value
 * as the credential it is — so this migration is not what keeps those installs working. What it does
 * is get the plaintext off the disk on the first launch after the update, rather than whenever the
 * user next happens to open 图床设置 and press 保存. For most people that day never comes, and the
 * backup would keep copying the token off the device until it did.
 */
class ImageHostSecretEncryptionMigration(private val cipher: SecretCipher) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        ImageHostSecretKeys.all.any { currentData[it].orEmpty().isPlaintextSecret() }

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply {
            for (key in ImageHostSecretKeys.all) {
                val stored = currentData[key].orEmpty()
                if (!stored.isPlaintextSecret()) continue
                // A keystore that refuses leaves the value alone: still readable, still plaintext, and
                // tried again on the next launch. Dropping it would disconnect a working host.
                set(key, cipher.encrypt(stored) ?: continue)
            }
        }

    override suspend fun cleanUp() = Unit
}

private fun String.isPlaintextSecret() = isNotEmpty() && !SecretCipher.isEncrypted(this)

/** The stored side of 图床设置. Split from [ImageHostRepository] so the HTTP half can be faked alone. */
interface ImageHostSettings {
    val selected: Flow<ImageHostProvider>

    /** The stored configuration for one host, whether or not it is the selected one. */
    fun config(provider: ImageHostProvider): Flow<ImageHostConfig>

    suspend fun select(provider: ImageHostProvider)

    suspend fun save(config: ImageHostConfig)

    /** Forgets one host's credentials. The selection is left alone — a disconnected host stays chosen. */
    suspend fun disconnect(provider: ImageHostProvider)
}

/**
 * Which host is selected, and the credentials for each.
 *
 * All six live in one store, keyed by [ImageHostProvider.id], so switching hosts does not mean
 * re-pasting a token: the one that was there before is still there when the user switches back.
 * That is the whole reason the configs are kept per provider rather than as a single current record.
 *
 * Nothing here leaves the device — except that a DataStore file is part of the cloud backup, which
 * `data_extraction_rules.xml` only holds the Room database and the WebView cookies out of. So the
 * credentials go in encrypted: see [ImageHostSecretKeys] for which values those are and
 * [io.github.nodyssey.data.security.SecretCipher] for what encrypted means here. These are the user's
 * own credentials on services this app does not run, and the only place they are ever sent is the
 * host they belong to.
 */
class DataStoreImageHostSettings(
    private val dataStore: DataStore<Preferences>,
    /** The same cipher [ImageHostSecretEncryptionMigration] runs with, unless a test says otherwise. */
    private val cipher: SecretCipher,
) : ImageHostSettings {
    private val preferences: Flow<Preferences> = dataStore.data
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }

    override val selected: Flow<ImageHostProvider> =
        preferences.map { ImageHostProvider.fromId(it[ImageHostKeys.SELECTED]) }

    override fun config(provider: ImageHostProvider): Flow<ImageHostConfig> =
        preferences.map { it.readConfig(provider) }

    override suspend fun select(provider: ImageHostProvider) {
        dataStore.edit { it[ImageHostKeys.SELECTED] = provider.id }
    }

    override suspend fun save(config: ImageHostConfig) {
        dataStore.edit { preferences ->
            preferences.putSecret(ImageHostKeys.token(config.provider), config.token)
            preferences[ImageHostKeys.siteUrl(config.provider)] = config.siteUrl.normalizedSiteUrl()
            if (config.provider != ImageHostProvider.CUSTOM) return@edit
            preferences[ImageHostKeys.CUSTOM_FILE_FIELD] = config.custom.fileField.trim()
            preferences[ImageHostKeys.CUSTOM_HEADER_NAME] = config.custom.headerName.trim()
            preferences.putSecret(ImageHostKeys.CUSTOM_HEADER_VALUE, config.custom.headerValue)
            preferences.putSecret(ImageHostKeys.CUSTOM_FORM_FIELDS, config.custom.formFields)
            preferences[ImageHostKeys.CUSTOM_URL_PATH] = config.custom.urlPath.trim()
            preferences[ImageHostKeys.CUSTOM_URL_PREFIX] = config.custom.urlPrefix.trim()
        }
    }

    override suspend fun disconnect(provider: ImageHostProvider) {
        dataStore.edit { preferences ->
            preferences.remove(ImageHostKeys.token(provider))
            if (provider != ImageHostProvider.CUSTOM) return@edit
            // A custom host keeps its credential in one of two places, and which one is the user's
            // choice: a header value, or a `token=…` line among the form fields. Both go. The address
            // and the paths stay — those are configuration, not a secret, and re-typing them is the
            // part nobody wants to do twice.
            preferences.remove(ImageHostKeys.CUSTOM_HEADER_VALUE)
            preferences.remove(ImageHostKeys.CUSTOM_FORM_FIELDS)
        }
    }

    private fun Preferences.readConfig(provider: ImageHostProvider) = ImageHostConfig(
        provider = provider,
        siteUrl = this[ImageHostKeys.siteUrl(provider)].orEmpty(),
        token = secret(ImageHostKeys.token(provider)),
        custom = CustomHostFields(
            // Each falls back to the field's own default rather than to empty: an unset 取值路径
            // should read `url`, which is what most uploaders answer with, not a blank that fails
            // validation before the user has typed anything.
            fileField = this[ImageHostKeys.CUSTOM_FILE_FIELD] ?: CustomHostFields().fileField,
            headerName = this[ImageHostKeys.CUSTOM_HEADER_NAME].orEmpty(),
            headerValue = secret(ImageHostKeys.CUSTOM_HEADER_VALUE),
            formFields = secret(ImageHostKeys.CUSTOM_FORM_FIELDS),
            urlPath = this[ImageHostKeys.CUSTOM_URL_PATH] ?: CustomHostFields().urlPath,
            urlPrefix = this[ImageHostKeys.CUSTOM_URL_PREFIX].orEmpty(),
        ),
    )

    /**
     * A credential on its way out.
     *
     * A device that cannot encrypt stores nothing rather than the credential in the clear: the user
     * sees an empty field and has to paste the token again, which is recoverable. A leaked token is
     * not — it uploads under their account until they think to rotate it.
     */
    private fun MutablePreferences.putSecret(key: Preferences.Key<String>, value: String) {
        this[key] = cipher.encrypt(value.trim()).orEmpty()
    }

    /**
     * A credential on its way in, in whichever form it was left in.
     *
     * Unmarked means plaintext from before this store encrypted anything, or from a device
     * [ImageHostSecretEncryptionMigration] could not encrypt on — a working credential either way, so
     * it is handed back as it is and rewritten by the next save. Marked but unreadable means a
     * restored backup or a reset keystore, and there the credential is genuinely gone.
     */
    private fun Preferences.secret(key: Preferences.Key<String>): String {
        val stored = this[key].orEmpty()
        return if (SecretCipher.isEncrypted(stored)) cipher.decrypt(stored) else stored
    }
}
