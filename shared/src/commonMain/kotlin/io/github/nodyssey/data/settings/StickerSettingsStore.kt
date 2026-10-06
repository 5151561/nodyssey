package io.github.nodyssey.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.nodyssey.data.sticker.StickerCdn
import io.github.nodyssey.data.sticker.StickerCdnSettings
import io.github.nodyssey.data.sticker.StickerGroupLayout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import okio.IOException

/**
 * 表情管理's settings: where pictures load from, and the order and visibility of the panel's groups.
 * Keys in the settings store, made by [SettingsRepository] for the reason [ReportCompareStore] is:
 * two `DataStore`s over one file is an error.
 */
class StickerSettingsStore internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val preferences: Flow<Preferences> =
        dataStore.data.catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }

    val settings: Flow<StickerCdnSettings> =
        preferences
            .map { StickerCdnSettings(cdn = StickerCdn.fromId(it[KEY_CDN]), customBase = it[KEY_CUSTOM].orEmpty()) }
            .distinctUntilChanged()

    val groupLayout: Flow<StickerGroupLayout> =
        preferences
            .map { prefs ->
                StickerGroupLayout(
                    order = prefs[KEY_GROUP_ORDER]?.split('\n')?.filter { it.isNotEmpty() }.orEmpty(),
                    hidden = prefs[KEY_GROUPS_HIDDEN].orEmpty(),
                )
            }.distinctUntilChanged()

    suspend fun setCdn(cdn: StickerCdn) {
        dataStore.edit { it[KEY_CDN] = cdn.id }
    }

    suspend fun setCustomBase(base: String) {
        dataStore.edit { it[KEY_CUSTOM] = base.trim() }
    }

    suspend fun setGroupOrder(keys: List<String>) {
        dataStore.edit { it[KEY_GROUP_ORDER] = keys.joinToString("\n") }
    }

    suspend fun setGroupHidden(
        key: String,
        hidden: Boolean,
    ) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_GROUPS_HIDDEN].orEmpty()
            prefs[KEY_GROUPS_HIDDEN] = if (hidden) current + key else current - key
        }
    }

    private companion object {
        val KEY_CDN = stringPreferencesKey("sticker_cdn")
        val KEY_CUSTOM = stringPreferencesKey("sticker_cdn_custom")
        val KEY_GROUP_ORDER = stringPreferencesKey("sticker_group_order")
        val KEY_GROUPS_HIDDEN = stringSetPreferencesKey("sticker_groups_hidden")
    }
}
