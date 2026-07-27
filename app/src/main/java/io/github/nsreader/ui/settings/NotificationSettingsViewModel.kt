package io.github.nsreader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.data.settings.SettingsRepository
import io.github.nsreader.data.settings.UserSettings
import io.github.nsreader.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Board f4. Pure settings plumbing: every value lives in the settings SSOT, and the poll schedule
 * follows that SSOT from [io.github.nsreader.NodeSeekApp] — nothing here talks to WorkManager.
 */
class NotificationSettingsViewModel(
    private val settings: SettingsRepository,
) : ViewModel() {
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

    fun setNotifyMentions(value: Boolean) {
        viewModelScope.launch { settings.setNotifyMentions(value) }
    }

    fun setNotifyReplies(value: Boolean) {
        viewModelScope.launch { settings.setNotifyReplies(value) }
    }

    fun setNotifyMessages(value: Boolean) {
        viewModelScope.launch { settings.setNotifyMessages(value) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    NotificationSettingsViewModel(settings = container.settingsRepository)
                }
            }
    }
}
