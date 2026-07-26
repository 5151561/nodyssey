package io.github.nsreader.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nsreader.model.SearchHistoryEntry
import io.github.nsreader.model.SearchSort
import io.github.nsreader.model.SearchTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    private val json = Json { ignoreUnknownKeys = true }

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
                searchHistory = decodeSearchHistory(preferences),
                recentBoards = decodeValues(preferences[KEY_RECENT_BOARDS]),
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[KEY_THEME_MODE] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[KEY_DYNAMIC_COLOR] = enabled }

    suspend fun setFontScale(scale: Float) =
        edit { it[KEY_FONT_SCALE] = scale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE) }

    suspend fun setImagesOnWifiOnly(enabled: Boolean) = edit { it[KEY_IMAGES_WIFI_ONLY] = enabled }

    suspend fun addSearchHistory(entry: SearchHistoryEntry) {
        val normalized = entry.copy(query = entry.query.trim())
        if (normalized.query.isEmpty()) return
        dataStore.edit { preferences ->
            val history = decodeSearchHistory(preferences)
            preferences[KEY_SEARCH_HISTORY] =
                json.encodeToString(
                    (listOf(normalized) + history.filterNot { it.key == normalized.key })
                        .take(MAX_RECENT_SEARCHES)
                        .map(SearchHistoryEntry::toStored),
                )
        }
    }

    suspend fun removeSearchHistory(entry: SearchHistoryEntry) {
        dataStore.edit { preferences ->
            preferences[KEY_SEARCH_HISTORY] =
                json.encodeToString(
                    decodeSearchHistory(preferences)
                        .filterNot { it.key == entry.key }
                        .map(SearchHistoryEntry::toStored),
                )
        }
    }

    suspend fun clearSearchHistory(target: SearchTarget) {
        dataStore.edit { preferences ->
            preferences[KEY_SEARCH_HISTORY] =
                json.encodeToString(
                    decodeSearchHistory(preferences)
                        .filterNot { it.target == target }
                        .map(SearchHistoryEntry::toStored),
                )
        }
    }

    suspend fun recordRecentBoards(boards: Set<String>) {
        if (boards.isEmpty()) return
        dataStore.edit { preferences ->
            val recent = decodeValues(preferences[KEY_RECENT_BOARDS])
            preferences[KEY_RECENT_BOARDS] =
                encodeValues(boards.toList() + recent.filterNot(boards::contains), MAX_RECENT_BOARDS)
        }
    }

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
        private val KEY_SEARCH_HISTORY = stringPreferencesKey("search_history_v3")
        private val KEY_RECENT_BOARDS = stringPreferencesKey("recent_boards")

        private const val RECENT_SEARCH_SEPARATOR = '\u001F'
        private const val MAX_RECENT_SEARCHES = 8
        private const val MAX_RECENT_BOARDS = 6

        private fun decodeRecentSearches(value: String?): List<String> =
            value.orEmpty().split(RECENT_SEARCH_SEPARATOR).filter(String::isNotBlank)

        private fun decodeValues(value: String?): List<String> =
            value.orEmpty().split(RECENT_SEARCH_SEPARATOR).filter(String::isNotBlank)

        private fun encodeValues(
            values: List<String>,
            limit: Int,
        ): String =
            values
                .asSequence()
                .map { it.replace(RECENT_SEARCH_SEPARATOR.toString(), " ").trim() }
                .filter(String::isNotEmpty)
                .distinct()
                .take(limit)
                .joinToString(RECENT_SEARCH_SEPARATOR.toString())
    }

    private fun decodeSearchHistory(preferences: Preferences): List<SearchHistoryEntry> {
        val stored =
            preferences[KEY_SEARCH_HISTORY]?.let { encoded ->
                runCatching { json.decodeFromString<List<StoredSearchHistory>>(encoded) }.getOrNull()
            }
        if (stored != null) return stored.mapNotNull(StoredSearchHistory::toDomain)

        return decodeRecentSearches(preferences[KEY_RECENT_SEARCHES]).map { query ->
            SearchHistoryEntry(query = query, target = SearchTarget.POSTS)
        }
    }
}

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val fontScale: Float = 1f,
    val imagesOnWifiOnly: Boolean = false,
    val searchHistory: List<SearchHistoryEntry> = emptyList(),
    val recentBoards: List<String> = emptyList(),
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
private data class StoredSearchHistory(
    val query: String,
    val target: String,
    val categorySlugs: List<String> = emptyList(),
    val sort: String = SearchSort.RELEVANCE.name,
) {
    fun toDomain(): SearchHistoryEntry? {
        val resolvedTarget = runCatching { SearchTarget.valueOf(target) }.getOrNull() ?: return null
        val resolvedSort =
            when (sort) {
                "POST_TIME" -> SearchSort.TIME
                "LAST_REPLY" -> SearchSort.RELEVANCE
                else -> runCatching { SearchSort.valueOf(sort) }.getOrDefault(SearchSort.RELEVANCE)
            }
        return SearchHistoryEntry(query.trim(), resolvedTarget, categorySlugs.toSet(), resolvedSort)
            .takeIf { it.query.isNotEmpty() }
    }
}

private fun SearchHistoryEntry.toStored() =
    StoredSearchHistory(
        query = query,
        target = target.name,
        categorySlugs = categorySlugs.sorted(),
        sort = sort.name,
    )
