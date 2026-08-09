/*
 * Everything an Android library module in this repository shares with every other one.
 *
 * The application module repeats the same values through `plaza.android.application` rather than
 * applying this plugin, because `com.android.library` and `com.android.application` contribute
 * different, unrelated `android` extension types. The duplication is a handful of literals; sharing
 * them would mean reaching for `CommonExtension`, whose generic arity moves between AGP releases.
 */
plugins {
    id("com.android.library")
    id("plaza.dependency-locking")
}

android {
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        aidl = false
        buildConfig = false
        shaders = false
    }

    testOptions {
        unitTests {
            // Room and Compose tests run on Robolectric so CI needs no emulator.
            isIncludeAndroidResources = true
        }
    }

    lint {
        // Stated rather than left to AGP's default search: a module directory has no `lint.xml` of
        // its own, and relying on an implicit walk up to the root is the kind of thing that silently
        // stops applying.
        lintConfig = rootProject.file("lint.xml")

        // A lint regression must fail the build, not scroll past in a log.
        warningsAsErrors = true
        abortOnError = true
        sarifReport = true

        // Only checks that fire on the calendar rather than on a commit are disabled. Anything a
        // change to this repository can cause stays enabled, including `warningsAsErrors`.
        disable +=
            setOf(
                // Dependencies are bumped deliberately via the version catalog, and a new upstream
                // release would otherwise break CI on a commit that changed nothing.
                "GradleDependency",
                "NewerVersionAvailable",
                "AndroidGradlePluginVersion",
                // Same problem: this fires the day Google ships an SDK newer than targetSdk. Raising
                // targetSdk is a behavioural change that needs testing, not a lint autofix.
                "OldTargetApi",
            )
    }
}

kotlin {
    jvmToolchain(21)
}
