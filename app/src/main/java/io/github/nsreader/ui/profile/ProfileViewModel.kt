package io.github.nsreader.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.runCatchingExceptCancellation
import io.github.nsreader.data.PostRepository
import io.github.nsreader.data.ProfileRepository
import io.github.nsreader.data.UserProfile
import io.github.nsreader.data.session.SessionRepository
import io.github.nsreader.di.AppContainer
import io.github.nsreader.ui.postlist.toNodeSeekError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
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
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    private var signOutJob: Job? = null
    private var loadJob: Job? = null
    private val _uiState =
        MutableStateFlow(
            ProfileUiState(
                isSignedIn = session.state.value.isSignedIn,
                isLoading = session.state.value.isSignedIn,
            ),
        )
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        session.state
            .distinctUntilChangedBy { it.generation }
            .onEach { value ->
                if (value.isSignedIn) {
                    _uiState.update { it.copy(isSignedIn = true) }
                    refresh()
                } else {
                    loadJob?.cancel()
                    _uiState.value = ProfileUiState()
                }
            }.launchIn(viewModelScope)
    }

    fun refresh() {
        if (!session.state.value.isSignedIn) return
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isSignedIn = true, isLoading = true, error = null) }
                runCatchingExceptCancellation { profileRepository.profile(refresh = true) }
                    .onSuccess { profile -> _uiState.value = profile.toUiState() }
                    .onFailure { throwable ->
                        _uiState.update {
                            it.copy(isLoading = false, error = throwable.toNodeSeekError())
                        }
                    }
            }
    }

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
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ProfileViewModel(
                        session = container.sessionRepository,
                        postRepository = container.postRepository,
                        profileRepository = container.profileRepository,
                    )
                }
            }
    }
}

private fun UserProfile.toUiState(): ProfileUiState =
    ProfileUiState(
        isSignedIn = true,
        uid = uid,
        displayName = name,
        avatarUrl = avatarUrl,
        level = rank?.let { "Lv $it" },
        memberSince = memberSinceLabel(),
        chickenCount = chickenCount,
        starCount = starCount,
        streakDays = streakDays,
    )

private fun UserProfile.memberSinceLabel(): String {
    val match = createdAt?.let(REGISTERED_YEAR_MONTH::find)
    val date =
        match?.let {
            val year = it.groupValues[1].toIntOrNull()
            val month = it.groupValues[2].toIntOrNull()
            if (year != null && month != null) "${year}年${month}月 注册 · " else ""
        }.orEmpty()
    return "${date}UID $uid"
}

data class ProfileUiState(
    val isSignedIn: Boolean = false,
    val isLoading: Boolean = false,
    val error: NodeSeekError? = null,
    val uid: Long? = null,
    val displayName: String = "",
    val avatarUrl: String? = null,
    val level: String? = null,
    val memberSince: String? = null,
    val chickenCount: Int? = null,
    val starCount: Int? = null,
    val streakDays: Int? = null,
) {
    val hasProfile: Boolean
        get() = uid != null && displayName.isNotBlank()
}

private val REGISTERED_YEAR_MONTH = Regex("""^(\d{4})-(\d{2})""")
