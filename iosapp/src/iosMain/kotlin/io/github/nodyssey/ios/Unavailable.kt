package io.github.nodyssey.ios

import io.github.nodyssey.data.imagehost.HostedImage
import io.github.nodyssey.data.imagehost.ImageHostConfig
import io.github.nodyssey.data.imagehost.ImageHostError
import io.github.nodyssey.data.imagehost.ImageHostException
import io.github.nodyssey.data.imagehost.ImageHostRepository
import io.github.nodyssey.data.imagehost.ImageHostSettings
import io.github.nodyssey.data.imagehost.ImageHostUpload
import io.github.nodyssey.data.offline.OfflineWorkScheduler
import io.github.nodyssey.data.update.AppUpdateRepository
import io.github.plaza.core.update.ApkInstaller
import io.github.plaza.core.update.AppRelease
import io.github.plaza.core.update.AppUpdateState
import io.github.plaza.core.update.InstallOutcome
import io.github.plaza.core.update.ReleaseNote
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest

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
 *   - **A gap, and it is named.** Uploading to an image host is behind six clients in `:app` written
 *     against `OkHttpClient` rather than `HttpTransport`; background download scheduling is step D4.
 *     Both are recorded in `docs/kmp-migration-plan.md`.
 *
 * Nothing here throws where a button leads to it. A `TODO()` would be a crash waiting for the first
 * person to press one, and what is behind these is *nothing* rather than something broken — the one
 * exception is the upload, which raises the error its own interface already defines for "this host
 * cannot do that", because the tray words that and a silent no-op would leave a row spinning.
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

/**
 * 图床设置 in full; the upload itself declines.
 *
 * The split is where the code is rather than where the feature is. Everything about *which* host and
 * *what credential* is `DataStoreImageHostSettings` in `commonMain` — so the settings screen, the
 * credential in the Keychain, the switching between six providers all work here exactly as they do on
 * Android. What does not is the six clients that speak those six protocols: they live in `:app` and
 * are written against `OkHttpClient`, including the progress callback the tray's ring reads, which
 * `HttpTransport` has no shape for. Porting them is a step, not a footnote.
 *
 * [ImageHostError.Unsupported] is the error the interface already defines for "this host has no
 * endpoint for that", and the tray words it. It is not exactly what is true — the host is fine, this
 * build is not — but it is the one of nine that leads to the right reaction, which is to stop rather
 * than to check the token or try again.
 */
internal class UnavailableImageHostRepository(
    private val settings: ImageHostSettings,
) : ImageHostRepository,
    ImageHostSettings by settings {
    // The same expression `DefaultImageHostRepository` uses: which host is selected and what its
    // fields are is settings, and settings are the half that works here.
    @OptIn(ExperimentalCoroutinesApi::class)
    override val current: Flow<ImageHostConfig> =
        settings.selected.flatMapLatest { provider -> settings.config(provider) }

    override suspend fun upload(
        upload: ImageHostUpload,
        onProgress: (Float) -> Unit,
    ): HostedImage = throw ImageHostException(ImageHostError.Unsupported)

    override suspend fun images(): List<HostedImage> = throw ImageHostException(ImageHostError.Unsupported)

    override suspend fun delete(image: HostedImage) = throw ImageHostException(ImageHostError.Unsupported)
}
