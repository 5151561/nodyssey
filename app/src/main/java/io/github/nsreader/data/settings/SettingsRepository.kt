package io.github.nsreader.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * The single source of truth for user settings.
 *
 * Everything that reads a setting collects [settings]; nothing keeps its own copy. That rule is the
 * whole point — settings duplicated into a ViewModel field or an `object` cache is how a settings
 * screen and the rest of the app drift apart.
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {

    val settings: Flow<UserSettings> = dataStore.data
        // A corrupt or unreadable store must not take the app down; fall back to defaults.
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { preferences ->
            UserSettings(
                themeMode = preferences[KEY_THEME_MODE]
                    ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                    ?: ThemeMode.SYSTEM,
                dynamicColor = preferences[KEY_DYNAMIC_COLOR] ?: false,
                fontScale = preferences[KEY_FONT_SCALE] ?: 1f,
                imagesOnWifiOnly = preferences[KEY_IMAGES_WIFI_ONLY] ?: false,
                recentSearches = decodeRecentSearches(preferences[KEY_RECENT_SEARCHES]),
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[KEY_THEME_MODE] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[KEY_DYNAMIC_COLOR] = enabled }

    suspend fun setFontScale(scale: Float) =
        edit { it[KEY_FONT_SCALE] = scale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE) }

    suspend fun setImagesOnWifiOnly(enabled: Boolean) = edit { it[KEY_IMAGES_WIFI_ONLY] = enabled }

    suspend fun addRecentSearch(query: String) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return
        dataStore.edit { preferences ->
            val searches = decodeRecentSearches(preferences[KEY_RECENT_SEARCHES])
            preferences[KEY_RECENT_SEARCHES] =
                encodeRecentSearches(listOf(normalized) + searches.filterNot { it == normalized })
        }
    }

    suspend fun removeRecentSearch(query: String) {
        dataStore.edit { preferences ->
            val searches = decodeRecentSearches(preferences[KEY_RECENT_SEARCHES]).filterNot { it == query }
            preferences[KEY_RECENT_SEARCHES] = encodeRecentSearches(searches)
        }
    }

    suspend fun clearRecentSearches() = edit { it.remove(KEY_RECENT_SEARCHES) }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private fun emptyPreferences() = androidx.datastore.preferences.core.emptyPreferences()

    companion object {
        const val MIN_FONT_SCALE = 0.85f
        const val MAX_FONT_SCALE = 1.5f

        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_FONT_SCALE = floatPreferencesKey("font_scale")
        private val KEY_IMAGES_WIFI_ONLY = booleanPreferencesKey("images_on_wifi_only")
        private val KEY_RECENT_SEARCHES = stringPreferencesKey("recent_searches")

        private const val RECENT_SEARCH_SEPARATOR = '\u001F'
        private const val MAX_RECENT_SEARCHES = 8

        private fun decodeRecentSearches(value: String?): List<String> =
            value.orEmpty().split(RECENT_SEARCH_SEPARATOR).filter(String::isNotBlank)

        private fun encodeRecentSearches(searches: List<String>): String =
            searches
                .asSequence()
                .map { it.replace(RECENT_SEARCH_SEPARATOR.toString(), " ").trim() }
                .filter(String::isNotEmpty)
                .distinct()
                .take(MAX_RECENT_SEARCHES)
                .joinToString(RECENT_SEARCH_SEPARATOR.toString())
    }
}

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val fontScale: Float = 1f,
    val imagesOnWifiOnly: Boolean = false,
    val recentSearches: List<String> = emptyList(),
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }
