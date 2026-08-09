package io.github.plaza.core

import android.content.Context
import android.os.Build

/**
 * The installed build's own version.
 *
 * Read through `PackageManager` rather than `BuildConfig`: the release build type turns
 * `buildConfig` off (see `app/build.gradle.kts`), and the package manager is the authority anyway —
 * it reports what is installed, which is the number an update has to be compared against.
 */
data class AppVersion(
    val name: String,
    val code: Long,
)

fun readAppVersion(context: Context): AppVersion {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    return AppVersion(
        name = packageInfo.versionName.orEmpty(),
        code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        },
    )
}
