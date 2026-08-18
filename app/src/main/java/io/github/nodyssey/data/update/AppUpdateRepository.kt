package io.github.nodyssey.data.update

import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.update.AppRelease
import io.github.plaza.core.update.AppUpdateException
import io.github.plaza.core.update.AppUpdateState
import io.github.plaza.core.update.InstallOutcome
import io.github.plaza.core.update.ReleaseNote
import io.github.plaza.core.update.ReleaseSource
import io.github.plaza.core.update.UpdateCheck
import io.github.plaza.core.update.UpdateCheckRecord
import io.github.plaza.core.update.UpdateDownload
import io.github.plaza.core.update.UpdateFailure
import io.github.plaza.core.update.isNewerVersionName
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

    /**
     * 接收 dev 版更新, as the user left it — read at check time rather than collected.
     *
     * A check is a moment, not a subscription: what matters is the answer when the question is asked,
     * and flipping the switch forces a fresh check of its own.
     */
    suspend fun devChannelEnabled(): Boolean

    /** The version whose launch reminder was answered with 稍后, or null when none was. */
    suspend fun postponedUpdateVersion(): String?

    suspend fun setPostponedUpdateVersion(versionName: String)
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
     * The release a launch check found and the user has not answered yet — the 启动提醒 dialog.
     *
     * Separate from [state] because "a newer build exists" and "ask about it" are different facts with
     * different lifetimes: the dot on 设置 stays for as long as the release is out there, while this is
     * one question, asked at launch, gone as soon as either button is pressed.
     */
    val launchReminder: StateFlow<AppRelease?>

    /**
     * Asks whether a newer release exists.
     *
     * Without [force] the stored answer is reused while it is younger than
     * [DefaultAppUpdateRepository.CHECK_INTERVAL_MILLIS] — that is what keeps the silent check at
     * app start from calling GitHub on every launch. The 检查更新 button forces.
     */
    fun check(force: Boolean = false)

    /**
     * The launch check: [check] with no force, and then a reminder if the answer is one worth raising.
     *
     * Opening 关于 must not raise the dialog, which is why this is a separate entry point rather than a
     * flag on the state: only the caller that runs at app start asks for a reminder.
     */
    fun checkOnLaunch()

    /** 下载并安装 from the reminder: closes it and starts the download. */
    fun acceptLaunchReminder()

    /** 稍后: closes the reminder and remembers the version, so no later launch raises it again. */
    fun postponeLaunchReminder()

    /** Downloads the available release's APK into the cache. No-op when there is nothing to fetch. */
    fun download()

    fun cancelDownload()

    /**
     * 更新日志: every published release, newest first, on whichever channel the user is on.
     *
     * A plain suspend call rather than state on [state]: the log is one screen's content, read when it
     * opens and gone when it closes, and nothing else in the app has a use for it. Throws
     * [AppUpdateException] like every other call here — the screen decides the wording.
     *
     * Cached for [DefaultAppUpdateRepository.CHECK_INTERVAL_MILLIS] like the check is, and for the same
     * reason: the anonymous API allows 60 calls an hour *per address*, so opening this screen four
     * times must cost one call, not four. [force] is the 刷新 button on that screen.
     */
    suspend fun releaseNotes(force: Boolean = false): List<ReleaseNote>

    /**
     * Reports how the install session ended.
     *
     * An [InstallOutcome] rather than the platform's own status integer: which number meant "the
     * user said no" is a fact about `PackageInstaller`, and it is read where the broadcast arrives.
     */
    fun onInstallOutcome(outcome: InstallOutcome)
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

    private val mutableLaunchReminder = MutableStateFlow<AppRelease?>(null)
    override val launchReminder: StateFlow<AppRelease?> = mutableLaunchReminder.asStateFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    /**
     * The last 更新日志 answer, in memory only.
     *
     * Not on disk like the check's record is: that one exists so the red dot is on screen in the first
     * frame after a cold start, while this is only here to stop one session's repeated visits to the
     * screen from spending the hourly quota. A relaunch may fetch again, and that is one call a day.
     */
    private var cachedNotes: CachedNotes? = null

    override fun check(force: Boolean) = runCheck(force = force, reminding = false)

    override fun checkOnLaunch() = runCheck(force = false, reminding = true)

    override fun acceptLaunchReminder() {
        mutableLaunchReminder.value = null
        // 下载并安装 means the download starts here, not on the 关于 screen the dialog sends the user
        // to: that screen binds to the same shared state, so it opens already showing progress.
        download()
    }

    override fun postponeLaunchReminder() {
        val release = mutableLaunchReminder.value ?: return
        mutableLaunchReminder.value = null
        scope.launch { store.setPostponedUpdateVersion(release.versionName) }
    }

    private fun runCheck(force: Boolean, reminding: Boolean) {
        if (checkJob?.isActive == true) return
        checkJob =
            scope.launch {
                val devChannel = store.devChannelEnabled()
                val record = store.updateCheckRecord()
                val age = clock.nowMillis() - record.checkedAtMillis
                // A record that was never written, or one stamped in the future by a clock that has
                // since been corrected, counts as stale rather than as a fresh "nothing new". So is
                // one from the other channel: it answered a different question.
                val fresh =
                    record.checkedAtMillis > 0L &&
                        age in 0 until CHECK_INTERVAL_MILLIS &&
                        record.devChannel == devChannel
                if (!force && fresh) {
                    publish(record.release)
                } else {
                    mutableState.update { it.copy(check = UpdateCheck.Checking) }
                    try {
                        val release = source.latestRelease(includePreRelease = devChannel)
                        store.setUpdateCheckRecord(
                            UpdateCheckRecord(clock.nowMillis(), release, devChannel),
                        )
                        publish(release)
                    } catch (e: AppUpdateException) {
                        mutableState.update { it.copy(check = UpdateCheck.Failed(e.failure)) }
                    }
                }
                // Deliberately after the stored-answer path too: a launch that answered from the record
                // still has to raise the reminder, or the first launch inside the six-hour window would
                // be the only one that ever mentions the release.
                if (reminding) raiseLaunchReminder()
            }
    }

    /**
     * Raises the 启动提醒 unless there is nothing to offer or this version was already met with 稍后.
     *
     * Filtered against the version installed by [publish] before this runs, so a release that is
     * already on the device cannot be announced as new.
     */
    private suspend fun raiseLaunchReminder() {
        val release = mutableState.value.available ?: return
        if (release.versionName == store.postponedUpdateVersion()) return
        mutableLaunchReminder.value = release
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

    override suspend fun releaseNotes(force: Boolean): List<ReleaseNote> {
        val devChannel = store.devChannelEnabled()
        val cached = cachedNotes
        if (!force && cached != null && cached.devChannel == devChannel) {
            val age = clock.nowMillis() - cached.fetchedAtMillis
            if (age in 0 until CHECK_INTERVAL_MILLIS) return cached.notes
        }
        val notes = source.releaseNotes(includePreRelease = devChannel)
        cachedNotes = CachedNotes(clock.nowMillis(), devChannel, notes)
        return notes
    }

    override fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        mutableState.update { it.copy(download = UpdateDownload.Idle) }
    }

    override fun onInstallOutcome(outcome: InstallOutcome) {
        when (outcome) {
            InstallOutcome.Installed -> {
                // The new build is in; this process is about to be replaced. Drop the APK rather than
                // leaving a copy of the previous download sitting in the cache forever.
                scope.launch { withContext(dispatchers.io) { downloadDirectory.deleteRecursively() } }
                mutableState.update {
                    it.copy(download = UpdateDownload.Idle, installFailure = null)
                }
            }

            // Backing out of the system dialog is an answer, not a fault.
            InstallOutcome.Abandoned -> mutableState.update { it.copy(installFailure = null) }

            is InstallOutcome.Failed -> mutableState.update { it.copy(installFailure = outcome.failure) }
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

    /**
     * Whether the APK for [release] is already on disk, whole.
     *
     * The rename out of `.part` is what says "whole": it happens only after the last byte, so a file
     * under this name was complete when it was written. The size, when the release states one, is the
     * second opinion — but the feed does not carry it, so most of the time this is the rename alone.
     */
    private fun File.isComplete(release: AppRelease): Boolean =
        isFile &&
            length() > 0L &&
            (release.sizeBytes <= 0L || length() == release.sizeBytes)

    private data class CachedNotes(
        val fetchedAtMillis: Long,
        val devChannel: Boolean,
        val notes: List<ReleaseNote>,
    )

    companion object {
        /**
         * How long a stored answer counts as current, for both the check and 更新日志.
         *
         * A day. It used to be six hours, on the reasoning that a release published in the morning
         * should be offered the same day — which was written as if the 60-calls-an-hour ceiling were
         * this app's own budget. It is not: GitHub counts anonymous calls **per address**, so every
         * device behind one NAT or one proxy exit shares those sixty, and a phone that has asked
         * nothing all day still meets a 403 because the address was spent by strangers. Under a shared
         * budget the only responsible size is the one that answers the question as rarely as the
         * question deserves — nobody needs a release offered within the hour, and 检查更新 is there
         * for the moment someone does.
         */
        const val CHECK_INTERVAL_MILLIS = 24 * 60 * 60 * 1000L

        private const val PART_SUFFIX = ".part"
    }
}
