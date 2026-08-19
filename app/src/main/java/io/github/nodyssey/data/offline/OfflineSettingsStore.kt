package io.github.nodyssey.data.offline

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import io.github.nodyssey.data.OfflineSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The four controls in 离线管理, on disk.
 *
 * Its own DataStore file rather than a corner of `settings`: these are read by a WorkManager worker
 * in a process that may have opened nothing else, and one small file is cheaper to fault in than
 * the whole of the settings screen's.
 *
 * Every default matches [OfflineSettings]'s own, so a device that has never opened the sheet reads
 * the same values the sheet would show it.
 */
class OfflineSettingsStore(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<OfflineSettings> = dataStore.data.map(::read)

    suspend fun update(settings: OfflineSettings) {
        dataStore.edit { prefs ->
            prefs[WIFI_ONLY] = settings.wifiOnly
            prefs[INCLUDE_IMAGES] = settings.includeImages
            prefs[AUTO_SYNC] = settings.autoSyncReplies
            prefs[RETENTION_DAYS] = settings.retentionDays
        }
    }

    private fun read(prefs: Preferences) =
        OfflineSettings(
            wifiOnly = prefs[WIFI_ONLY] ?: OfflineSettings().wifiOnly,
            includeImages = prefs[INCLUDE_IMAGES] ?: OfflineSettings().includeImages,
            autoSyncReplies = prefs[AUTO_SYNC] ?: OfflineSettings().autoSyncReplies,
            retentionDays = prefs[RETENTION_DAYS] ?: OfflineSettings.DEFAULT_RETENTION_DAYS,
        )

    private companion object {
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val INCLUDE_IMAGES = booleanPreferencesKey("include_images")
        val AUTO_SYNC = booleanPreferencesKey("auto_sync_replies")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
    }
}
