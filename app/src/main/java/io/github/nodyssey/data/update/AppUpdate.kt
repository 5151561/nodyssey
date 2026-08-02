package io.github.nodyssey.data.update

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
)

/**
 * Why an update step could not finish.
 *
 * Carries no user-facing text, for the same reason [io.github.nodyssey.core.net.NodeSeekError]
 * does not: the data layer must not decide wording. The About screen maps these onto strings.
 */
sealed interface UpdateFailure {
    /** Transport failure — no connection, timeout, TLS. GitHub being unreachable lands here. */
    data object Network : UpdateFailure

    /** GitHub answered, with a status we cannot use. 403 with a spent rate limit is the likely one. */
    data class Server(val statusCode: Int) : UpdateFailure

    /** A 200 whose body is not the release JSON this understands. */
    data object Unreadable : UpdateFailure

    /** The download could not be written to or renamed inside the cache directory. */
    data object Storage : UpdateFailure
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
 * Why the system installer refused the APK.
 *
 * A user who backed out of the confirmation dialog is not in here: `STATUS_FAILURE_ABORTED` means
 * "no thanks", and showing an error for it would turn their own decision into a fault.
 */
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
)
