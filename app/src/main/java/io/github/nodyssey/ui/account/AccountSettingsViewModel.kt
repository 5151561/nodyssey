package io.github.nodyssey.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.PostRepository
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.account.AccountSettingsRepository
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.di.AppContainer
import io.github.plaza.core.runCatchingExceptCancellation
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
 * The screen is a destination hub. Page-level summaries are static and only the blocked-user count is
 * a live value, so opening the hub does not prefetch data owned by its profile, security, contact, and
 * preference destinations.
 *
 * A failure to read the blocked-user count is not an error state. The row still navigates, because a
 * summary failure must not block access to the destination itself.
 */
class AccountSettingsViewModel(
    private val account: AccountSettingsRepository,
    private val profiles: ProfileRepository,
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
            signedOut,
        ) { site, isSignedOut ->
            AccountSettingsUiState(
                blockedCount = site.blockedCount,
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
                        posts = container.postRepository,
                        session = container.sessionRepository,
                    )
                }
            }
    }
}

/** The only remote summary rendered by the destination hub. */
private data class RemoteAccountState(
    val blockedCount: Int? = null,
)

data class AccountSettingsUiState(
    val blockedCount: Int? = null,
    /** Set once sign-out has committed; the screen pops itself on it. See `signOut`. */
    val signedOut: Boolean = false,
)
