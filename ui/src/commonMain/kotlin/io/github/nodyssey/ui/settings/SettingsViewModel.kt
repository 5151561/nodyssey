package io.github.nodyssey.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.AppCacheStore
import io.github.nodyssey.data.PostRepository
import io.github.nodyssey.data.dns.DohSupport
import io.github.nodyssey.data.imagehost.ImageHostRepository
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.data.settings.AppLanguage
import io.github.nodyssey.data.settings.ReportFormat
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.settings.ThemeMode
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.data.update.AppUpdateRepository
import io.github.nodyssey.di.AppContainer
import io.github.plaza.core.AppVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val posts: PostRepository,
    private val session: SessionRepository,
    private val cache: AppCacheStore,
    private val updates: AppUpdateRepository,
    imageHost: ImageHostRepository,
    appVersion: AppVersion,
    /** Null where the platform cannot apply one, which is how the 加密 DNS row knows to stay away. */
    doh: DohSupport?,
) : ViewModel() {
    private val clearingCache = MutableStateFlow(false)

    /** Null until the first measurement lands; walking the cache directory is not instant. */
    private val cacheSizeBytes = MutableStateFlow<Long?>(null)

    private val versionName = appVersion.name.ifBlank { "—" }

    /**
     * The two rows on this screen that report on a screen behind them, folded into one flow.
     *
     * Folded rather than combined alongside the rest because `combine` has a typed overload for five
     * flows and this would have been the sixth; two subtitles are a smaller thing than an array of
     * `Any?` to unpack.
     */
    private val entries =
        doh?.settings?.config?.let { config ->
            combine(imageHost.current, config) { host, doh -> SettingsEntries(host.isConfigured, doh.enabled) }
        } ?: imageHost.current.map { host -> SettingsEntries(host.isConfigured, dohEnabled = null) }

    val uiState: StateFlow<SettingsUiState> =
        combine(
            settings.settings,
            clearingCache,
            cacheSizeBytes,
            updates.state,
            entries,
        ) { values, clearing, cacheSize, update, entries ->
            SettingsUiState(
                settings = values,
                isClearingCache = clearing,
                cacheSizeBytes = cacheSize,
                versionName = versionName,
                imageHostConnected = entries.imageHostConnected,
                dohEnabled = entries.dohEnabled,
                // Read off the shared updater rather than checked here: the answer is already in
                // memory by the time this screen opens, and 我的 shows the same dot from the same
                // state.
                updateVersionName = update.available?.versionName,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(versionName = versionName),
        )

    init {
        measureCache()
    }

    /** 明暗 is the one theme control 设置 kept; the rest are on [ThemeSettingsViewModel]. */
    fun setThemeMode(value: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(value) }
    }

    /**
     * 语言 — stored here and applied by the platform.
     *
     * Nothing else happens on this call: `ApplyAppLanguage` at the root of the composition is
     * watching the same settings flow, and on Android that is what recreates the activity under the
     * new locale. A ViewModel that also poked the platform would be a second path to the same
     * change, and the two would disagree the first time one of them was skipped.
     */
    fun setAppLanguage(value: AppLanguage) {
        viewModelScope.launch { settings.setAppLanguage(value) }
    }

    /** 单手模式 — one answer for every screen that carries a `OneHandTopAppBar`. */
    fun setOneHandMode(value: Boolean) {
        viewModelScope.launch { settings.setOneHandMode(value) }
    }

    fun setFontScale(value: Float) {
        viewModelScope.launch { settings.setFontScale(value) }
    }

    fun setStickerUniformSize(value: Boolean) {
        viewModelScope.launch { settings.setStickerUniformSize(value) }
    }

    fun setStickerSize(value: Int) {
        viewModelScope.launch { settings.setStickerSize(value) }
    }

    fun setImagesOnWifiOnly(value: Boolean) {
        viewModelScope.launch { settings.setImagesOnWifiOnly(value) }
    }

    fun setReportFormat(value: ReportFormat) {
        viewModelScope.launch { settings.setReportFormat(value) }
    }

    fun setHomePageBar(value: Boolean) {
        viewModelScope.launch { settings.setHomePageBar(value) }
    }

    fun setUpdateCheckOnLaunch(value: Boolean) {
        viewModelScope.launch { settings.setUpdateCheckOnLaunch(value) }
    }

    /**
     * 接收 dev 版更新, and then a check on the spot.
     *
     * Forced rather than left to the next launch: the switch is flipped by someone who wants to know
     * whether there *is* a test build, and the six-hour stored answer came from the other channel.
     */
    fun setUpdateDevChannel(value: Boolean) {
        viewModelScope.launch {
            settings.setUpdateDevChannel(value)
            updates.check(force = true)
        }
    }

    fun clearCache() {
        if (clearingCache.value) return
        viewModelScope.launch {
            clearingCache.value = true
            try {
                val currentSession = session.state.value
                posts.clearCache(currentSession.isSignedIn, currentSession.fingerprint)
                // The database is the smaller half. The files beside it — images, WebView, a
                // downloaded APK — are what the number on the row is made of.
                cache.clear()
            } finally {
                clearingCache.value = false
            }
            measureCache()
        }
    }

    private fun measureCache() {
        viewModelScope.launch { cacheSizeBytes.value = cache.sizeBytes() }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    SettingsViewModel(
                        settings = container.settingsRepository,
                        posts = container.postRepository,
                        session = container.sessionRepository,
                        cache = container.appCacheStore,
                        updates = container.appUpdateRepository,
                        imageHost = container.imageHostRepository,
                        appVersion = container.appVersion,
                        doh = container.doh,
                    )
                }
            }
    }
}

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val isClearingCache: Boolean = false,
    /** What 清除缓存 would reclaim, or null while it is still being measured. */
    val cacheSizeBytes: Long? = null,
    /** The installed build's own version, as `PackageManager` reports it. */
    val versionName: String = "—",
    /** The newer version on GitHub, or null when there is none to offer. */
    val updateVersionName: String? = null,
    /** Whether the selected image host is usable — the 图床 row's subtitle, and nothing more of it. */
    val imageHostConnected: Boolean = false,
    /**
     * Whether 加密 DNS is on, or null where the platform has none to offer — in which case the row is
     * not drawn at all, the same way 默认打开方式 is absent where the system has no such switch.
     */
    val dohEnabled: Boolean? = null,
)

private data class SettingsEntries(val imageHostConnected: Boolean, val dohEnabled: Boolean?)
