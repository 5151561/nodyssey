// Everything a Kotlin Multiplatform library module in this repository shares.
//
// The sibling `plaza.android.library` stays as it is: a module that only ever runs on Android has no
// use for source-set hierarchies or a Native toolchain, and paying for them would make every such
// module slower to configure for nothing. This plugin is for the modules whose contents are supposed
// to compile without an Android or a JVM under them.
//
// The Android side here is a *consumer*, not a platform this code is written against: `:app` and
// `:core` are Android modules and resolve this module through its Android variant. That is why the
// target arrives via `com.android.kotlin.multiplatform.library` — the Android target inside a KMP
// module — rather than by applying `com.android.library` next to it, which AGP 9 no longer supports
// in combination with the multiplatform plugin.
//
// Line comments rather than a block comment on purpose, the same as in `build-logic/build.gradle.kts`:
// Kotlin nests block comments, so a stray `/*` inside prose silently swallows the rest of the file,
// and the only symptom is a plugin reported as not found.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    // Not redundant beside the plugin above, and not optional. AGP's `KmpTaskManager` guards the whole
    // block that registers the main component's lint tasks behind `plugins.hasPlugin("com.android.lint")`
    // — without it a KMP module gets `lintAnalyzeAndroidHostTest` and nothing for `commonMain`, so
    // `warningsAsErrors` below would be guarding an empty room and `:app:lintDebug` would report a
    // clean repository while never having read a line of this module.
    id("com.android.lint")
    id("plaza.dependency-locking")
}

kotlin {
    jvmToolchain(21)

    android {
        // The same two numbers `plaza.android.library` states, and for the same reason: a module
        // resolving a different compileSdk than the application it ships inside is a class of bug
        // that only shows up at runtime.
        compileSdk = 37
        minSdk = 26

        // `commonTest` has to execute somewhere CI can reach. The Apple targets only build on a Mac,
        // so without a host test compilation the common tests would exist and never run on the Linux
        // runner — the shape of failure where a suite is green because it is empty.
        withHostTest {}

        lint {
            // Stated rather than left to AGP's default search, the same as in `plaza.android.library`:
            // relying on an implicit walk up to the root is the kind of thing that silently stops
            // applying.
            lintConfig = rootProject.file("lint.xml")

            warningsAsErrors = true
            abortOnError = true
            sarifReport = true

            // The same calendar-driven checks the Android library plugin disables; anything a change
            // to this repository can cause stays enabled.
            disable +=
                setOf(
                    "GradleDependency",
                    "NewerVersionAvailable",
                    "AndroidGradlePluginVersion",
                    "OldTargetApi",
                )
        }
    }

    // Apple Silicon only, which is a constraint inherited from Paging: `paging-common` 3.5.0 ships no
    // `macosX64` artifact. Supporting an Intel Mac would mean answering what the feed list is built on
    // instead, so the decision is recorded here rather than discovered later.
    //
    // Neither target builds on the Linux runner CI uses, and `kotlin.native.ignoreDisabledTargets` in
    // `gradle.properties` is what keeps that a skip rather than a failure. The consequence is stated
    // there: only a Mac runs the Native compilation, so `macosArm64Test` is a local gate.
    iosArm64()
    macosArm64()
}
