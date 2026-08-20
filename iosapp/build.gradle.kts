// The iOS shell: what `:app` is for Android, on the other platform.
//
// Not `plaza.kmp.library` — that plugin's whole point is the Android target every *library* module
// here shares, and this module is the one that must not have one. It is also not a library: nothing
// depends on it, it depends on everything, and what it produces is a framework an Xcode project
// links rather than a klib another module resolves.

plugins {
    // Not `alias(libs.plugins...)`: `build-logic` already puts the Kotlin Gradle plugin on the root
    // build's classpath, and asking for it again by version fails as a duplicate request. Same line,
    // same reason, as `gallery/build.gradle.kts`.
    id("org.jetbrains.kotlin.multiplatform")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    id("plaza.dependency-locking")
}

kotlin {
    jvmToolchain(21)

    // Both arches, and both are load-bearing: the simulator is what step D3b runs on this machine,
    // and the device arch is the one an installable build needs. A framework is per-architecture, so
    // leaving either out is not a smaller build — it is a destination Xcode cannot select.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            // What the Xcode project imports. Named for what it is rather than for the app: the app
            // is the Xcode target, and this is the Kotlin half it embeds.
            baseName = "NodysseyShell"

            // Static, which is what the CMP template ships and what avoids a second signing step for
            // an embedded dynamic framework. The cost is link time, paid once per build.
            isStatic = true
        }
    }

    sourceSets {
        iosMain.dependencies {
            // The screens, and through them `:designsys`, `:shared` and Compose Resources — the same
            // three faces `:app` reads.
            implementation(project(":ui"))

            // `ComposeUIViewController`, the one thing this module needs that a screen does not: the
            // seam between UIKit's world and the composition.
            implementation(compose.ui)

            // Coil, and the reason this module names it at all: on this platform the core library
            // ships no network fetcher, so an app that does not install one draws every remote image
            // as a failure. `:app` answers by handing Coil its `OkHttpClient`; the answer here is a
            // `NetworkClient` over `NSURLSession` — see `IosImageLoader.kt`. `coil-network-core` is
            // the interface half of that, with no engine of its own.
            implementation(libs.coil.core)
            implementation(libs.coil.network.core)

            // The same two `:app` names, and each for a reason that is visible on screen rather
            // than theoretical — see `IosImageLoader.kt`.
            implementation(libs.coil.network.cache.control)
            implementation(libs.coil.svg)
        }
    }
}
