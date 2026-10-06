package io.github.nodyssey.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nodyssey.data.sticker.StickerCdn
import io.github.nodyssey.data.sticker.StickerCdnSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import okio.IOException

/**
 * 表情管理 › 图片从哪儿加载. Two keys in the settings store, made by [SettingsRepository] for the reason
 * [ReportCompareStore] is: two `DataStore`s over one file is an error.
 */
class StickerCdnStore internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<StickerCdnSettings> =
        dataStore.data
            .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
            .map { StickerCdnSettings(cdn = StickerCdn.fromId(it[KEY_CDN]), customBase = it[KEY_CUSTOM].orEmpty()) }
            .distinctUntilChanged()

    suspend fun setCdn(cdn: StickerCdn) {
        dataStore.edit { it[KEY_CDN] = cdn.id }
    }

    suspend fun setCustomBase(base: String) {
        dataStore.edit { it[KEY_CUSTOM] = base.trim() }
    }

    private companion object {
        val KEY_CDN = stringPreferencesKey("sticker_cdn")
        val KEY_CUSTOM = stringPreferencesKey("sticker_cdn_custom")
    }
}
