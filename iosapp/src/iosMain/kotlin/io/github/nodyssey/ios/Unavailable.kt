package io.github.nodyssey.ios

import io.github.nodyssey.data.offline.OfflineWorkScheduler
import io.github.nodyssey.data.update.AppUpdateRepository
import io.github.plaza.core.update.ApkInstaller
import io.github.plaza.core.update.AppRelease
import io.github.plaza.core.update.AppUpdateState
import io.github.plaza.core.update.InstallOutcome
import io.github.plaza.core.update.ReleaseNote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/*
 * What this shell declines to do, gathered in one file so the list reads as a list.
 *
 * Every one of these is a member of `AppContainer`: a screen names it, so something has to be there
 * for the graph to assemble at all. They fall into two groups, and the difference matters when
 * reading this as a to-do list, because only one of the two is one.
 *
 *   - **Not a gap.** Sideloading an APK and checking GitHub for a newer build are Android's answer to
 *     a question this platform answers differently: software arrives from the App Store. There is
 *     nothing here to implement later.
 *   - **A gap, and it is named.** Background download scheduling is step D4's `BGTaskScheduler`, and
 *     it is recorded in `docs/kmp-migration-plan.md`.
 *
 * There were four of these when this shell first ran. The image-host upload was the third, and it is
 * gone: step D3c moved the six clients into `commonMain` and gave `HttpTransport` the upload-progress
 * callback that had kept them out. `IosAppContainer` builds the real repository now.
 *
 * Nothing here throws where a button leads to it. A `TODO()` would be a crash waiting for the first
 * person to press one, and what is behind these is *nothing* rather than something broken.
 */

/** There is no sideloading here, and no screen asks: `rememberInstallPermissionRequest` draws nothing. */
internal object NoApkInstaller : ApkInstaller {
    override fun canInstallPackages(): Boolean = false

    override suspend fun install(apkPath: String): Boolean = false
}

/**
 * 应用内更新 as this platform has it: it does not.
 *
 * Not a stub for something later. The Android app checks GitHub Releases because it is distributed as
 * an APK and nothing else would tell a user a new one exists; an iOS build arrives through the App
 * Store or TestFlight, both of which own that job and neither of which an app is allowed to bypass.
 * A permanently idle state is the honest answer, and every screen already draws it — it is the same
 * state the Android app is in for the whole of a launch where nothing newer was published.
 */
internal object NoAppUpdates : AppUpdateRepository {
    override val state: StateFlow<AppUpdateState> = MutableStateFlow(AppUpdateState())

    override val launchReminder: StateFlow<AppRelease?> = MutableStateFlow(null)

    override fun check(force: Boolean) = Unit

    override fun checkOnLaunch() = Unit

    override fun acceptLaunchReminder() = Unit

    override fun postponeLaunchReminder() = Unit

    override fun download() = Unit

    override fun cancelDownload() = Unit

    override suspend fun releaseNotes(force: Boolean): List<ReleaseNote> = emptyList()

    override fun onInstallOutcome(outcome: InstallOutcome) = Unit
}

/**
 * Background downloads are step D4's — `WorkManager` → `BGTaskScheduler`.
 *
 * Declining here does not turn 离线阅读 off: `RoomOfflineLibrary` drains its own queue while the app is
 * running and only asks the scheduler to keep going once it is not. What is missing is the keeping
 * going, which is what D4 is.
 */
internal object NoOfflineWorkScheduler : OfflineWorkScheduler {
    override fun startDrain(wifiOnly: Boolean, restart: Boolean) = Unit

    override fun ensureMaintenance() = Unit
}
