package io.github.nodyssey.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.update.AppUpdateRepository
import io.github.nodyssey.di.AppContainer
import io.github.plaza.core.AppVersion
import io.github.plaza.core.crash.CrashReport
import io.github.plaza.core.crash.CrashReportStore
import io.github.plaza.core.update.ApkInstaller
import io.github.plaza.core.update.AppUpdateState
import io.github.plaza.core.update.InstallFailure
import io.github.plaza.core.update.InstallOutcome
import io.github.plaza.core.update.UpdateDownload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AboutAppViewModel(
    private val updates: AppUpdateRepository,
    private val installer: ApkInstaller,
    private val appVersion: AppVersion,
    private val crashes: CrashReportStore,
) : ViewModel() {
    /**
     * "允许安装未知应用" is off for this app.
     *
     * Kept here rather than in the repository because it is not a fact about the update — it is a
     * fact about this device that stops being true the moment the user comes back from Settings.
     */
    private val needsInstallPermission = MutableStateFlow(false)

    /** Read once when the screen opens; a crash cannot happen while the user is looking at 关于. */
    private val crashReport = MutableStateFlow<CrashReport?>(null)

    val uiState: StateFlow<AboutAppUiState> =
        combine(updates.state, needsInstallPermission, crashReport) { update, blocked, crash ->
            AboutAppUiState(
                appName = appVersion.label,
                versionName = appVersion.name.ifBlank { "—" },
                versionCode = appVersion.code,
                update = update,
                needsInstallPermission = blocked,
                crashReport = crash,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue =
            AboutAppUiState(
                appName = appVersion.label,
                versionName = appVersion.name.ifBlank { "—" },
                versionCode = appVersion.code,
            ),
        )

    init {
        // Opening 关于 is not a manual check: this is answered from the stored result unless it has
        // gone stale, so the screen states something true without asking GitHub every time it opens.
        updates.check()
        viewModelScope.launch { crashReport.value = crashes.latest() }
    }

    /** Forgets the stored crash; the 导出 rows disappear with it. */
    fun clearCrashReport() {
        viewModelScope.launch {
            crashes.clear()
            crashReport.value = null
        }
    }

    fun checkForUpdates() = updates.check(force = true)

    fun download() = updates.download()

    fun cancelDownload() = updates.cancelDownload()

    /**
     * Hands the downloaded APK over, or reports that the permission has to come first.
     *
     * Asking [ApkInstaller.canInstallPackages] rather than committing and letting the system raise
     * it: the settings screen it would open on its own arrives with no explanation of what asked for
     * it, and the 关于 screen can say why before sending anyone there.
     *
     * Where that screen *is* has no neutral shape, so it is not here — see
     * `rememberInstallPermissionRequest`, which the route wires to [onInstallPermissionResult].
     */
    fun install() {
        val ready = updates.state.value.download as? UpdateDownload.Ready ?: return
        if (!installer.canInstallPackages()) {
            needsInstallPermission.value = true
            return
        }
        needsInstallPermission.value = false
        viewModelScope.launch {
            val committed = installer.install(ready.apkPath)
            // Nothing will arrive at the receiver if the session never got written, so an unnamed
            // failure stands in for it and the screen still says something.
            if (!committed) updates.onInstallOutcome(InstallOutcome.Failed(InstallFailure.UNKNOWN))
        }
    }

    /** Called when the user returns from that settings screen, whatever they did there. */
    fun onInstallPermissionResult() {
        needsInstallPermission.value = false
        install()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    AboutAppViewModel(
                        updates = container.appUpdateRepository,
                        installer = container.apkInstaller,
                        appVersion = container.appVersion,
                        crashes = container.crashReportStore,
                    )
                }
            }
    }
}

data class AboutAppUiState(
    /** What this build calls itself — see [AppVersion.label] for why it is not a string resource. */
    val appName: String = "Nodyssey",
    val versionName: String = "—",
    val versionCode: Long = 0L,
    val update: AppUpdateState = AppUpdateState(),
    val needsInstallPermission: Boolean = false,
    /** The last crash, when one is on record — see [CrashReportStore]. Null hides the 导出 rows. */
    val crashReport: CrashReport? = null,
)
