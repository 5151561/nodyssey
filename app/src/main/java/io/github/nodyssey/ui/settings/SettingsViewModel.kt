package io.github.nodyssey.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.PostRepository
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.data.settings.ExternalLinkTarget
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val posts: PostRepository,
    private val session: SessionRepository,
    private val updates: AppUpdateRepository,
    appVersion: AppVersion,
) : ViewModel() {
    private val clearingCache = MutableStateFlow(false)

    private val versionName = appVersion.name.ifBlank { "—" }

    val uiState: StateFlow<SettingsUiState> =
        combine(settings.settings, clearingCache, updates.state) { values, clearing, update ->
            SettingsUiState(
                settings = values,
                isClearingCache = clearing,
                versionName = versionName,
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

    fun setThemeMode(value: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(value) }
    }

    fun setFontScale(value: Float) {
        viewModelScope.launch { settings.setFontScale(value) }
    }

    fun setImagesOnWifiOnly(value: Boolean) {
        viewModelScope.launch { settings.setImagesOnWifiOnly(value) }
    }

    fun setExternalLinkTarget(value: ExternalLinkTarget) {
        viewModelScope.launch { settings.setExternalLinkTarget(value) }
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
            } finally {
                clearingCache.value = false
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    SettingsViewModel(
                        settings = container.settingsRepository,
                        posts = container.postRepository,
                        session = container.sessionRepository,
                        updates = container.appUpdateRepository,
                        appVersion = container.appVersion,
                    )
                }
            }
    }
}

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val isClearingCache: Boolean = false,
    /** The installed build's own version, as `PackageManager` reports it. */
    val versionName: String = "—",
    /** The newer version on GitHub, or null when there is none to offer. */
    val updateVersionName: String? = null,
)
