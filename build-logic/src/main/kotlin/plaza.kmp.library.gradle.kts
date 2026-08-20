// Everything a Kotlin Multiplatform library module in this repository shares.
//
// The sibling `plaza.android.library` stays as it is: a module that only ever runs on Android has no
// use for source-set hierarchies or a Native toolchain, and paying for them would make every such
// module slower to configure for nothing. This plugin is for the modules whose contents are supposed
// to compile without an Android or a JVM under them.
//
// Targets other than Android belong to the module rather than here: which ones a module declares is
// a decision about that module — `:shared` answers to Paging's missing `macosX64` artifact, and
// `:designsys` has a desktop target because a JVM is the cheapest place to prove a Compose module
// left Android. What every KMP module in this repository shares is the Android target below and the
// gates around it.
//
// The Android side here is a *consumer*, not a platform this code is written against: `:app` is an
// Android module and resolves these modules through their Android variant. That is why the
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

        // `commonTest` has to execute somewhere CI can reach. A module's other targets may need a
        // toolchain the Linux runner does not have — a Mac for Apple, a desktop JVM nobody runs on
        // CI — so without a host test compilation the common tests would exist and never run there:
        // the shape of failure where a suite is green because it is empty.
        withHostTest {
            // Robolectric reads the merged manifest and resources through the properties file this
            // switch generates, and a Compose test needs it: `createComposeRule` launches a
            // `ComponentActivity` that only the merged test manifest declares. The same line is in
            // `plaza.android.library` as `testOptions.unitTests.isIncludeAndroidResources`; a KMP
            // module spells it here because it has no `testOptions`.
            isIncludeAndroidResources = true
        }

        lint {
            // Stated rather than left to AGP's default search, the same as in `plaza.android.library`:
            // relying on an implicit walk up to the root is the kind of thing that silently stops
            // applying.
            lintConfig = rootProject.file("lint.xml")

            warningsAsErrors = true
            abortOnError = true

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
}
