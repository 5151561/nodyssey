package io.github.nsreader.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.runCatchingExceptCancellation
import io.github.nsreader.data.AssetsRepository
import io.github.nsreader.data.AttendanceStatus
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
    private val assetsRepository: AssetsRepository,
) : ViewModel() {
    private var signOutJob: Job? = null
    private var loadJob: Job? = null
    private var profileJob: Job? = null
    private var attendanceJob: Job? = null
    private var attendanceCheckedUid: Long? = null
    private val _uiState =
        MutableStateFlow(
            ProfileUiState(
                isSignedIn = session.state.value.isSignedIn,
                isLoading = session.state.value.isSignedIn,
            ),
        )
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        assetsRepository
            .observeAttendanceStatus()
            .filterNotNull()
            .onEach(::applyAttendanceStatus)
            .launchIn(viewModelScope)
        session.state
            .distinctUntilChangedBy { it.generation }
            .onEach { value ->
                if (value.isSignedIn) {
                    observeAndRefresh(value.fingerprint)
                } else {
                    loadJob?.cancel()
                    profileJob?.cancel()
                    attendanceJob?.cancel()
                    attendanceCheckedUid = null
                    _uiState.value = ProfileUiState()
                }
            }.launchIn(viewModelScope)
    }

    fun refresh() {
        if (!session.state.value.isSignedIn) return
        refresh(session.state.value.fingerprint)
    }

    /** Re-checks today's receipt when the retained profile tab becomes active again. */
    fun refreshAttendance() {
        _uiState.value.uid?.let(::refreshAttendance)
    }

    private fun observeAndRefresh(sessionFingerprint: Int) {
        loadJob?.cancel()
        profileJob?.cancel()
        attendanceJob?.cancel()
        attendanceCheckedUid = null
        _uiState.value = ProfileUiState(isSignedIn = true, isLoading = true)
        profileJob =
            profileRepository
                .observeProfile(sessionFingerprint)
                .filterNotNull()
                .onEach { profile ->
                    _uiState.update { current ->
                        profile.toUiState().copy(
                            isLoading = current.isLoading,
                            error = current.error,
                            isCheckingAttendance =
                            current.isCheckingAttendance.takeIf { current.uid == profile.uid } ?: false,
                            hasSignedInToday =
                            current.hasSignedInToday.takeIf { current.uid == profile.uid } ?: false,
                            attendanceGain =
                            current.attendanceGain.takeIf { current.uid == profile.uid },
                        )
                    }
                    if (attendanceCheckedUid != profile.uid) refreshAttendance(profile.uid)
                }.launchIn(viewModelScope)
        refresh(sessionFingerprint)
    }

    private fun refresh(sessionFingerprint: Int) {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isSignedIn = true, isLoading = true, error = null) }
                runCatchingExceptCancellation {
                    profileRepository.refreshProfile(sessionFingerprint)
                    // The write above is complete, but Room delivers invalidations asynchronously.
                    // Read the same SSOT flow before clearing loading so an empty frame cannot appear.
                    profileRepository.observeProfile(sessionFingerprint).filterNotNull().first()
                }.onSuccess { profile ->
                    _uiState.update { current ->
                        profile.toUiState().copy(
                            isCheckingAttendance = current.isCheckingAttendance,
                            hasSignedInToday = current.hasSignedInToday,
                            attendanceGain = current.attendanceGain,
                        )
                    }
                }
                    .onFailure { throwable ->
                        _uiState.update {
                            it.copy(isLoading = false, error = throwable.toNodeSeekError())
                        }
                    }
            }
    }

    private fun refreshAttendance(uid: Long) {
        attendanceJob?.cancel()
        attendanceCheckedUid = uid
        attendanceJob =
            viewModelScope.launch {
                _uiState.update { current ->
                    if (current.uid == uid) current.copy(isCheckingAttendance = true) else current
                }
                runCatchingExceptCancellation { assetsRepository.refreshAttendanceStatus(uid) }
                    .onSuccess(::applyAttendanceStatus)
                    .onFailure {
                        _uiState.update { current ->
                            if (current.uid == uid) current.copy(isCheckingAttendance = false) else current
                        }
                    }
            }
    }

    private fun applyAttendanceStatus(status: AttendanceStatus) {
        _uiState.update { current ->
            if (current.uid != status.uid) {
                current
            } else {
                current.copy(
                    isCheckingAttendance = false,
                    hasSignedInToday = status.hasSignedIn,
                    attendanceGain = status.gain,
                )
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
                profileRepository.clearCachedProfile()
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
                        assetsRepository = container.assetsRepository,
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
    val isCheckingAttendance: Boolean = false,
    val hasSignedInToday: Boolean = false,
    val attendanceGain: Int? = null,
) {
    val hasProfile: Boolean
        get() = uid != null && displayName.isNotBlank()
}

private val REGISTERED_YEAR_MONTH = Regex("""^(\d{4})-(\d{2})""")
