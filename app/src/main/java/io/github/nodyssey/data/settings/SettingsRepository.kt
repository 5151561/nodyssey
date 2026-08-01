package io.github.nodyssey.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nodyssey.data.NotificationCounts
import io.github.nodyssey.model.SearchHistoryEntry
import io.github.nodyssey.model.SearchSort
import io.github.nodyssey.model.SearchTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
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
                externalLinkTarget = preferences[KEY_EXTERNAL_LINK_TARGET]
                    ?.let { runCatching { ExternalLinkTarget.valueOf(it) }.getOrNull() }
                    ?: ExternalLinkTarget.CUSTOM_TAB,
                holidayTheme = preferences[KEY_HOLIDAY_THEME] ?: false,
                searchHistory = decodeSearchHistory(preferences),
                recentBoards = decodeValues(preferences[KEY_RECENT_BOARDS]),
                hiddenHomeBoards =
                decodeValues(preferences[KEY_HIDDEN_HOME_BOARDS])
                    // Only the three slugs the site allows can hide a board; anything else in the
                    // store (a renamed slug, a bad write) must not silently thin out the feed.
                    .filter { it in OPTIONAL_HOME_BOARD_SLUGS }
                    .toSet(),
                notificationsEnabled = preferences[KEY_NOTIFICATIONS_ENABLED] ?: false,
                notificationPollMinutes =
                (preferences[KEY_NOTIFICATION_POLL_MINUTES] ?: DEFAULT_POLL_MINUTES)
                    .let { minutes -> if (minutes in POLL_MINUTE_CHOICES) minutes else DEFAULT_POLL_MINUTES },
                notificationsWifiOnly = preferences[KEY_NOTIFICATIONS_WIFI_ONLY] ?: false,
                notificationQuietHours = preferences[KEY_NOTIFICATION_QUIET_HOURS] ?: true,
                notifyMentions = preferences[KEY_NOTIFY_MENTIONS] ?: true,
                notifyReplies = preferences[KEY_NOTIFY_REPLIES] ?: true,
                notifyMessages = preferences[KEY_NOTIFY_MESSAGES] ?: true,
            )
        }

    /**
     * 临时显示被屏蔽内容 — deliberately *not* in DataStore.
     *
     * The site keeps this in the user menu as a momentary escape hatch, and d6 4/5 words the app's
     * version the same way: "重启 App 后恢复屏蔽". Local storage that survived the process would
     * quietly turn "show me, just for now" into "never block again", so the flag lives and dies with
     * the process on purpose.
     */
    private val showBlockedContentState = MutableStateFlow(false)
    val showBlockedContent: StateFlow<Boolean> = showBlockedContentState.asStateFlow()

    fun setShowBlockedContent(enabled: Boolean) {
        showBlockedContentState.value = enabled
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[KEY_THEME_MODE] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[KEY_DYNAMIC_COLOR] = enabled }

    suspend fun setFontScale(scale: Float) =
        edit { it[KEY_FONT_SCALE] = scale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE) }

    suspend fun setImagesOnWifiOnly(enabled: Boolean) = edit { it[KEY_IMAGES_WIFI_ONLY] = enabled }

    suspend fun setExternalLinkTarget(target: ExternalLinkTarget) =
        edit { it[KEY_EXTERNAL_LINK_TARGET] = target.name }

    suspend fun setNotificationsEnabled(enabled: Boolean) =
        edit { it[KEY_NOTIFICATIONS_ENABLED] = enabled }

    suspend fun setNotificationPollMinutes(minutes: Int) =
        edit {
            it[KEY_NOTIFICATION_POLL_MINUTES] =
                if (minutes in POLL_MINUTE_CHOICES) minutes else DEFAULT_POLL_MINUTES
        }

    suspend fun setNotificationsWifiOnly(enabled: Boolean) =
        edit { it[KEY_NOTIFICATIONS_WIFI_ONLY] = enabled }

    suspend fun setNotificationQuietHours(enabled: Boolean) =
        edit { it[KEY_NOTIFICATION_QUIET_HOURS] = enabled }

    suspend fun setNotifyMentions(enabled: Boolean) = edit { it[KEY_NOTIFY_MENTIONS] = enabled }

    suspend fun setNotifyReplies(enabled: Boolean) = edit { it[KEY_NOTIFY_REPLIES] = enabled }

    suspend fun setNotifyMessages(enabled: Boolean) = edit { it[KEY_NOTIFY_MESSAGES] = enabled }

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

    /**
     * The local mirror of the site's 首页版块 switches — Remote rows, cached so the feed can draw
     * itself without the network.
     *
     * Stored as the *hidden* set rather than the visible one because that is the shape of the site's
     * own preference: only 交易 / 生活 / 贴图 can be turned off, everything else is always on, and
     * the empty set — the default — means "hide nothing". [slug] outside
     * [OPTIONAL_HOME_BOARD_SLUGS] is refused here and filtered on read, so the store can never hide
     * a board the site would show.
     */
    suspend fun setHomeBoardHidden(slug: String, hidden: Boolean) {
        if (slug !in OPTIONAL_HOME_BOARD_SLUGS) return
        dataStore.edit { preferences ->
            val current = decodeValues(preferences[KEY_HIDDEN_HOME_BOARDS]).toSet()
            val next = if (hidden) current + slug else current - slug
            if (next.isEmpty()) {
                preferences.remove(KEY_HIDDEN_HOME_BOARDS)
            } else {
                preferences[KEY_HIDDEN_HOME_BOARDS] =
                    encodeValues(next.toList(), OPTIONAL_HOME_BOARD_SLUGS.size)
            }
        }
    }

    /** Local mirror of the site's Remote 启用节日主题 switch; the sync authority is the account. */
    suspend fun setHolidayTheme(enabled: Boolean) = edit { it[KEY_HOLIDAY_THEME] = enabled }

    /** Most-recently-scoped board, kept at the top of the range sheet. Null — the whole site — is not a board. */
    suspend fun recordRecentBoard(slug: String?) {
        if (slug.isNullOrBlank()) return
        dataStore.edit { preferences ->
            val recent = decodeValues(preferences[KEY_RECENT_BOARDS])
            preferences[KEY_RECENT_BOARDS] =
                encodeValues(listOf(slug) + recent.filterNot { it == slug }, MAX_RECENT_BOARDS)
        }
    }

    /**
     * The unread totals the poll worker most recently saw. Worker bookkeeping, not a user setting —
     * deliberately absent from [settings]; it lives here only because this class owns the DataStore.
     */
    suspend fun notificationSeenCounts(): NotificationCounts {
        val preferences =
            dataStore.data
                .catch { throwable ->
                    if (throwable is IOException) emit(emptyPreferences()) else throw throwable
                }.first()
        return NotificationCounts(
            replies = preferences[KEY_SEEN_REPLIES] ?: 0,
            mentions = preferences[KEY_SEEN_MENTIONS] ?: 0,
            messages = preferences[KEY_SEEN_MESSAGES] ?: 0,
        )
    }

    suspend fun setNotificationSeenCounts(counts: NotificationCounts) =
        edit {
            it[KEY_SEEN_REPLIES] = counts.replies
            it[KEY_SEEN_MENTIONS] = counts.mentions
            it[KEY_SEEN_MESSAGES] = counts.messages
        }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private fun emptyPreferences() = androidx.datastore.preferences.core.emptyPreferences()

    companion object {
        const val MIN_FONT_SCALE = 0.85f
        const val MAX_FONT_SCALE = 1.5f

        /** WorkManager's own floor is 15 minutes, which is why the choices start there — board f4. */
        val POLL_MINUTE_CHOICES = listOf(15, 30, 60)
        const val DEFAULT_POLL_MINUTES = 30

        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_FONT_SCALE = floatPreferencesKey("font_scale")
        private val KEY_IMAGES_WIFI_ONLY = booleanPreferencesKey("images_on_wifi_only")
        private val KEY_EXTERNAL_LINK_TARGET = stringPreferencesKey("external_link_target")
        private val KEY_RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        private val KEY_SEARCH_HISTORY = stringPreferencesKey("search_history_v3")
        private val KEY_RECENT_BOARDS = stringPreferencesKey("recent_boards")
        private val KEY_HIDDEN_HOME_BOARDS = stringPreferencesKey("hidden_home_boards")
        private val KEY_HOLIDAY_THEME = booleanPreferencesKey("holiday_theme")
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val KEY_NOTIFICATION_POLL_MINUTES = intPreferencesKey("notification_poll_minutes")
        private val KEY_NOTIFICATIONS_WIFI_ONLY = booleanPreferencesKey("notifications_wifi_only")
        private val KEY_NOTIFICATION_QUIET_HOURS = booleanPreferencesKey("notification_quiet_hours")
        private val KEY_NOTIFY_MENTIONS = booleanPreferencesKey("notify_mentions")
        private val KEY_NOTIFY_REPLIES = booleanPreferencesKey("notify_replies")
        private val KEY_NOTIFY_MESSAGES = booleanPreferencesKey("notify_messages")
        private val KEY_SEEN_REPLIES = intPreferencesKey("notification_seen_replies")
        private val KEY_SEEN_MENTIONS = intPreferencesKey("notification_seen_mentions")
        private val KEY_SEEN_MESSAGES = intPreferencesKey("notification_seen_messages")

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

    /**
     * Deduplicated because decoding is lossy: a stored row's board list collapses to its first
     * board and the retired sorts fold into [SearchSort.RELEVANCE], so two rows written by an older
     * build can land on one domain entry. The list is read straight into a LazyColumn keyed on
     * [SearchHistoryEntry.key], which throws the moment a key repeats — and the first write after
     * this drops the collapsed twin from disk for good.
     */
    private fun decodeSearchHistory(preferences: Preferences): List<SearchHistoryEntry> {
        val stored =
            preferences[KEY_SEARCH_HISTORY]?.let { encoded ->
                runCatching { json.decodeFromString<List<StoredSearchHistory>>(encoded) }.getOrNull()
            }
        if (stored != null) {
            return stored.mapNotNull(StoredSearchHistory::toDomain).distinctBy(SearchHistoryEntry::key)
        }

        return decodeRecentSearches(preferences[KEY_RECENT_SEARCHES])
            .map { query -> SearchHistoryEntry(query = query, target = SearchTarget.POSTS) }
            .distinctBy(SearchHistoryEntry::key)
    }
}

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val fontScale: Float = 1f,
    val imagesOnWifiOnly: Boolean = false,
    val externalLinkTarget: ExternalLinkTarget = ExternalLinkTarget.CUSTOM_TAB,
    /** Local mirror of the account's Remote 启用节日主题 switch. */
    val holidayTheme: Boolean = false,
    val searchHistory: List<SearchHistoryEntry> = emptyList(),
    val recentBoards: List<String> = emptyList(),
    /** Boards switched off the home feed. Empty — the default — hides nothing; see `setHomeBoardHidden`. */
    val hiddenHomeBoards: Set<String> = emptySet(),
    /*
     * App notification polling (board f4). Off by default: polling costs battery and needs the
     * POST_NOTIFICATIONS runtime permission, so it starts only when the user asks for it.
     */
    val notificationsEnabled: Boolean = false,
    val notificationPollMinutes: Int = SettingsRepository.DEFAULT_POLL_MINUTES,
    val notificationsWifiOnly: Boolean = false,
    /** Fixed 23:00–07:00 window (board f4): the worker still fetches, but posts nothing. */
    val notificationQuietHours: Boolean = true,
    val notifyMentions: Boolean = true,
    val notifyReplies: Boolean = true,
    val notifyMessages: Boolean = true,
)

