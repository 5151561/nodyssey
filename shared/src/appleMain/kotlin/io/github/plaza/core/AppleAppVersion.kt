package io.github.plaza.core

import platform.Foundation.NSBundle

/**
 * This build's own version, out of the `Info.plist` Xcode wrote.
 *
 * The three keys are the platform's spelling of the three things `AndroidAppVersion.kt` reads from a
 * `PackageInfo`: the marketing version is `versionName`, the build number is `versionCode`, and the
 * bundle's display name is what the home screen shows — the same source the Android side uses for
 * [AppVersion.label], and for the same reason. A debug build that renames itself renames the label
 * with it, and the 关于 screen has one answer rather than two.
 */
fun readAppVersion(): AppVersion {
    val bundle = NSBundle.mainBundle
    fun string(key: String): String? = (bundle.objectForInfoDictionaryKey(key) as? String)?.takeIf { it.isNotBlank() }

    return AppVersion(
        name = string("CFBundleShortVersionString") ?: "0",
        // A string in the plist, a number here — Xcode writes it as text and Android counts with it.
        code = string("CFBundleVersion")?.toLongOrNull() ?: 0L,
        label = string("CFBundleDisplayName") ?: string("CFBundleName") ?: "",
    )
}
