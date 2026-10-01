package io.github.nodyssey.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.TitleKeywordStore
import io.github.nodyssey.data.TrackedThreadStore
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.model.TitleKeywordKind
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Board f4. Pure settings plumbing: every value lives in the settings SSOT, and the poll schedule
 * follows that SSOT from [io.github.nodyssey.NodysseyApp] — nothing here talks to WorkManager.
 */
class NotificationSettingsViewModel(
    private val settings: SettingsRepository,
    titleKeywords: TitleKeywordStore,
    trackedThreads: TrackedThreadStore,
) : ViewModel() {
    /** How many 提醒关键词 and followed threads there are — the two 管理 rows' subtitles. */
    val alertCounts: StateFlow<AlertCounts> =
        combine(titleKeywords.keywords(TitleKeywordKind.ALERT), trackedThreads.tracked) { keywords, tracked ->
            AlertCounts(keywords = keywords.size, trackedThreads = tracked.size)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertCounts())

    val uiState: StateFlow<UserSettings> =
        settings.settings
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UserSettings(),
            )

    fun setEnabled(value: Boolean) {
        viewModelScope.launch { settings.setNotificationsEnabled(value) }
    }

    fun setPollMinutes(value: Int) {
        viewModelScope.launch { settings.setNotificationPollMinutes(value) }
    }

    fun setWifiOnly(value: Boolean) {
        viewModelScope.launch { settings.setNotificationsWifiOnly(value) }
    }

    fun setQuietHours(value: Boolean) {
        viewModelScope.launch { settings.setNotificationQuietHours(value) }
    }

    fun setNotifyInteractions(value: Boolean) {
        viewModelScope.launch { settings.setNotifyInteractions(value) }
    }

    fun setNotifyMessages(value: Boolean) {
        viewModelScope.launch { settings.setNotifyMessages(value) }
    }

    fun setNotifyKeywordAlerts(value: Boolean) {
        viewModelScope.launch { settings.setNotifyKeywordAlerts(value) }
    }

    fun setNotifyTrackedThreads(value: Boolean) {
        viewModelScope.launch { settings.setNotifyTrackedThreads(value) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    NotificationSettingsViewModel(
                        settings = container.settingsRepository,
                        titleKeywords = container.titleKeywordStore,
                        trackedThreads = container.trackedThreadStore,
                    )
                }
            }
    }
}

data class AlertCounts(
    val keywords: Int = 0,
    val trackedThreads: Int = 0,
)
