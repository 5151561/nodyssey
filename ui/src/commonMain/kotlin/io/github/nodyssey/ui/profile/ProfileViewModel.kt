package io.github.nodyssey.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.AssetsRepository
import io.github.nodyssey.data.AttendanceBoardEntry
import io.github.nodyssey.data.AttendanceMode
import io.github.nodyssey.data.AttendanceStatus
import io.github.nodyssey.data.PostRepository
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.UserProfile
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.postlist.toSiteError
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.runCatchingExceptCancellation
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
    private var attendanceBoardJob: Job? = null
    private var signInJob: Job? = null
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
                    attendanceBoardJob?.cancel()
                    signInJob?.cancel()
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
        attendanceBoardJob?.cancel()
        signInJob?.cancel()
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
                            attendanceKnown =
                            current.attendanceKnown.takeIf { current.uid == profile.uid } ?: false,
                            hasSignedInToday =
                            current.hasSignedInToday.takeIf { current.uid == profile.uid } ?: false,
                            attendanceGain =
                            current.attendanceGain.takeIf { current.uid == profile.uid },
                            attendanceMessage =
                            current.attendanceMessage.takeIf { current.uid == profile.uid },
                            choosingAttendanceMode =
                            current.choosingAttendanceMode.takeIf { current.uid == profile.uid } ?: false,
                            isSigningIn = current.isSigningIn.takeIf { current.uid == profile.uid } ?: false,
                            attendanceFailure =
                            current.attendanceFailure.takeIf { current.uid == profile.uid },
                            boardOpen = current.boardOpen.takeIf { current.uid == profile.uid } ?: false,
                            isLoadingBoard =
                            current.isLoadingBoard.takeIf { current.uid == profile.uid } ?: false,
                            board = current.board.takeIf { current.uid == profile.uid }.orEmpty(),
                            boardError = current.boardError.takeIf { current.uid == profile.uid },
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
                            attendanceKnown = current.attendanceKnown,
                            hasSignedInToday = current.hasSignedInToday,
                            attendanceGain = current.attendanceGain,
                            attendanceMessage = current.attendanceMessage,
                            choosingAttendanceMode = current.choosingAttendanceMode,
                            isSigningIn = current.isSigningIn,
                            attendanceFailure = current.attendanceFailure,
                            boardOpen = current.boardOpen,
                            isLoadingBoard = current.isLoadingBoard,
                            board = current.board,
                            boardError = current.boardError,
                        )
                    }
                }
                    .onFailure { throwable ->
                        _uiState.update {
                            it.copy(isLoading = false, error = throwable.toSiteError())
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
                    attendanceKnown = true,
                    hasSignedInToday = status.hasSignedIn,
                    attendanceGain = status.gain,
                )
            }
        }
    }

    fun requestAttendance() {
        _uiState.update { it.copy(choosingAttendanceMode = true) }
    }

    fun dismissAttendanceChooser() {
        _uiState.update { it.copy(choosingAttendanceMode = false) }
    }

    /**
     * Signs in for the day without leaving 我的.
     *
     * The button used to push 账户与成长 with the chooser pre-opened, which turned one tap into a
     * screen the user then had to back out of — and, because the pushed key still asked for the
     * chooser, backing out reopened it. The sign-in itself is one request against a repository this
     * screen already holds, so it happens here and the shared [AttendanceStatus] keeps 账户与成长 in
     * agreement.
     */
    fun signInForToday(mode: AttendanceMode) {
        if (signInJob?.isActive == true) return
        signInJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isSigningIn = true, choosingAttendanceMode = false) }
                runCatchingExceptCancellation { assetsRepository.signInForToday(mode) }
                    .onSuccess { result ->
                        _uiState.update {
                            it.copy(
                                isSigningIn = false,
                                attendanceKnown = true,
                                hasSignedInToday = true,
                                attendanceGain = result.gain ?: it.attendanceGain,
                                attendanceMessage = result.message,
                            )
                        }
                        // Chicken earned is chicken the header is showing, so the profile is re-read
                        // rather than patched locally.
                        refresh()
                    }.onFailure { throwable ->
                        _uiState.update {
                            it.copy(isSigningIn = false, attendanceFailure = throwable.toSiteError())
                        }
                    }
            }
    }

    /** Clears the sign-in failure once its snackbar has been shown. */
    fun attendanceFailureShown() {
        _uiState.update { it.copy(attendanceFailure = null) }
    }

    fun openAttendanceBoard() {
        _uiState.update { it.copy(boardOpen = true) }
        if (_uiState.value.board.isEmpty()) loadAttendanceBoard()
    }

    fun dismissAttendanceBoard() {
        _uiState.update { it.copy(boardOpen = false) }
    }

    fun loadAttendanceBoard() {
        attendanceBoardJob?.cancel()
        attendanceBoardJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingBoard = true, boardError = null) }
                runCatchingExceptCancellation { assetsRepository.attendanceBoard() }
                    .onSuccess { entries ->
                        _uiState.update {
                            it.copy(
                                isLoadingBoard = false,
                                board = entries,
                            )
                        }
                    }.onFailure { throwable ->
                        _uiState.update {
                            it.copy(
                                isLoadingBoard = false,
                                boardError = throwable.toSiteError(),
                            )
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
    val error: SiteError? = null,
    val uid: Long? = null,
    val displayName: String = "",
    val avatarUrl: String? = null,
    val level: String? = null,
    val memberSince: String? = null,
    val chickenCount: Int? = null,
    val starCount: Int? = null,
    val isCheckingAttendance: Boolean = false,
    /** Whether today's receipt has been read at least once; a re-check never un-answers it. */
    val attendanceKnown: Boolean = false,
    val hasSignedInToday: Boolean = false,
    val attendanceGain: Int? = null,
    /** The site's own sentence about today's sign-in, shown when it did not answer with a count. */
    val attendanceMessage: String? = null,
    /** True while the 随机 / 固定 5 个 chooser is on screen. */
    val choosingAttendanceMode: Boolean = false,
    val isSigningIn: Boolean = false,
    val attendanceFailure: SiteError? = null,
    val boardOpen: Boolean = false,
    val isLoadingBoard: Boolean = false,
    val board: List<AttendanceBoardEntry> = emptyList(),
    val boardError: SiteError? = null,
) {
    val hasProfile: Boolean
        get() = uid != null && displayName.isNotBlank()

    /**
     * Whether the sign-in button has nothing to show yet.
     *
     * Only this may put the button into its spinner: a check running over an answer we already have
     * is a background refresh, and regressing 已签到 to 检查中… on every visit was the bug.
     */
    val isAttendanceUnknown: Boolean
        get() = isCheckingAttendance && !attendanceKnown
}

private val REGISTERED_YEAR_MONTH = Regex("""^(\d{4})-(\d{2})""")
