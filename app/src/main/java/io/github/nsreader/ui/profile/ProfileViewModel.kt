package io.github.nsreader.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.data.PostRepository
import io.github.nsreader.data.session.SessionRepository
import io.github.nsreader.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State holder for "我的".
 *
 * It derives its state from [SessionRepository] rather than keeping a flag of its own, so signing out
 * from here and signing in through the WebView cannot disagree about which one happened last.
 */
class ProfileViewModel(
    private val session: SessionRepository,
    private val postRepository: PostRepository,
) : ViewModel() {
    private var signOutJob: Job? = null
    val uiState: StateFlow<ProfileUiState> =
        session.state
            .map { ProfileUiState(isSignedIn = it.isSignedIn) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = ProfileUiState(isSignedIn = session.state.value.isSignedIn),
            )

    fun signOut() {
        if (signOutJob?.isActive == true) return
        signOutJob =
            viewModelScope.launch {
                // Remove authenticated content before publishing the signed-out state. Other tabs
                // keep their Navigation 3 entries alive, so clearing cookies alone would leave their
                // already-rendered private rows readable.
                postRepository.clearSessionData()
                session.signOut()
            }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ProfileViewModel(
                        session = container.sessionRepository,
                        postRepository = container.postRepository,
                    )
                }
            }
    }
}

data class ProfileUiState(
    val isSignedIn: Boolean = false,
)
