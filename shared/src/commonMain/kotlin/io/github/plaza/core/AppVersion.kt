package io.github.plaza.core

/**
 * The installed build's own version, and what it calls itself.
 *
 * Read through `PackageManager` rather than `BuildConfig` — see [readAppVersion], which is where
 * that argument and the platform call both live.
 */
data class AppVersion(
    val name: String,
    val code: Long,
    /**
     * The app's own displayed name — "Nodyssey", or "Nodyssey·D" for a debug build.
     *
     * Here rather than in the string resources because the debug suffix is a *build* fact: on
     * Android it is a build-type override of `app_name`, which the launcher and the task switcher
     * read. Since step D1 the 关于 screen's copy of that name is a Compose Resource, and those have
     * no build types — so the screen asks the platform what this build is called instead of holding
     * a second answer that would have quietly stopped agreeing.
     */
    val label: String = "",
)
