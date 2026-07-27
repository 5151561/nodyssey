package io.github.nsreader.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.R
import io.github.nsreader.core.runCatchingExceptCancellation
import io.github.nsreader.data.account.AccountSettingsRepository
import io.github.nsreader.data.account.EndpointNotVerifiedException
import io.github.nsreader.data.settings.OPTIONAL_HOME_BOARD_SLUGS
import io.github.nsreader.data.settings.SettingsRepository
import io.github.nsreader.data.settings.ThemeMode
import io.github.nsreader.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for 偏好与首页版块 (d6 5/5).
 *
 * Every row belongs to exactly one of two stores, and the split is the site's own:
 *
 * - **Local** (this device, DataStore): 自动夜间模式 and its 依据. These write through
 *   [SettingsRepository] and never touch the network.
 * - **Remote** (this account, server): 启用节日主题 and the three 首页版块 switches. The account is
 *   the authority; DataStore only *mirrors* them so the feed and theme work offline. A toggle writes
 *   the mirror first — the app must obey the user immediately — and then tells the server. While the
 *   `/setting` endpoints are still stubbed ([EndpointNotVerifiedException]), the screen carries the
 *   standing pending banner instead of failing every toggle with a toast.
 */
class PreferencesViewModel(
    private val settings: SettingsRepository,
    private val account: AccountSettingsRepository,
) : ViewModel() {
    private val remote = MutableStateFlow(RemotePreferencesState())

    val uiState: StateFlow<PreferencesUiState> =
        combine(settings.settings, remote) { values, remoteState ->
            PreferencesUiState(
                holidayTheme = values.holidayTheme,
                autoNight = values.themeMode == ThemeMode.SYSTEM || values.themeMode == ThemeMode.TIMED,
                nightBasisTimed = values.themeMode == ThemeMode.TIMED,
                hiddenBoards = values.hiddenHomeBoards,
                endpointPending = remoteState.endpointPending,
                message = remoteState.message,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = PreferencesUiState(),
        )

    init {
        viewModelScope.launch {
            runCatchingExceptCancellation { account.remotePreferences() }
                .onSuccess { fetched ->
                    // The account answered, so its values win over whatever the mirror still holds
                    // from the last session — this is the sync-down half of the mirror contract.
                    settings.setHolidayTheme(fetched.holidayTheme)
                    val mirrored = settings.settings.first().hiddenHomeBoards
                    OPTIONAL_HOME_BOARD_SLUGS.forEach { slug ->
                        val hidden = slug in fetched.hiddenBoards
                        if (hidden != (slug in mirrored)) settings.setHomeBoardHidden(slug, hidden)
                    }
                }.onFailure { throwable ->
                    remote.update {
                        it.copy(
                            endpointPending = throwable is EndpointNotVerifiedException,
                            message =
                            if (throwable is EndpointNotVerifiedException) {
                                null
                            } else {
                                throwable.toAccountMessage(R.string.account_preferences_title)
                            },
                        )
                    }
                }
        }
    }

    fun setHolidayTheme(enabled: Boolean) {
        viewModelScope.launch {
            settings.setHolidayTheme(enabled)
            pushRemote { account.setHolidayTheme(enabled) }
        }
    }

    fun setAutoNight(enabled: Boolean) {
        viewModelScope.launch {
            // On restores the default basis (跟随系统 — the app enhancement d6 marks as default);
            // off lands on plain light, the state the switch visually returns the screen to.
            settings.setThemeMode(if (enabled) ThemeMode.SYSTEM else ThemeMode.LIGHT)
        }
    }

    fun setNightBasisTimed(timed: Boolean) {
        viewModelScope.launch {
            settings.setThemeMode(if (timed) ThemeMode.TIMED else ThemeMode.SYSTEM)
        }
    }

    fun setBoardHidden(slug: String, hidden: Boolean) {
        if (slug !in OPTIONAL_HOME_BOARD_SLUGS) return
        viewModelScope.launch {
            settings.setHomeBoardHidden(slug, hidden)
            pushRemote { account.setHomeBoardHidden(slug, hidden) }
        }
    }

    fun consumeMessage() = remote.update { it.copy(message = null) }

    /**
     * The sync-up half of the mirror contract. A pending endpoint raises the banner once and is
     * otherwise silent — the mirror already took the change, so from the user's side the toggle
     * worked; the banner is what says "…but only on this device, for now".
     */
    private suspend fun pushRemote(write: suspend () -> Unit) {
        runCatchingExceptCancellation { write() }
            .onFailure { throwable ->
                if (throwable is EndpointNotVerifiedException) {
                    remote.update { it.copy(endpointPending = true) }
                } else {
                    remote.update {
                        it.copy(message = throwable.toAccountMessage(R.string.account_preferences_title))
                    }
                }
            }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    PreferencesViewModel(
                        settings = container.settingsRepository,
                        account = container.accountSettingsRepository,
                    )
                }
            }
    }
}

private data class RemotePreferencesState(
    val endpointPending: Boolean = false,
    val message: AccountMessage? = null,
)

data class PreferencesUiState(
    val holidayTheme: Boolean = false,
    val autoNight: Boolean = true,
    val nightBasisTimed: Boolean = false,
    val hiddenBoards: Set<String> = emptySet(),
    val endpointPending: Boolean = false,
    val message: AccountMessage? = null,
)
