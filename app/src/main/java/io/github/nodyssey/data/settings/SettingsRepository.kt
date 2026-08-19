package io.github.nodyssey.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nodyssey.data.NotificationCounts
import io.github.nodyssey.data.update.UpdateCheckStore
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.SearchHistoryEntry
import io.github.nodyssey.model.SearchTarget
import io.github.plaza.core.update.AppRelease
import io.github.plaza.core.update.UpdateCheckRecord
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
 *
 * @param devChannelDefault what 接收 dev 版更新 reads as before anyone touches the switch. Wired to
 * "this build is itself a test build" rather than to a constant: a phone running `1.2.9-dev.3` that
 * checks the stable channel is asking whether a *release* has passed `1.2.9-dev.3`, and the honest
 * answer to that stays 已是最新 until `1.3.0` ships — so a tester who never found the switch would
 * sit on a build that has had four successors and be told there is nothing. The switch still wins
 * once it is touched, in both directions, which is what keeps turning it off from being undone here.
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val devChannelDefault: Boolean = false,
) : UpdateCheckStore {
    private val json = Json { ignoreUnknownKeys = true }

    val settings: Flow<UserSettings> = dataStore.data
        // A corrupt or unreadable store must not take the app down; fall back to defaults.
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { preferences ->
            UserSettings(
                themeMode = preferences[KEY_THEME_MODE]
                    ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                    ?: ThemeMode.SYSTEM,
                colorSource = preferences[KEY_COLOR_SOURCE]
                    ?.let(::decodeColorSource)
                    // 动态取色 used to be a bare switch. A store written by an older build has no
                    // 配色来源 at all, and the one thing it can say is whether that switch was on.
                    ?: if (preferences[KEY_DYNAMIC_COLOR] == true) ColorSource.WALLPAPER else ColorSource.PRESET,
                presetSeed = preferences[KEY_PRESET_SEED] ?: DEFAULT_SEED_COLOR,
                seedColor = preferences[KEY_SEED_COLOR] ?: DEFAULT_SEED_COLOR,
                wallpaperSeed = preferences[KEY_WALLPAPER_SEED],
                wallpaperSystemPalette = preferences[KEY_WALLPAPER_SYSTEM_PALETTE] ?: true,
                wallpaperAutoUpdate = preferences[KEY_WALLPAPER_AUTO_UPDATE] ?: false,
                paletteStyle = preferences[KEY_PALETTE_STYLE]
                    ?.let { runCatching { PaletteStyle.valueOf(it) }.getOrNull() }
                    ?: PaletteStyle.SOFT,
                savedThemes = decodeSavedThemes(preferences[KEY_SAVED_THEMES]),
                fontScale = preferences[KEY_FONT_SCALE] ?: 1f,
                stickerUniformSize = preferences[KEY_STICKER_UNIFORM_SIZE] ?: true,
                stickerSize = (preferences[KEY_STICKER_SIZE] ?: DEFAULT_STICKER_SIZE_SP)
                    .coerceIn(MIN_STICKER_SIZE_SP, MAX_STICKER_SIZE_SP),
                imagesOnWifiOnly = preferences[KEY_IMAGES_WIFI_ONLY] ?: false,
                externalLinkTarget = preferences[KEY_EXTERNAL_LINK_TARGET]
                    ?.let { runCatching { ExternalLinkTarget.valueOf(it) }.getOrNull() }
                    ?: ExternalLinkTarget.CUSTOM_TAB,
                reportFormat = preferences[KEY_REPORT_FORMAT]
                    ?.let { runCatching { ReportFormat.valueOf(it) }.getOrNull() }
                    ?: ReportFormat.ADAPTED,
                homePageBar = preferences[KEY_HOME_PAGE_BAR] ?: true,
                holidayTheme = preferences[KEY_HOLIDAY_THEME] ?: false,
                searchHistory = decodeSearchHistory(preferences),
                recentBoards = decodeValues(preferences[KEY_RECENT_BOARDS]),
                hiddenHomeBoards =
                decodeValues(preferences[KEY_HIDDEN_HOME_BOARDS])
                    // Only the three slugs the site allows can hide a board; anything else in the
                    // store (a renamed slug, a bad write) must not silently thin out the feed.
                    .filter { it in OPTIONAL_HOME_BOARD_SLUGS }
                    .toSet(),
                homeBoardOrder = decodeValues(preferences[KEY_HOME_BOARD_ORDER]),
                disabledHomeBoards = decodeValues(preferences[KEY_DISABLED_HOME_BOARDS]).toSet(),
                postToolbarActions = decodeValues(preferences[KEY_POST_TOOLBAR]),
                replyToolbarActions = decodeValues(preferences[KEY_REPLY_TOOLBAR]),
                messageToolbarActions = decodeValues(preferences[KEY_MESSAGE_TOOLBAR]),
                notificationsEnabled = preferences[KEY_NOTIFICATIONS_ENABLED] ?: false,
                notificationPollMinutes =
                (preferences[KEY_NOTIFICATION_POLL_MINUTES] ?: DEFAULT_POLL_MINUTES)
                    .let { minutes -> if (minutes in POLL_MINUTE_CHOICES) minutes else DEFAULT_POLL_MINUTES },
                notificationsWifiOnly = preferences[KEY_NOTIFICATIONS_WIFI_ONLY] ?: false,
                notificationQuietHours = preferences[KEY_NOTIFICATION_QUIET_HOURS] ?: true,
                notifyMentions = preferences[KEY_NOTIFY_MENTIONS] ?: true,
                notifyReplies = preferences[KEY_NOTIFY_REPLIES] ?: true,
                notifyMessages = preferences[KEY_NOTIFY_MESSAGES] ?: true,
                readHistoryLimit = readHistoryLimit(preferences),
                updateCheckOnLaunch = preferences[KEY_UPDATE_CHECK_ON_LAUNCH] ?: true,
                updateDevChannel = preferences[KEY_UPDATE_DEV_CHANNEL] ?: devChannelDefault,
            )
        }

    /**
     * 临时显示被屏蔽内容 — deliberately *not* in DataStore.
     *
     * The site keeps this in the user menu as a momentary escape hatch, and d6 4/5 words the app's
     * version the same way: "重启 App 后恢复屏蔽". Local storage that survived the process would
     * quietly turn "show me, just for now" into "never block again", so the flag lives and dies with
     * the process on purpose.
     *
     * It is a *view* switch and nothing more: the block list is account state and the server is what
     * marks a post or a floor blocked. Flipping this reveals rows the app has already downloaded —
     * it never asks the site for anything, and it can never block or unblock anyone.
     */
    private val showBlockedContentState = MutableStateFlow(false)
    val showBlockedContent: StateFlow<Boolean> = showBlockedContentState.asStateFlow()

    fun setShowBlockedContent(enabled: Boolean) {
        showBlockedContentState.value = enabled
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[KEY_THEME_MODE] = mode.name }

    suspend fun setColorSource(source: ColorSource) = edit { it[KEY_COLOR_SOURCE] = source.name }

    /*
     * One stored seed per source, rather than one shared between them.
     *
     * j1 asks for it in as many words — "切回「自定义」直接沿用 #2F6D8C，不用重新调色" — and the
     * alternative is worse than it sounds: with a single field, tapping 预设 · 苔绿 to see what it
     * looks like would silently overwrite a colour that took a minute in the picker to arrive at,
     * and 自定义 has no way to get it back.
     */

    /** The preset that 预设 is currently on, as its seed. */
    suspend fun setPresetSeed(argb: Int) = edit { it[KEY_PRESET_SEED] = argb }

    /**
     * The seed 自定义 expands into a scheme from. Stored as ARGB, and kept even while another source
     * is selected so that coming back to 自定义 returns the colour that was picked.
     */
    suspend fun setSeedColor(argb: Int) = edit { it[KEY_SEED_COLOR] = argb }

    /** Which of the wallpaper's candidates 动态取色 is on; absent means "the first one it finds". */
    suspend fun setWallpaperSeed(argb: Int) = edit { it[KEY_WALLPAPER_SEED] = argb }

    /**
     * 使用系统调色板 — hand the OS's own Monet scheme through instead of generating one from the
     * candidate above. On by default, and ignored below API 31 where there is no such palette.
     */
    suspend fun setWallpaperSystemPalette(enabled: Boolean) =
        edit { it[KEY_WALLPAPER_SYSTEM_PALETTE] = enabled }

    /** 壁纸变化时自动更新 — re-read the wallpaper on the next launch rather than keeping this seed. */
    suspend fun setWallpaperAutoUpdate(enabled: Boolean) =
        edit { it[KEY_WALLPAPER_AUTO_UPDATE] = enabled }

    /** 色彩风格 — how far the generated scheme travels from whichever seed is in force. */
    suspend fun setPaletteStyle(style: PaletteStyle) = edit { it[KEY_PALETTE_STYLE] = style.name }

    /**
     * 我的主题 — appends, or renames in place when the colour is already saved.
     *
     * Keyed on the colour rather than on the name: two entries the reader cannot tell apart in the
     * chip row is the failure mode, and a name is what they would rename to tell them apart.
     */
    suspend fun saveTheme(name: String, argb: Int) =
        edit { preferences ->
            val existing = decodeSavedThemes(preferences[KEY_SAVED_THEMES])
            val without = existing.filterNot { it.color == argb }
            preferences[KEY_SAVED_THEMES] =
                json.encodeToString(
                    (without + SavedTheme(name = name.trim(), color = argb))
                        .takeLast(MAX_SAVED_THEMES),
                )
        }

    suspend fun deleteSavedTheme(argb: Int) =
        edit { preferences ->
            preferences[KEY_SAVED_THEMES] =
                json.encodeToString(
                    decodeSavedThemes(preferences[KEY_SAVED_THEMES]).filterNot { it.color == argb },
                )
        }

    private fun decodeSavedThemes(encoded: String?): List<SavedTheme> {
        if (encoded.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<SavedTheme>>(encoded) }.getOrNull().orEmpty()
    }

    suspend fun setFontScale(scale: Float) =
        edit { it[KEY_FONT_SCALE] = scale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE) }

    /**
     * 表情统一缩限. On — the default, and what every build before this one did — every inline sticker
     * is drawn in the same [stickerSize] square. Off, each is drawn at its own natural size, the way
     * the web does it.
     */
    suspend fun setStickerUniformSize(uniform: Boolean) =
        edit { it[KEY_STICKER_UNIFORM_SIZE] = uniform }

    /** The side of that square, in sp. Only read while 表情统一缩限 is on. */
    suspend fun setStickerSize(sizeSp: Int) =
        edit { it[KEY_STICKER_SIZE] = sizeSp.coerceIn(MIN_STICKER_SIZE_SP, MAX_STICKER_SIZE_SP) }

    suspend fun setImagesOnWifiOnly(enabled: Boolean) = edit { it[KEY_IMAGES_WIFI_ONLY] = enabled }

    suspend fun setExternalLinkTarget(target: ExternalLinkTarget) =
        edit { it[KEY_EXTERNAL_LINK_TARGET] = target.name }

    suspend fun setReportFormat(format: ReportFormat) = edit { it[KEY_REPORT_FORMAT] = format.name }

    /** 首页翻页栏; see [UserSettings.homePageBar] for what it adds. */
    suspend fun setHomePageBar(enabled: Boolean) = edit { it[KEY_HOME_PAGE_BAR] = enabled }

    suspend fun setUpdateCheckOnLaunch(enabled: Boolean) =
        edit { it[KEY_UPDATE_CHECK_ON_LAUNCH] = enabled }

    /** 接收 dev 版更新; see [UserSettings.updateDevChannel] for what the user is agreeing to. */
    suspend fun setUpdateDevChannel(enabled: Boolean) =
        edit { it[KEY_UPDATE_DEV_CHANNEL] = enabled }

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

    /**
     * How long 浏览历史 remembers. Anything outside [READ_HISTORY_LIMIT_CHOICES] is refused rather
     * than stored, so a bad write cannot leave the history capped at some number no picker can undo.
     *
     * Lowering it does not itself delete anything — the rows go on the next
     * [io.github.nodyssey.data.PostRepository.trimReadHistory]. The caller that changes this setting
     * is expected to ask for that trim; see the history screen's ViewModel.
     *
     * It caps the stored reading places as well, on the same reasoning that it caps the history:
     * both answer "how many threads does this app remember", and a second number for one of them
     * would be a cap nobody set and no picker could reach. They are trimmed together, by the same
     * caller — see [io.github.nodyssey.data.PostRepository.trimReadHistory].
     */
    suspend fun setReadHistoryLimit(limit: Int) =
        edit {
            it[KEY_READ_HISTORY_LIMIT] =
                if (limit in READ_HISTORY_LIMIT_CHOICES) limit else DEFAULT_READ_HISTORY_LIMIT
        }

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

    /**
     * The home strip's own arrangement: what order the pills sit in, and which ones the user parked.
     *
     * Written as one pair because they are one edit. Splitting them into two `edit` calls would let
     * the flow emit an order that has already moved a board to the tail while it is still marked
     * enabled — a frame where the strip is visibly wrong, for no benefit.
     *
     * This is *not* the site's 首页版块 preference ([setHomeBoardHidden]). That one is the account's
     * and can only touch three boards; this one is local, covers every board, and is reversible from
     * the strip itself. The two narrow the list independently and on purpose.
     */
    suspend fun setHomeBoardArrangement(
        order: List<String>,
        disabled: Set<String>,
    ) {
        dataStore.edit { preferences ->
            if (order.isEmpty()) {
                preferences.remove(KEY_HOME_BOARD_ORDER)
            } else {
                preferences[KEY_HOME_BOARD_ORDER] = encodeValues(order, MAX_HOME_BOARDS)
            }
            // Only slugs the order still knows about; a disabled board with no rank would come back
            // as a fresh board on the next read and silently re-enable itself.
            val ranked = disabled.filter { it in order }
            if (ranked.isEmpty()) {
                preferences.remove(KEY_DISABLED_HOME_BOARDS)
            } else {
                preferences[KEY_DISABLED_HOME_BOARDS] = encodeValues(ranked, MAX_HOME_BOARDS)
            }
        }
    }

    /**
     * Stores one composer's strip. An empty list clears the preference rather than writing one, so
     * "I put it back the way it was" and "I never touched it" stay the same stored state.
     */
    suspend fun setComposerToolbar(
        surface: ComposerSurface,
        actions: List<String>,
    ) {
        val key = when (surface) {
            ComposerSurface.POST -> KEY_POST_TOOLBAR
            ComposerSurface.REPLY -> KEY_REPLY_TOOLBAR
            ComposerSurface.MESSAGE -> KEY_MESSAGE_TOOLBAR
        }
        edit { preferences ->
            if (actions.isEmpty()) {
                preferences.remove(key)
            } else {
                preferences[key] = encodeValues(actions, MAX_TOOLBAR_ACTIONS)
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
        val preferences = preferences()
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

    /**
     * The last answer GitHub gave about a newer build — bookkeeping like [notificationSeenCounts],
     * not a user setting, and deliberately absent from [settings].
     *
     * It is stored at all so that "有新版本" is on screen the instant the app opens rather than a
     * network round trip later, and so a relaunch does not re-ask GitHub. A record that fails to
     * decode reads as "never checked", which costs one extra call and nothing else.
     */
    override suspend fun updateCheckRecord(): UpdateCheckRecord {
        val preferences = preferences()
        return UpdateCheckRecord(
            checkedAtMillis = preferences[KEY_UPDATE_CHECKED_AT] ?: 0L,
            devChannel = preferences[KEY_UPDATE_RECORD_DEV_CHANNEL] ?: false,
            release =
            preferences[KEY_UPDATE_RELEASE]?.let { stored ->
                runCatching { json.decodeFromString<AppRelease>(stored) }.getOrNull()
            },
        )
    }

    override suspend fun setUpdateCheckRecord(record: UpdateCheckRecord) =
        edit { preferences ->
            preferences[KEY_UPDATE_CHECKED_AT] = record.checkedAtMillis
            // The channel the answer came from, not the setting: the setting can change between two
            // checks, and that is exactly the case this field lets the repository notice.
            preferences[KEY_UPDATE_RECORD_DEV_CHANNEL] = record.devChannel
            val release = record.release
            if (release == null) {
                preferences.remove(KEY_UPDATE_RELEASE)
            } else {
                preferences[KEY_UPDATE_RELEASE] = json.encodeToString(release)
            }
        }

    /**
     * The one version the user has said 稍后 to, kept as a version name rather than a timestamp.
     *
     * A version name is what makes "不再提醒这个版本" survive: the next release has a different name
     * and asks again on its own, with no expiry to tune and nothing to reset when one ships.
     */
    override suspend fun devChannelEnabled(): Boolean =
        preferences()[KEY_UPDATE_DEV_CHANNEL] ?: devChannelDefault

    override suspend fun postponedUpdateVersion(): String? = preferences()[KEY_UPDATE_POSTPONED]

    override suspend fun setPostponedUpdateVersion(versionName: String) =
        edit { it[KEY_UPDATE_POSTPONED] = versionName }

    /** One snapshot of the store, with the same IOException fallback [settings] has. */
    private suspend fun preferences(): Preferences =
        dataStore.data
            .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
            .first()

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private fun emptyPreferences() = androidx.datastore.preferences.core.emptyPreferences()

    companion object {
        const val MIN_FONT_SCALE = 0.85f
        const val MAX_FONT_SCALE = 1.5f

        /*
         * 表情大小, in sp. The bottom of the range is the 20sp box a sticker has always had — the one
         * the body copy's 27sp line was designed around — and the top is the 90px the site's own
         * `img.sticker { max-width: 90px }` caps a sticker at, so the slider's far end and 表情统一缩限
         * switched off agree about how big the biggest sticker gets.
         */
        const val MIN_STICKER_SIZE_SP = 20
        const val MAX_STICKER_SIZE_SP = 90
        const val DEFAULT_STICKER_SIZE_SP = MIN_STICKER_SIZE_SP

        /** WorkManager's own floor is 15 minutes, which is why the choices start there — board f4. */
        val POLL_MINUTE_CHOICES = listOf(15, 30, 60)
        const val DEFAULT_POLL_MINUTES = 30

        /**
         * 无上限, expressed as a limit so that every caller stays one `LIMIT :n` and one `trimTo(n)`.
         *
         * A sentinel of 0 was the obvious alternative and is the dangerous one: a stray 0 anywhere in
         * this chain would silently mean "keep nothing", and these rows are the unread baselines. The
         * failure mode of the largest possible number is that nothing gets trimmed.
         */
        const val READ_HISTORY_UNLIMITED = Int.MAX_VALUE

        /**
         * How many threads 浏览历史 keeps.
         *
         * A row is a handful of strings — a thousand of them is well under a megabyte — so the cap is
         * not really about storage. It is about the second job these rows do: they are the baselines
         * behind 已读变灰 and 「N 条新回复」, and a small cap makes threads look unread again while the
         * reader still cares. That is why the default is 300 rather than the 100 the list itself
         * would be tidier at.
         */
        val READ_HISTORY_LIMIT_CHOICES = listOf(100, 300, 1000, READ_HISTORY_UNLIMITED)
        const val DEFAULT_READ_HISTORY_LIMIT = 300

        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

        /** Read only to migrate a store written before 配色来源 existed; nothing writes it now. */
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_COLOR_SOURCE = stringPreferencesKey("color_source")
        private val KEY_PRESET_SEED = intPreferencesKey("preset_seed")
        private val KEY_SEED_COLOR = intPreferencesKey("seed_color")
        private val KEY_WALLPAPER_SEED = intPreferencesKey("wallpaper_seed")
        private val KEY_WALLPAPER_SYSTEM_PALETTE = booleanPreferencesKey("wallpaper_system_palette")
        private val KEY_WALLPAPER_AUTO_UPDATE = booleanPreferencesKey("wallpaper_auto_update")
        private val KEY_PALETTE_STYLE = stringPreferencesKey("palette_style")
        private val KEY_SAVED_THEMES = stringPreferencesKey("saved_themes")

        /** 石墨青, the colour the app has always been, now as the seed every scheme starts from. */
        const val DEFAULT_SEED_COLOR: Int = 0xFF35606E.toInt()

        /** Enough that the chip row still fits on a phone; past that it stops being a shortcut. */
        const val MAX_SAVED_THEMES = 12

        /**
         * Stored names outlived the words on the screen.
         *
         * 品牌色 became 预设 and 自选 became 自定义 when 主题 grew a preset grid, but a store written
         * before that says `BRAND` and `SEED`. Reading those as the source the reader chose — rather
         * than dropping them and landing everyone back on the default — is the whole job here.
         */
        private fun decodeColorSource(stored: String): ColorSource? =
            when (stored) {
                "BRAND" -> ColorSource.PRESET
                "SEED" -> ColorSource.CUSTOM
                else -> runCatching { ColorSource.valueOf(stored) }.getOrNull()
            }
        private val KEY_FONT_SCALE = floatPreferencesKey("font_scale")
        private val KEY_STICKER_UNIFORM_SIZE = booleanPreferencesKey("sticker_uniform_size")
        private val KEY_STICKER_SIZE = intPreferencesKey("sticker_size_sp")
        private val KEY_IMAGES_WIFI_ONLY = booleanPreferencesKey("images_on_wifi_only")
        private val KEY_EXTERNAL_LINK_TARGET = stringPreferencesKey("external_link_target")
        private val KEY_REPORT_FORMAT = stringPreferencesKey("report_format")
        private val KEY_RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        private val KEY_SEARCH_HISTORY = stringPreferencesKey("search_history_v3")
        private val KEY_RECENT_BOARDS = stringPreferencesKey("recent_boards")
        private val KEY_HIDDEN_HOME_BOARDS = stringPreferencesKey("hidden_home_boards")
        private val KEY_HOME_BOARD_ORDER = stringPreferencesKey("home_board_order")
        private val KEY_DISABLED_HOME_BOARDS = stringPreferencesKey("disabled_home_boards")
        private val KEY_POST_TOOLBAR = stringPreferencesKey("post_toolbar_actions")
        private val KEY_REPLY_TOOLBAR = stringPreferencesKey("reply_toolbar_actions")
        private val KEY_MESSAGE_TOOLBAR = stringPreferencesKey("message_toolbar_actions")
        private val KEY_HOME_PAGE_BAR = booleanPreferencesKey("home_page_bar")
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
        private val KEY_READ_HISTORY_LIMIT = intPreferencesKey("read_history_limit")
        private val KEY_UPDATE_CHECKED_AT = longPreferencesKey("update_checked_at")
        private val KEY_UPDATE_RELEASE = stringPreferencesKey("update_latest_release")
        private val KEY_UPDATE_POSTPONED = stringPreferencesKey("update_postponed_version")
        private val KEY_UPDATE_CHECK_ON_LAUNCH = booleanPreferencesKey("update_check_on_launch")
        private val KEY_UPDATE_DEV_CHANNEL = booleanPreferencesKey("update_dev_channel")
        private val KEY_UPDATE_RECORD_DEV_CHANNEL = booleanPreferencesKey("update_checked_dev_channel")

        private const val RECENT_SEARCH_SEPARATOR = '\u001F'
        private const val MAX_RECENT_SEARCHES = 8
        private const val MAX_RECENT_BOARDS = 6

        /** The site has fifteen boards. The cap is only here so a corrupt write cannot grow forever. */
        private const val MAX_HOME_BOARDS = 64

        /** Comfortably above `EditorAction.entries.size`; a stored strip can never need more. */
        private const val MAX_TOOLBAR_ACTIONS = 32

        /**
         * How many threads 浏览历史 keeps, which is also how many reading places are kept.
         *
         * One reader, and every caller goes through [settings]. A stored value outside
         * [READ_HISTORY_LIMIT_CHOICES] reads as the default here as well as in [setReadHistoryLimit],
         * so a bad write cannot cap either of them at a number no picker can undo.
         */
        private fun readHistoryLimit(preferences: Preferences): Int =
            (preferences[KEY_READ_HISTORY_LIMIT] ?: DEFAULT_READ_HISTORY_LIMIT)
                .let { limit -> if (limit in READ_HISTORY_LIMIT_CHOICES) limit else DEFAULT_READ_HISTORY_LIMIT }

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
     * board and the retired sorts fold into their [FeedSort] equivalents, so two rows written by an older
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

/** Which composer's formatting strip a stored arrangement belongs to. */
enum class ComposerSurface { POST, REPLY, MESSAGE }

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorSource: ColorSource = ColorSource.PRESET,
    /** ARGB. The preset 预设 is on; only read while [colorSource] is [ColorSource.PRESET]. */
    val presetSeed: Int = SettingsRepository.DEFAULT_SEED_COLOR,
    /** ARGB. Only read while [colorSource] is [ColorSource.CUSTOM]; see `setSeedColor`. */
    val seedColor: Int = SettingsRepository.DEFAULT_SEED_COLOR,
    /**
     * ARGB of the wallpaper candidate that was picked, or null for "whatever the wallpaper leads
     * with". Null on a fresh install and after 壁纸变化时自动更新 discards a stale choice.
     */
    val wallpaperSeed: Int? = null,
    /** 使用系统调色板 — the OS's own Monet scheme rather than one generated from [wallpaperSeed]. */
    val wallpaperSystemPalette: Boolean = true,
    /** 壁纸变化时自动更新 — re-read the wallpaper on the next launch instead of keeping a seed. */
    val wallpaperAutoUpdate: Boolean = false,
    /** 色彩风格 — applies to whichever seed is in force, and to all three sources alike. */
    val paletteStyle: PaletteStyle = PaletteStyle.SOFT,
    /** 我的主题 — hand-picked seeds the reader named and kept. Oldest first; see `saveTheme`. */
    val savedThemes: List<SavedTheme> = emptyList(),
    val fontScale: Float = 1f,
    /**
     * 表情统一缩限. True — the default — draws every inline sticker in the same [stickerSize] square,
     * which at its smallest is the 20sp box that keeps a sticker inside a line of body text. False
     * draws each sticker at its own natural size, capped the way the site caps it.
     */
    val stickerUniformSize: Boolean = true,
    /** The side of that square in sp, between [SettingsRepository.MIN_STICKER_SIZE_SP] and its max. */
    val stickerSize: Int = SettingsRepository.DEFAULT_STICKER_SIZE_SP,
    val imagesOnWifiOnly: Boolean = false,
    val externalLinkTarget: ExternalLinkTarget = ExternalLinkTarget.CUSTOM_TAB,
    /** How a NodeQuality-style benchmark report is drawn in a post; see [ReportFormat]. */
    val reportFormat: ReportFormat = ReportFormat.ADAPTED,
    /**
     * Whether 首页 carries the same 翻页栏 the thread and 管理记录 have.
     *
     * On by default: 首页 is read by page number in the browser, and the reader who arrives from there
     * expects to be able to say "page 40" rather than fling for it. Switching it off is for the reader
     * who only scrolls, to whom a control naming the page is answering a question they never ask.
     *
     * It does not change how the feed loads either way. Pages still append while scrolling; the bar
     * only adds a way to arrive somewhere, which is the same pairing the comment thread uses.
     */
    val homePageBar: Boolean = true,
    /** Local mirror of the account's Remote 启用节日主题 switch. */
    val holidayTheme: Boolean = false,
    val searchHistory: List<SearchHistoryEntry> = emptyList(),
    val recentBoards: List<String> = emptyList(),
    /** Boards switched off the home feed. Empty — the default — hides nothing; see `setHomeBoardHidden`. */
    val hiddenHomeBoards: Set<String> = emptySet(),
    /**
     * The strip's arrangement, as edited by long-pressing it. Empty means "never customised": the
     * boards keep the order the API returned them in and none of them are parked.
     */
    val homeBoardOrder: List<String> = emptyList(),
    /** Boards parked at the tail of the strip. A subset of [homeBoardOrder]; see `setHomeBoardArrangement`. */
    val disabledHomeBoards: Set<String> = emptySet(),
    /*
     * The three formatting strips, as laid out through the wrench on the toolbar. Stored as
     * `EditorAction` names in the order they appear; empty means "never customised" and the surface
     * falls back to its own default set. Separate per surface on purpose — a topic wants a list and a
     * link where a reply wants a quote and an @, and one shared arrangement would have to drop one of
     * those from somebody's defaults to exist at all.
     */
    val postToolbarActions: List<String> = emptyList(),
    val replyToolbarActions: List<String> = emptyList(),
    val messageToolbarActions: List<String> = emptyList(),
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
    /** 浏览历史保留条数; [SettingsRepository.READ_HISTORY_UNLIMITED] for 无上限. */
    val readHistoryLimit: Int = SettingsRepository.DEFAULT_READ_HISTORY_LIMIT,
    /**
     * 启动时检查更新. On by default: an APK installed from Releases has nothing else to tell its owner
     * that a fix shipped, and the check is one conditional request every six hours at most.
     *
     * Off means neither the launch check nor the reminder it raises — 关于 still checks when opened and
     * on the 检查更新 button, which is a check the user asked for by being on that screen.
     */
    val updateCheckOnLaunch: Boolean = true,
    /**
     * 接收 dev 版更新 — whether the update check also offers the `vX.Y.Z-dev.N` test builds.
     *
     * Off, and opt-in on purpose: a dev tag is cut to try one thing out, it carries no CHANGELOG
     * section, and nothing promises it works. `.github/workflows/release.yml` publishes those as
     * GitHub prereleases, which is what keeps them away from everyone who has not turned this on.
     *
     * Turning it off does not undo an install: a device already running 1.3.0-dev.2 stays on it and
     * simply hears nothing further until a stable release passes that number.
     */
    val updateDevChannel: Boolean = false,
)

/**
 * How 设置 · 主题 decides light or dark. [SYSTEM] is the default, and the only automatic one: the
 * site's own 定时（日落）night mode used to live here as a fourth entry, and a value stored back then
 * reads as [SYSTEM] again, which is what that setting was asking for anyway.
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Where the seed comes from: one of the six presets, the system wallpaper (API 31+), or a colour the
 * reader dialled in themselves. All three end up in the same generator.
 *
 * One choice rather than a switch per source — they are alternatives, and two of them being on at
 * once would have no answer. Each remembers its own seed, so switching between them to compare is
 * free; see `setPresetSeed` for why that matters.
 *
 * The names on disk are `BRAND` and `SEED` for the first and last, from the build where the first
 * one meant a hand-tuned palette rather than a preset. `SettingsRepository.decodeColorSource` reads
 * those; renaming the constants without it would land every upgrading phone back on the default.
 */
enum class ColorSource { PRESET, WALLPAPER, CUSTOM }

/**
 * 色彩风格 — the five Material variants, in the order the chip row offers them.
 *
 * A thin mirror of `PlazaPaletteStyle`: `:designsys` cannot see this module, and the stored name has
 * to survive a rename on either side of that line.
 */
enum class PaletteStyle { SOFT, VIBRANT, EXPRESSIVE, NEUTRAL, MONOCHROME }

/**
 * One entry of 我的主题.
 *
 * The name is optional on the way in — j1 labels the field 名称（可选） — and an empty one is drawn
 * as the hex instead, so a chip is never blank.
 */
@Serializable
data class SavedTheme(
    val name: String,
    /** ARGB, and the identity of the entry: saving the same colour twice renames it. */
    val color: Int,
)

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
 * How a benchmark report inside a post is drawn.
 *
 * [ADAPTED] is the default: the report is read apart by
 * [io.github.nodyssey.core.report.QualityReportParser] and drawn again as ordinary rows, because
 * eighty columns of terminal art across a phone puts the type near 7sp.
 *
 * [SOURCE] draws it the way it was posted — the same terminal ground and fit-to-width type the
 * full-screen 查看原始报告 uses, inline in the floor. The scripts are versioned and the card is an
 * interpretation, so a reader who does not trust the interpretation gets to switch it off for good
 * rather than tapping through to the original on every report.
 */
enum class ReportFormat { ADAPTED, SOURCE }

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
    val sort: String = FeedSort.POST_TIME.name,
) {
    fun toDomain(): SearchHistoryEntry? {
        val resolvedTarget = runCatching { SearchTarget.valueOf(target) }.getOrNull() ?: return null
        val resolvedSort =
            when (sort) {
                // The retired labels for the same two `sortBy` values the site has always taken.
                "TIME" -> FeedSort.POST_TIME

                "RELEVANCE" -> FeedSort.LAST_REPLY

                else -> runCatching { FeedSort.valueOf(sort) }.getOrDefault(FeedSort.LAST_REPLY)
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
