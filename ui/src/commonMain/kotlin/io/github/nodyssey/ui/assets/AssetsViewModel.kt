package io.github.nodyssey.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.core.LevelProgress
import io.github.nodyssey.core.LevelSpan
import io.github.nodyssey.data.AssetsRepository
import io.github.nodyssey.data.AttendanceBoardEntry
import io.github.nodyssey.data.AttendanceMode
import io.github.nodyssey.data.DailyQuota
import io.github.nodyssey.data.GrowthSnapshot
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.postlist.toSiteError
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssetsUiState(
    val isLoading: Boolean = true,
    val error: SiteError? = null,
    /** The signed-in account, so the attendance board can mark its own row. */
    val uid: Long? = null,
    val level: Int? = null,
    val chickenCount: Int? = null,
    val starCount: Int? = null,
    /** The current level's span — the level bar's zero is its floor, not the account's. */
    val levelSpan: LevelSpan? = null,
    val postQuota: DailyQuota = DailyQuota(null, null),
    val commentQuota: DailyQuota = DailyQuota(null, null),
    val attendanceQuota: DailyQuota = DailyQuota(null, null),
    val feedingQuota: DailyQuota = DailyQuota(null, null),
    val isSigningIn: Boolean = false,
    /** True when today's board contains the signed-in account or a sign-in just succeeded. */
    val attendanceGain: Int? = null,
    val attendanceMessage: String? = null,
    val hasSignedInToday: Boolean = false,
    /** True while the mode chooser (随机 / 固定 5 个) is on screen. */
    val choosingAttendanceMode: Boolean = false,
    val boardOpen: Boolean = false,
    val isLoadingBoard: Boolean = false,
    val board: List<AttendanceBoardEntry> = emptyList(),
    val boardError: SiteError? = null,
) {
    val hasData: Boolean get() = chickenCount != null || starCount != null || level != null

    /** Where the balance sits in its level; null until both are known. */
    val levelProgress: LevelProgress?
        get() = LevelProgress.of(chickenCount, levelSpan)
}

/**
 * State holder for 账户与成长.
 *
 * Levelling on NodeSeek is chicken-based — the progress bar *is* the chicken count — so this screen has
 * no separate growth number to fetch. Today's four allowances do come from the site, but a failed
 * allowance lookup is not a failed screen: the UiState carries those as unknown rather than as zero.
 */
class AssetsViewModel(
    private val repository: AssetsRepository,
    /** [io.github.nodyssey.data.ProfileRepository.selfUid]: the account this screen belongs to. */
    selfUid: StateFlow<Long?>,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AssetsUiState())
    val uiState: StateFlow<AssetsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var signInJob: Job? = null

    init {
        selfUid.onEach { uid -> _uiState.update { it.copy(uid = uid) } }.launchIn(viewModelScope)
        repository
            .observeAttendanceStatus()
            .filterNotNull()
            .onEach { status ->
                _uiState.update {
                    it.copy(
                        hasSignedInToday = status.hasSignedIn,
                        attendanceGain = status.gain,
                    )
                }
            }.launchIn(viewModelScope)
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                runCatchingExceptCancellation { repository.growth() }
                    .onSuccess { snapshot -> _uiState.update { it.withGrowth(snapshot) } }
                    .onFailure { throwable ->
                        _uiState.update { it.copy(isLoading = false, error = throwable.toSiteError()) }
                    }
            }
    }

    fun requestAttendance() = _uiState.update { it.copy(choosingAttendanceMode = true) }

    fun dismissAttendanceChooser() = _uiState.update { it.copy(choosingAttendanceMode = false) }

    /**
     * Signs in for the day, in whichever of the site's two modes the user picked.
     *
     * The result is kept as the site's own sentence rather than translated into a boolean: a repeat
     * sign-in is answered with "今天已完成签到", which is more useful to show than to classify.
     */
    fun signInForToday(mode: AttendanceMode) {
        if (signInJob?.isActive == true) return
        signInJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isSigningIn = true, choosingAttendanceMode = false) }
                runCatchingExceptCancellation { repository.signInForToday(mode) }
                    .onSuccess { result ->
                        _uiState.update {
                            it.copy(
                                isSigningIn = false,
                                hasSignedInToday = true,
                                attendanceGain = result.gain ?: it.attendanceGain,
                                attendanceMessage = result.message,
                            )
                        }
                        // Chicken earned moves the level bar, so the account is re-read rather than
                        // patched locally: the server's number is the one that matters here.
                        refresh()
                    }.onFailure { throwable ->
                        _uiState.update {
                            it.copy(isSigningIn = false, error = throwable.toSiteError())
                        }
                    }
            }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun openBoard() {
        _uiState.update { it.copy(boardOpen = true) }
        if (_uiState.value.board.isEmpty()) loadBoard()
    }

    fun dismissBoard() = _uiState.update { it.copy(boardOpen = false) }

    fun loadBoard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBoard = true, boardError = null) }
            runCatchingExceptCancellation { repository.attendanceBoard() }
                .onSuccess { entries ->
                    _uiState.update { it.copy(isLoadingBoard = false, board = entries) }
                }.onFailure { throwable ->
                    _uiState.update {
                        it.copy(isLoadingBoard = false, boardError = throwable.toSiteError())
                    }
                }
        }
    }

    private fun AssetsUiState.withGrowth(snapshot: GrowthSnapshot): AssetsUiState =
        copy(
            isLoading = false,
            error = null,
            level = snapshot.level,
            chickenCount = snapshot.chickenCount,
            starCount = snapshot.starCount,
            levelSpan = snapshot.levelSpan,
            postQuota = snapshot.postQuota,
            commentQuota = snapshot.commentQuota,
            attendanceQuota = snapshot.attendanceQuota,
            feedingQuota = snapshot.feedingQuota,
        )

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { AssetsViewModel(container.assetsRepository, container.profileRepository.selfUid) }
            }
    }
}
