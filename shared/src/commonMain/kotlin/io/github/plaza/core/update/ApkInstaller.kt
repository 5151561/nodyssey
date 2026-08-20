package io.github.plaza.core.update

/**
 * The other half of 应用内更新 — handing the downloaded file to whatever installs software here.
 *
 * An interface rather than the Android class it used to be, because [io.github.nodyssey.data.update
 * .AppUpdateRepository] and the 关于 screen are both platform-neutral since steps A7 and D1 while
 * *installing* never can be: Android has `PackageInstaller`, and a platform whose store owns updates
 * has nothing to put here at all.
 *
 * What is deliberately not on it is the permission prompt. "允许安装未知应用" is an Android settings
 * screen reached with an `Intent` and answered with an activity result — there is no neutral shape
 * for that, so the screen asks for it through `rememberInstallPermissionRequest` instead and this
 * interface only answers whether it is needed.
 */
interface ApkInstaller {
    /**
     * Whether this app may ask to install packages at all.
     *
     * False means the user has to grant it first; committing anyway surfaces a settings screen with
     * no explanation of what asked for it.
     */
    fun canInstallPackages(): Boolean

    /**
     * Hands the downloaded file over, and answers whether the request was accepted — not whether the
     * install succeeded. Everything after the hand-off is the platform's own dialog, and its outcome
     * arrives at [io.github.nodyssey.data.update.AppUpdateRepository.onInstallOutcome].
     *
     * A path rather than a file handle: `java.io.File` is the JVM's.
     */
    suspend fun install(apkPath: String): Boolean
}
