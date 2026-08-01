package io.github.nodyssey.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.PostRepository
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.data.settings.ExternalLinkTarget
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.settings.ThemeMode
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.di.AppContainer
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
) : ViewModel() {
    private val clearingCache = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> =
        combine(settings.settings, clearingCache) { values, clearing ->
            SettingsUiState(settings = values, isClearingCache = clearing)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
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
                    )
                }
            }
    }
}

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val isClearingCache: Boolean = false,
)
