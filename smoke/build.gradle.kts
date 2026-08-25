/*
 * The R8 smoke — see the note at `include(":smoke")` in `settings.gradle.kts` for why this is a
 * test module and why its tests are UIAutomator rather than Compose rules.
 *
 * `com.android.test` puts the test sources in `src/main`: the module *is* its tests, the way a
 * Macrobenchmark module is. It declares a `minified` build type only so variant matching pairs it
 * with `:app`'s build type of the same name; the test APK itself is never minified.
 */
plugins {
    alias(libs.plugins.android.test)
    id("plaza.dependency-locking")
}

android {
    namespace = "io.github.nodyssey.smoke"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    // The test instruments *itself* and reaches the app only through UIAutomator, the same
    // declaration a Macrobenchmark module makes. Without it AGP assumes the test shares the app's
    // classpath and demands this APK be shrunk with matching rules — the coupling this module's
    // whole design avoids.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("minified") {
            // The name is what matters — it pairs this module with `:app`'s build type of the same
            // name. The debug signature is only so the emulator will install the test APK; nothing
            // in this module is minified.
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

// The debug variant would instrument `:app`'s debug build, which `:app`'s own androidTest already
// covers with far richer journeys. Only the minified variant earns this module its keep.
androidComponents {
    beforeVariants { variant ->
        variant.enable = variant.buildType == "minified"
    }
}

dependencies {
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}
