package io.github.nodyssey.data.update

import android.content.pm.PackageInstaller
import io.github.nodyssey.core.AppClock
import io.github.nodyssey.core.AppDispatchers
import io.github.nodyssey.core.update.isNewerVersionName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Where the stored answer from the last check lives; [io.github.nodyssey.data.settings.SettingsRepository] owns it. */
interface UpdateCheckStore {
    suspend fun updateCheckRecord(): UpdateCheckRecord

    suspend fun setUpdateCheckRecord(record: UpdateCheckRecord)
}

/**
 * 应用内更新 — the whole of it: ask GitHub, hold the answer, fetch the APK.
 *
 * One instance per process, held by the container rather than by a ViewModel, because a download has
 * to survive leaving the 关于 screen and a badge has to be readable from 设置 and 我的 at the same
 * time. Handing the APK to the system installer is [ApkInstaller]'s job; this only reports what came
 * back from it.
 */
interface AppUpdateRepository {
    val state: StateFlow<AppUpdateState>

    /**
     * Asks whether a newer release exists.
     *
     * Without [force] the stored answer is reused while it is younger than
     * [DefaultAppUpdateRepository.CHECK_INTERVAL_MILLIS] — that is what keeps the silent check at
     * app start from calling GitHub on every launch. The 检查更新 button forces.
     */
    fun check(force: Boolean = false)

    /** Downloads the available release's APK into the cache. No-op when there is nothing to fetch. */
    fun download()

    fun cancelDownload()

    /** Reports a `PackageInstaller.STATUS_*` back from [ApkInstallResultReceiver]. */
    fun onInstallStatus(status: Int)
}

class DefaultAppUpdateRepository(
    private val source: ReleaseSource,
    private val store: UpdateCheckStore,
    private val clock: AppClock,
    private val dispatchers: AppDispatchers,
    private val scope: CoroutineScope,
    private val currentVersionName: String,
    private val downloadDirectory: File,
) : AppUpdateRepository {
    private val mutableState = MutableStateFlow(AppUpdateState())
    override val state: StateFlow<AppUpdateState> = mutableState.asStateFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    override fun check(force: Boolean) {
        if (checkJob?.isActive == true) return
        checkJob =
            scope.launch {
                val record = store.updateCheckRecord()
                val age = clock.nowMillis() - record.checkedAtMillis
                // A record that was never written, or one stamped in the future by a clock that has
                // since been corrected, counts as stale rather than as a fresh "nothing new".
                val fresh = record.checkedAtMillis > 0L && age in 0 until CHECK_INTERVAL_MILLIS
                if (!force && fresh) {
                    publish(record.release)
                    return@launch
                }

                mutableState.update { it.copy(check = UpdateCheck.Checking) }
                try {
                    val release = source.latestRelease()
                    store.setUpdateCheckRecord(UpdateCheckRecord(clock.nowMillis(), release))
                    publish(release)
                } catch (e: AppUpdateException) {
                    mutableState.update { it.copy(check = UpdateCheck.Failed(e.failure)) }
                }
            }
    }

    override fun download() {
        val release = mutableState.value.available ?: return
        if (downloadJob?.isActive == true) return
        downloadJob =
            scope.launch {
                mutableState.update {
                    it.copy(
                        download = UpdateDownload.Running(0L, release.sizeBytes),
                        installFailure = null,
                    )
                }
                try {
                    val apk = withContext(dispatchers.io) { prepareTarget(release) }
                    if (!apk.isComplete(release)) {
                        val partial = File(apk.parentFile, apk.name + PART_SUFFIX)
                        source.download(release, partial) { downloaded, total ->
                            mutableState.update {
                                it.copy(download = UpdateDownload.Running(downloaded, total))
                            }
                        }
                        // Renamed only once the last byte is written, so a download cut off halfway
                        // can never be mistaken for an installable APK on the next attempt.
                        val renamed = withContext(dispatchers.io) { partial.renameTo(apk) }
                        if (!renamed) throw AppUpdateException(UpdateFailure.Storage)
                    }
                    mutableState.update {
                        it.copy(
                            download = UpdateDownload.Ready(apk.absolutePath, release.versionName),
                        )
                    }
                } catch (e: AppUpdateException) {
                    mutableState.update { it.copy(download = UpdateDownload.Failed(e.failure)) }
                }
            }
    }

    override fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        mutableState.update { it.copy(download = UpdateDownload.Idle) }
    }

    override fun onInstallStatus(status: Int) {
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                // The new build is in; this process is about to be replaced. Drop the APK rather than
                // leaving a copy of the previous download sitting in the cache forever.
                scope.launch { withContext(dispatchers.io) { downloadDirectory.deleteRecursively() } }
                mutableState.update {
                    it.copy(download = UpdateDownload.Idle, installFailure = null)
                }
            }

            // Backing out of the system dialog is an answer, not a fault.
            PackageInstaller.STATUS_FAILURE_ABORTED ->
                mutableState.update { it.copy(installFailure = null) }

            else -> mutableState.update { it.copy(installFailure = installFailureOf(status)) }
        }
    }

    /**
     * Applies a check's answer, filtering it against the version actually installed.
     *
     * The filter runs here rather than at the call site because a stored answer outlives the build
     * that stored it: after this very feature installs 1.2.0, the record still says "1.2.0 is out",
     * and it has to read as 已是最新 rather than offering the update again.
     */
    private fun publish(release: AppRelease?) {
        val newer = release?.takeIf { isNewerVersionName(it.versionName, currentVersionName) }
        mutableState.update { current ->
            val sameRelease = current.available?.versionName == newer?.versionName
            if (!sameRelease) downloadJob?.cancel()
            current.copy(
                check = if (newer == null) UpdateCheck.UpToDate else UpdateCheck.Available(newer),
                // A download only ever belongs to one release; a different answer retires it.
                download = if (sameRelease) current.download else UpdateDownload.Idle,
            )
        }
    }

    /** Makes the directory, clears anything left from another version, and names this release's APK. */
    private fun prepareTarget(release: AppRelease): File {
        downloadDirectory.mkdirs()
        val name = release.assetName.takeIf { it.endsWith(".apk", ignoreCase = true) }
            ?: "nodyssey-${release.versionName}.apk"
        downloadDirectory
            .listFiles()
            ?.filter { it.name != name && it.name != name + PART_SUFFIX }
            ?.forEach { it.delete() }
        return File(downloadDirectory, name)
    }

    private fun File.isComplete(release: AppRelease): Boolean =
        release.sizeBytes > 0L && isFile && length() == release.sizeBytes

    companion object {
        /**
         * How long a stored answer counts as current.
         *
         * Six hours: often enough that a release published in the morning is offered the same day,
         * rare enough that the app is nowhere near GitHub's 60-calls-an-hour ceiling even if someone
         * relaunches it all day.
         */
        const val CHECK_INTERVAL_MILLIS = 6 * 60 * 60 * 1000L

        private const val PART_SUFFIX = ".part"

        private fun installFailureOf(status: Int): InstallFailure =
            when (status) {
                PackageInstaller.STATUS_FAILURE_BLOCKED -> InstallFailure.BLOCKED
                PackageInstaller.STATUS_FAILURE_CONFLICT -> InstallFailure.CONFLICT
                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> InstallFailure.INCOMPATIBLE
                PackageInstaller.STATUS_FAILURE_STORAGE -> InstallFailure.STORAGE
                PackageInstaller.STATUS_FAILURE_INVALID -> InstallFailure.INVALID
                else -> InstallFailure.UNKNOWN
            }
    }
}
