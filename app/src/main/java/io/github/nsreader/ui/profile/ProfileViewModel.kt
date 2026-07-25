package io.github.nsreader.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.data.session.SessionRepository
import io.github.nsreader.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * State holder for "我的".
 *
 * It derives its state from [SessionRepository] rather than keeping a flag of its own, so signing out
 * from here and signing in through the WebView cannot disagree about which one happened last.
 */
class ProfileViewModel(
    private val session: SessionRepository,
) : ViewModel() {
    val uiState: StateFlow<ProfileUiState> =
        session.state
            .map { ProfileUiState(isSignedIn = it.isSignedIn) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = ProfileUiState(isSignedIn = session.state.value.isSignedIn),
            )

    fun signOut() = session.signOut()

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ProfileViewModel(container.sessionRepository) }
            }
    }
}

data class ProfileUiState(
    val isSignedIn: Boolean = false,
)
