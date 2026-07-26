package io.github.nsreader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.core.runCatchingExceptCancellation
import io.github.nsreader.data.PostRepository
import io.github.nsreader.data.ProfileRepository
import io.github.nsreader.data.session.SessionRepository
import io.github.nsreader.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the account-settings list can say about the account today.
 *
 * Only the four values the account endpoint publishes are here. Password state, 2FA enrolment, the
 * email address and the block list all live on `/setting`, which renders client-side; their rows show
 * no current value rather than a plausible one.
 */
data class AccountSettingsUiState(
    val name: String = "",
    val avatarUrl: String? = null,
    val bio: String? = null,
    val readmeLength: Int? = null,
)

class AccountSettingsViewModel(
    private val profileRepository: ProfileRepository,
    private val postRepository: PostRepository,
    private val session: SessionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountSettingsUiState())
    val uiState: StateFlow<AccountSettingsUiState> = _uiState.asStateFlow()

    private var signOutJob: Job? = null

    init {
        viewModelScope.launch {
            runCatchingExceptCancellation { profileRepository.profile() }
                .onSuccess { profile ->
                    _uiState.update {
                        it.copy(
                            name = profile.name,
                            avatarUrl = profile.avatarUrl,
                            bio = profile.bio,
                            readmeLength = profile.readme?.length,
                        )
                    }
                }
        }
    }

    /** Same order as 我的: private content is dropped before the signed-out state is published. */
    fun signOut() {
        if (signOutJob?.isActive == true) return
        signOutJob =
            viewModelScope.launch {
                postRepository.clearSessionData()
                session.signOut()
            }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    AccountSettingsViewModel(
                        profileRepository = container.profileRepository,
                        postRepository = container.postRepository,
                        session = container.sessionRepository,
                    )
                }
            }
    }
}
