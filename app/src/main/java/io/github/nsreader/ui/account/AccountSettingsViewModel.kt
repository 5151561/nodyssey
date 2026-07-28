package io.github.nsreader.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.core.runCatchingExceptCancellation
import io.github.nsreader.data.PostRepository
import io.github.nsreader.data.ProfileRepository
import io.github.nsreader.data.account.AccountProfileFields
import io.github.nsreader.data.account.AccountSettingsRepository
import io.github.nsreader.data.account.TelegramBinding
import io.github.nsreader.data.session.SessionRepository
import io.github.nsreader.data.settings.SettingsRepository
import io.github.nsreader.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for 账号设置 (8g).
 *
 * The screen is a hub, and the only thing that makes it more than a list of links is the current-value
 * subtitle on each row. Those come from three different places — the forum profile, the site's setting
 * page, and the app's own preferences — so the merge happens here rather than in the composable.
 *
 * A failure to read the site half is not an error state. The rows still navigate, and 8g showing an
 * error screen because the blocked-user count could not be fetched would block access to 首页版块,
 * which is entirely local and always works.
 */
class AccountSettingsViewModel(
    private val account: AccountSettingsRepository,
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository,
    private val posts: PostRepository,
    private val session: SessionRepository,
) : ViewModel() {
    private val remote = MutableStateFlow(RemoteAccountState())
    private val signedOut = MutableStateFlow(false)
    private var loadJob: Job? = null
    private var signOutJob: Job? = null

    val uiState: StateFlow<AccountSettingsUiState> =
        combine(
            remote,
            settings.settings,
            signedOut,
        ) { site, preferences, isSignedOut ->
            AccountSettingsUiState(
                avatarUrl = site.avatarUrl,
                displayName = site.displayName,
                fields = site.fields,
                twoFactorEnabled = site.twoFactorEnabled,
                email = site.email,
                telegram = site.telegram,
                blockedCount = site.blockedCount,
                hiddenBoardCount = preferences.hiddenHomeBoards.size,
                holidayTheme = preferences.holidayTheme,
                signedOut = isSignedOut,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = AccountSettingsUiState(),
        )

    init {
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                // The forum profile is the half that works today, so it is read on its own rather than
                // inside the same runCatching as the settings-page calls that are still stubbed out.
                runCatchingExceptCancellation { profiles.profile() }
                    .onSuccess { profile ->
                        remote.update { it.copy(avatarUrl = profile.avatarUrl, displayName = profile.name) }
                    }
                runCatchingExceptCancellation { account.profileFields() }
                    .onSuccess { fields -> remote.update { it.copy(fields = fields) } }
                runCatchingExceptCancellation { account.twoFactor() }
                    .onSuccess { state -> remote.update { it.copy(twoFactorEnabled = state.enabled) } }
                runCatchingExceptCancellation { account.contact() }
                    .onSuccess { contact -> remote.update { it.copy(email = contact.email) } }
                runCatchingExceptCancellation { account.telegramBinding() }
                    .onSuccess { binding -> remote.update { it.copy(telegram = binding) } }
                runCatchingExceptCancellation { account.blockedUsers() }
                    .onSuccess { blocked -> remote.update { it.copy(blockedCount = blocked.size) } }
            }
    }

    /**
     * Signs out, then reports it through [AccountSettingsUiState.signedOut].
     *
     * A flag rather than a completion callback, for the same reason as `HomeBoardsViewModel.save`: a
     * lambda captured across the suspend point would navigate a back stack that a configuration change
     * has already replaced, leaving the user on the account settings of an account nobody is in.
     */
    fun signOut() {
        if (signOutJob?.isActive == true) return
        signOutJob =
            viewModelScope.launch {
                // Same order as 我的: drop authenticated rows before the signed-out state is published,
                // or the tabs still holding a Navigation 3 entry keep showing what they already drew.
                posts.clearSessionData()
                profiles.clearCachedProfile()
                session.signOut()
                signedOut.value = true
            }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    AccountSettingsViewModel(
                        account = container.accountSettingsRepository,
                        profiles = container.profileRepository,
                        settings = container.settingsRepository,
                        posts = container.postRepository,
                        session = container.sessionRepository,
                    )
                }
            }
    }
}

/** Everything 8g reads off the network, kept apart from the preferences it merges with. */
private data class RemoteAccountState(
    val avatarUrl: String? = null,
    val displayName: String = "",
    val fields: AccountProfileFields? = null,
    val twoFactorEnabled: Boolean? = null,
    val email: String? = null,
    val telegram: TelegramBinding? = null,
    val blockedCount: Int? = null,
)

data class AccountSettingsUiState(
    val avatarUrl: String? = null,
    val displayName: String = "",
    val fields: AccountProfileFields? = null,
    val twoFactorEnabled: Boolean? = null,
    val email: String? = null,
    /** null until the (stubbed) endpoint answers; the row shows no guess. */
    val telegram: TelegramBinding? = null,
    val blockedCount: Int? = null,
    /** How many of the three optional home boards are switched off; 0 means the feed shows everything. */
    val hiddenBoardCount: Int = 0,
    val holidayTheme: Boolean = false,
    /** Set once sign-out has committed; the screen pops itself on it. See `signOut`. */
    val signedOut: Boolean = false,
)
