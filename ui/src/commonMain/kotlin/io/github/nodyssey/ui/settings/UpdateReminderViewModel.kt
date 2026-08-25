package io.github.nodyssey.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.update.AppUpdateRepository
import io.github.nodyssey.di.AppContainer
import io.github.plaza.core.update.AppRelease
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The launch-time update surface: 启动提醒's dialog, and the dot on 我的.
 *
 * A ViewModel of its own rather than fields on `ProfileViewModel`, because whether a newer APK
 * exists is not a fact about the session — signing out rebuilds the profile state from scratch and
 * must not blink the reminder — and rather than reads on the repository from navigation code,
 * because that was the pattern this repository keeps having to un-grow: a repository call in a
 * navigation lambda is invisible to every ViewModel test and to the layer rule the guard tests pin.
 *
 * The repository stays the SSOT; this holds no copy. Both flows are the repository's own, so the
 * 关于 screen, the dialog and the dot can never disagree about what is available.
 */
class UpdateReminderViewModel(
    private val updates: AppUpdateRepository,
) : ViewModel() {
    /** The release 启动提醒 should be offering, or null when there is nothing to say. */
    val launchReminder: StateFlow<AppRelease?> = updates.launchReminder

    /** Whether a newer release exists at all — what the dot on 我的 marks. */
    val hasUpdate: StateFlow<Boolean> =
        updates.state
            .map { it.available != null }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = updates.state.value.available != null,
            )

    /** 下载并安装 — the reminder is spent, and the 关于 screen takes over the download. */
    fun acceptReminder() = updates.acceptLaunchReminder()

    /** 下次再说. */
    fun postponeReminder() = updates.postponeLaunchReminder()

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    UpdateReminderViewModel(updates = container.appUpdateRepository)
                }
            }
    }
}