/**
 * [TIMED] is d6 5/5's 夜间模式依据 = 定时（日落）: dark by the clock, regardless of the system theme.
 * The site's own automatic night mode is time-based too ("系统时间"); 跟随系统 ([SYSTEM]) is the app's
 * addition and the default.
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK, TIMED }

/**
 * Where a link that leaves the app goes.
 *
 * [CUSTOM_TAB] is the default because a thread is mostly other people's links: handing every one of
 * them to the browser as a separate task is what turns "glance at what they linked" into "find your
 * way back to the app". A Custom Tab keeps the back gesture pointing at the thread, and — unlike the
 * in-app WebView, which exists to carry the NodeSeek session and must never hold a stranger's page —
 * it is the browser's own process, with the browser's origin bar and its cookies, not ours.
 *
 * [BROWSER] is the escape hatch for anyone who wants their links in the browser they already have
 * signed in, with their extensions and their tab list. It is also what runs when no installed
 * browser supports Custom Tabs, whatever this setting says.
 */
enum class ExternalLinkTarget { CUSTOM_TAB, BROWSER }

/**
 * Whether the 定时 night window covers this hour.
 *
 * A fixed 19:00–07:00 window: the site publishes no schedule to copy and "日落" without a location
 * permission is a fiction, so the app uses the plainest defensible approximation and says so in the
 * settings row rather than pretending to track the sun.
 */
fun isTimedNightHour(hourOfDay: Int): Boolean =
    hourOfDay >= TIMED_NIGHT_START_HOUR || hourOfDay < TIMED_NIGHT_END_HOUR

const val TIMED_NIGHT_START_HOUR = 19
const val TIMED_NIGHT_END_HOUR = 7

/**
 * The stored field stays a list even though the domain now holds one board.
 *
 * Records written by the multi-select build are already on disk; keeping the shape means they still
 * decode, taking their first board as the scope, rather than every user losing their history to a
 * silent parse failure.
 */
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
        return SearchHistoryEntry(
            query = query.trim(),
            target = resolvedTarget,
            categorySlug = categorySlugs.sorted().firstOrNull()?.ifBlank { null },
            sort = resolvedSort,
        ).takeIf { it.query.isNotEmpty() }
    }
}

private fun SearchHistoryEntry.toStored() =
    StoredSearchHistory(
        query = query,
        target = target.name,
        categorySlugs = listOfNotNull(categorySlug),
        sort = sort.name,
    )
