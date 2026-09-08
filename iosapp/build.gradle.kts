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
    //
    // Two arches, not three: there is no `iosX64()`, so an Intel simulator slice does not exist. That
    // is a decision rather than an omission — this project is Apple-silicon-only — but Xcode does not
    // know it, and a plain `xcodebuild -sdk iphonesimulator` asks for a universal `arm64 + x86_64`
    // binary and fails on the half that was never built. The answer lives in the Xcode project as
    // `EXCLUDED_ARCHS[sdk=iphonesimulator*] = x86_64`, in both configurations; it is recorded here too
    // because Xcode rewrites `project.pbxproj` freely and this file is where the reason is.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            // What the Xcode project imports. Named for what it is rather than for the app: the app
            // is the Xcode target, and this is the Kotlin half it embeds.
            baseName = "NodysseyShell"

            // Static, which is what the CMP template ships and what avoids a second signing step for
            // an embedded dynamic framework. The cost is link time, paid once per build.
            isStatic = true

            /*
             * Optimize the generated code for size rather than speed. This is a trade, not a free
             * win — see the numbers and the cost below before removing or keeping it.
             *
             * The reason it is the only knob worth having here is what the link map says the binary
             * is made of. Asking the linker for a map (`LD_GENERATE_MAP_FILE=YES`) and adding up
             * the symbols by the object they came from, the 59.5 MB Release binary is:
             *
             *   45.4 MB  72%  this framework's own object — Kotlin, Compose, the K/N runtime
             *    7.0 MB  11%  ICU, of which 6.0 MB is `libicu.icudtl_dat.o`
             *    4.7 MB   8%  Skia proper, plus its Metal and GL backends
             *    1.8 MB   3%  the image codecs — png, jpeg, webp, gif, dng
             *    1.5 MB   2%  SQLite, which Room brings
             *    1.2 MB   2%  HarfBuzz
             *    0.1 MB   0%  expat, which the SVG renderer parses with
             *
             * So the C++ that gets blamed for a Compose binary is 8 MB of it, and the ICU blob is a
             * single object file a linker cannot split. Everything else is Kotlin, which is what
             * this option acts on: `smallBinary` sets LLVM's size level to AGGRESSIVE (`-Oz`, from
             * a baseline of none) and turns off `inlineForPerformance`, Kotlin's own pre-codegen
             * inlining pass.
             *
             * Measured on this machine, same commit, only this line differing:
             *
             *   binary     62,369,024 B → 53,063,360 B   (−14.9%)
             *   .ipa       download −2.50 MB — the CI build's 20.89 MB becomes about 18.4
             *   installed  59.5 MB → 50.6 MB
             *
             * The cost is runtime speed, and it is not measured. `-Oz` and no inlining is exactly
             * the trade its name implies, and Compose's hot paths — recomposition, layout, scroll —
             * are the code it applies to. Nothing here says the trade is bad; it says it was made
             * deliberately and can be unmade by deleting this line.
             *
             * Only Release is affected: the compiler ignores this for a debug binary and says so,
             * so a local debug build compiles the way it always did. The Release simulator build
             * with this on was installed and launched, and drew its first screen.
             */
            binaryOption("smallBinary", "true")
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
