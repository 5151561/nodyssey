package io.github.nodyssey.data.update

import io.github.plaza.core.update.AppRelease
import io.github.plaza.core.update.AppUpdateException
import io.github.plaza.core.update.AppUpdateState
import io.github.plaza.core.update.InstallOutcome
import io.github.plaza.core.update.ReleaseNote
import io.github.plaza.core.update.UpdateCheckRecord
import kotlinx.coroutines.flow.StateFlow

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
