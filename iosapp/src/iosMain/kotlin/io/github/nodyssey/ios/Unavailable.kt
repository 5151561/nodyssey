package io.github.nodyssey.ios

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
 * for the graph to assemble at all. What is left here is not a gap — sideloading an APK and checking
 * GitHub for a newer build are Android's answer to a question this platform answers differently:
 * software arrives from the App Store. There is nothing here to implement later.
 *
 * The list has only shrunk. The image-host upload went in step D3c, which moved the six clients into
 * `commonMain` and gave `HttpTransport` the upload-progress callback that had kept them out; the
 * background download scheduler went in step D4 — see `BgTaskOfflineScheduler`, the `BGTaskScheduler`
 * that WorkManager was mapped to. `IosAppContainer` builds both for real now.
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
