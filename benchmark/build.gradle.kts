/*
 * The performance harness — see the note at `include(":benchmark")` in `settings.gradle.kts`.
 *
 * Two things live here, both driving `:app`'s `minified` build from outside its process:
 * a baseline-profile generator whose output is committed as `app/src/main/baseline-prof.txt`,
 * and a cold-start Macrobenchmark that measures what that profile buys. See README.md in this
 * module for the run commands; nothing here is wired into CI.
 */
plugins {
    alias(libs.plugins.android.test)
    id("plaza.dependency-locking")
}

android {
    namespace = "io.github.nodyssey.benchmark"
    compileSdk = 37

    defaultConfig {
        // Not `:app`'s 26: `BaselineProfileRule` needs API 28+ to capture at all, and an unrooted
        // device or emulator has to be 33+ before the shell may read another process's profile.
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The emulator is the only device this project benchmarks on, and the macro library
        // refuses it by default because emulator timings are not phone timings. For the profile
        // generator timing does not matter at all (the output is *which methods ran*), and for the
        // startup benchmark the numbers are read as relative — profile against no profile on the
        // same emulator — never as absolute truth.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    targetProjectPath = ":app"

    // Same declaration as `:smoke`, and it is the Macrobenchmark module shape both copied: the test
    // instruments itself and reaches the app only through UIAutomator, so the app under test stays
    // exactly as shipped — no shared classpath, no keep rules.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("minified") {
            // The name pairs this module with `:app`'s R8 build type; the debug signature is only
            // so the emulator will install the test APK.
            signingConfig = signingConfigs.getByName("debug")
        }
        create("nonMinified") {
            // Pairs with `:app`'s un-renamed release twin — the one the profile generator captures
            // from, because a profile of R8's per-build names is a profile of nothing. See the
            // build type's note in `plaza.android.application`.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

// The debug variant would measure a debuggable build, which measures the debugger, not the app.
// `minified` is what the startup benchmark times (it is what ships); `nonMinified` is what the
// profile generator captures from.
androidComponents {
    beforeVariants { variant ->
        variant.enable = variant.buildType != "debug"
    }
}

dependencies {
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
