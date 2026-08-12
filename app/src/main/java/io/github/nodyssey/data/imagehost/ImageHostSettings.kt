package io.github.nodyssey.data.imagehost

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Which host is selected, and the credentials for each.
 *
 * All six live in one store, keyed by [ImageHostProvider.id], so switching hosts does not mean
 * re-pasting a token: the one that was there before is still there when the user switches back.
 * That is the whole reason the configs are kept per provider rather than as a single current record.
 *
 * Nothing here leaves the device. These are the user's own credentials on services this app does not
 * run, and the only place they are ever sent is the host they belong to.
 */
private val Context.imageHostDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "imagehost",
    produceMigrations = { context ->
        listOf(
            LegacyNodeImageKeyMigration {
                context.legacyNodeImageDataStore.data.first()[LEGACY_NODE_IMAGE_KEY]
            },
        )
    },
)

/**
 * Where the NodeImage key lived when nodeimage.com was the only host the app could talk to.
 *
 * Kept declared for exactly one reason: [LegacyNodeImageKeyMigration] reads it once. Deleting the
 * declaration would make every existing install look like a fresh one with no host connected.
 */
private val Context.legacyNodeImageDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nodeimage",
)

/**
 * Carries a pre-existing NodeImage key into the multi-host store, once.
 *
 * Before this feature there was one host and one key, under `api-key` in its own file. An upgrade
 * that ignored it would silently disconnect everybody who had already set the app up, and the first
 * they would hear of it is a failed attachment halfway through writing a post.
 */
internal class LegacyNodeImageKeyMigration(
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

private val LEGACY_NODE_IMAGE_KEY = stringPreferencesKey("api-key")

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

class DataStoreImageHostSettings(context: Context) : ImageHostSettings {
    private val dataStore = context.applicationContext.imageHostDataStore

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
            preferences[ImageHostKeys.token(config.provider)] = config.token.trim()
            preferences[ImageHostKeys.siteUrl(config.provider)] = config.siteUrl.normalizedSiteUrl()
            if (config.provider != ImageHostProvider.CUSTOM) return@edit
            preferences[ImageHostKeys.CUSTOM_FILE_FIELD] = config.custom.fileField.trim()
            preferences[ImageHostKeys.CUSTOM_HEADER_NAME] = config.custom.headerName.trim()
            preferences[ImageHostKeys.CUSTOM_HEADER_VALUE] = config.custom.headerValue.trim()
            preferences[ImageHostKeys.CUSTOM_FORM_FIELDS] = config.custom.formFields.trim()
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
        token = this[ImageHostKeys.token(provider)].orEmpty(),
        custom = CustomHostFields(
            // Each falls back to the field's own default rather than to empty: an unset 取值路径
            // should read `url`, which is what most uploaders answer with, not a blank that fails
            // validation before the user has typed anything.
            fileField = this[ImageHostKeys.CUSTOM_FILE_FIELD] ?: CustomHostFields().fileField,
            headerName = this[ImageHostKeys.CUSTOM_HEADER_NAME].orEmpty(),
            headerValue = this[ImageHostKeys.CUSTOM_HEADER_VALUE].orEmpty(),
            formFields = this[ImageHostKeys.CUSTOM_FORM_FIELDS].orEmpty(),
            urlPath = this[ImageHostKeys.CUSTOM_URL_PATH] ?: CustomHostFields().urlPath,
            urlPrefix = this[ImageHostKeys.CUSTOM_URL_PREFIX].orEmpty(),
        ),
    )
}
