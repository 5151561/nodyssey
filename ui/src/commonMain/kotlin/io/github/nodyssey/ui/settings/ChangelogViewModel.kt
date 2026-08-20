package io.github.nodyssey.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.update.AppUpdateRepository
import io.github.nodyssey.di.AppContainer
import io.github.plaza.core.update.AppUpdateException
import io.github.plaza.core.update.ReleaseNote
import io.github.plaza.core.update.UpdateFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 更新日志, read from the project's own GitHub Releases.
 *
 * The page used to say "this build ships no structured changelog" and point at the releases page,
 * which was true and unhelpful: the notes are published, they are the CHANGELOG section for that
 * version, and the app already talks to that API for the update check. Read live rather than bundled
 * so a build installed months ago can still show what has shipped since — which is the version anyone
 * on an old APK actually wants to read.
 *
 * 接收 dev 版更新 decides whether the test builds are listed too: someone who is not on that channel
 * cannot install them, so listing them would only be noise.
 *
 * Opening the screen does not necessarily spend a call: the repository keeps the last answer for a day,
 * because GitHub's anonymous quota is counted per address and shared with everyone behind it. 刷新 is
 * what asks again.
 */
class ChangelogViewModel(
    private val updates: AppUpdateRepository,
    private val currentVersionName: String,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ChangelogUiState(currentVersionName = currentVersionName))
    val uiState: StateFlow<ChangelogUiState> = mutableUiState.asStateFlow()

    init {
        load()
    }

    /**
     * 刷新, and the same thing the error state's 重试 does.
     *
     * Always forced: both buttons are pressed by someone who has just been told something they did not
     * like, and answering either from the day-old cache would look like the button does nothing.
     */
    fun refresh() = load(force = true)

    private fun load(force: Boolean = false) {
        viewModelScope.launch {
            mutableUiState.value =
                ChangelogUiState(currentVersionName = currentVersionName, loading = true)
            mutableUiState.value =
                try {
                    ChangelogUiState(
                        currentVersionName = currentVersionName,
                        releases = updates.releaseNotes(force = force),
                    )
                } catch (e: AppUpdateException) {
                    ChangelogUiState(currentVersionName = currentVersionName, failure = e.failure)
                }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ChangelogViewModel(
                        updates = container.appUpdateRepository,
                        currentVersionName = container.appVersion.name,
                    )
                }
            }
    }
}

data class ChangelogUiState(
    /** The installed build, so the entry that is this one can say so. Blank when unreadable. */
    val currentVersionName: String = "",
    val loading: Boolean = false,
    val releases: List<ReleaseNote> = emptyList(),
    val failure: UpdateFailure? = null,
)
