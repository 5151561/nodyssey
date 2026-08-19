package io.github.plaza.core.update

import kotlinx.serialization.Serializable

/**
 * A published release carrying an installable APK.
 *
 * Serializable because the last answer GitHub gave is kept on disk: the badge on 设置 has to be there
 * the moment the app opens, before any network call could have finished.
 */
@Serializable
data class AppRelease(
    /** The version inside the APK — `1.2.0`, not the `v1.2.0` tag. */
    val versionName: String,
    val tag: String,
    /** The release notes as published, which is the CHANGELOG section for that version. */
    val notes: String,
    val downloadUrl: String,
    val assetName: String,
    val sizeBytes: Long,
    /** The release page, for the "打不开就自己去下载" escape hatch. */
    val htmlUrl: String,
    /**
     * True for a GitHub prerelease — a `vX.Y.Z-dev.N` test build.
     *
     * Only ever set when the user turned the dev channel on, since that is the only check that looks
     * past `releases/latest`. Carried so the screens can say so before anyone installs one: a build
     * cut for testing has no CHANGELOG section of its own and no promise that it works.
     */
    val preRelease: Boolean = false,
    /**
     * The APK's SHA-256 as the manifest published it, lowercase hex; blank when it stated none.
     *
     * Checked against the bytes that arrive, which is the one thing a download can get wrong without
     * failing: a truncated or mangled file otherwise reaches the installer and comes back as
     * "解析包时出现问题", a sentence that names neither cause nor cure.
     */
    val sha256: String = "",
)

/**
 * One published release as 更新日志 reads it — the notes, not the download.
 *
 * Separate from [AppRelease] because the two ask different things of a release: an update has to have
 * an installable APK attached and a version newer than this build, while the log wants every release
 * the project published, in order, whatever is or is not attached to it.
 */
data class ReleaseNote(
    /** The version inside the APK — `1.2.0`, not the `v1.2.0` tag. */
    val versionName: String,
    val tag: String,
    /** The release notes as published, which is the CHANGELOG section for that version. */
    val notes: String,
    /** `2026-08-17`, as GitHub dated it, or blank when it said nothing. */
    val publishedOn: String,
    val preRelease: Boolean,
    val htmlUrl: String,
)

/**
 * Why an update step could not finish.
 *
 * Carries no user-facing text, for the same reason [io.github.plaza.core.net.SiteError] does not:
 * the data layer must not decide wording. The About screen maps these onto strings.
 */
sealed interface UpdateFailure {
    /** Transport failure — no connection, timeout, TLS. GitHub being unreachable lands here. */
    data object Network : UpdateFailure

    /** GitHub answered, with a status we cannot use. */
    data class Server(val statusCode: Int) : UpdateFailure

    /** A 200 whose body is not the release JSON this understands. */
    data object Unreadable : UpdateFailure

    /** The download could not be written to or renamed inside the cache directory. */
    data object Storage : UpdateFailure

    /** The bytes that arrived are not the ones the manifest published. Retrying is the answer. */
    data object Checksum : UpdateFailure
}

class AppUpdateException(
    val failure: UpdateFailure,
    cause: Throwable? = null,
) : Exception(failure.toString(), cause)

/** Where the "is there a newer build" question stands. */
sealed interface UpdateCheck {
    /** Nothing asked yet this process. */
    data object Idle : UpdateCheck

    data object Checking : UpdateCheck

    data object UpToDate : UpdateCheck

    data class Available(val release: AppRelease) : UpdateCheck

    data class Failed(val failure: UpdateFailure) : UpdateCheck
}

/** Where the APK for [UpdateCheck.Available] stands. */
sealed interface UpdateDownload {
    data object Idle : UpdateDownload

    data class Running(
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : UpdateDownload {
        /** Null when the response declared no length, which is what an indeterminate bar means. */
        val fraction: Float?
            get() = if (totalBytes > 0L) {
                (downloadedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
            } else {
                null
            }
    }

    /** On disk and complete. [versionName] is carried so a later check can tell it went stale. */
    data class Ready(
        val apkPath: String,
        val versionName: String,
    ) : UpdateDownload

    data class Failed(val failure: UpdateFailure) : UpdateDownload
}

/**
 * How an install session ended, once the user has answered the system's confirmation.
 *
 * The platform reports this as an integer from its own installer; naming the three outcomes here is
 * what keeps the repository — which only decides what the screen says — from having to know which
 * integer meant which. The translation belongs to whatever received the platform's answer.
 */
sealed interface InstallOutcome {
    /** The new build is in, and this process is about to be replaced by it. */
    data object Installed : InstallOutcome

    /**
     * The user backed out of the confirmation dialog.
     *
     * Separate from [Failed] because it is an answer rather than a fault, and showing an error for it
     * would turn their own decision into one.
     */
    data object Abandoned : InstallOutcome

    data class Failed(val failure: InstallFailure) : InstallOutcome
}

/** Why the system installer refused the APK. */
enum class InstallFailure {
    /** Blocked by the device — Play Protect, a device policy, or unknown sources still off. */
    BLOCKED,

    /** Refused against what is installed: a different signing key, or a downgrade. */
    CONFLICT,

    /** The package does not apply to this device. */
    INCOMPATIBLE,

    /** Not enough room. */
    STORAGE,

    /** The APK itself could not be parsed — a truncated or corrupt download. */
    INVALID,

    UNKNOWN,
}

data class AppUpdateState(
    val check: UpdateCheck = UpdateCheck.Idle,
    val download: UpdateDownload = UpdateDownload.Idle,
    val installFailure: InstallFailure? = null,
) {
    val available: AppRelease?
        get() = (check as? UpdateCheck.Available)?.release
}

/**
 * The last answer from GitHub, as stored.
 *
 * [release] is null when that answer was "nothing newer" — which still has to be remembered, or the
 * app would ask again on every launch.
 */
data class UpdateCheckRecord(
    val checkedAtMillis: Long = 0L,
    val release: AppRelease? = null,
    /**
     * Which channel produced this answer.
     *
     * Stored with it because the two channels answer different questions: a "nothing newer" recorded
     * while dev builds were excluded says nothing about whether one exists, so flipping the switch has
     * to invalidate the record rather than wait out the six hours.
     */
    val devChannel: Boolean = false,
)
