package io.github.plaza.core

/**
 * The installed build's own version.
 *
 * Read through `PackageManager` rather than `BuildConfig` — see [readAppVersion], which is where
 * that argument and the platform call both live.
 */
data class AppVersion(
    val name: String,
    val code: Long,
)
