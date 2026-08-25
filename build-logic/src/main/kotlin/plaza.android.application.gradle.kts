/*
 * Everything an Android application module in this repository shares with every other one.
 *
 * What stays in the module's own build file is what genuinely differs per app: `namespace`,
 * `applicationId`, the version, and the signing config — the keystore is read from app-specific
 * environment variables and the release build type has to be told about it after the module declares
 * it, which is too late to do from here.
 *
 * The `android` block below mirrors `plaza.android.library` on purpose. See the note there for why
 * the two are not folded into one.
 */
plugins {
    id("com.android.application")
    id("plaza.dependency-locking")
}

android {
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 36
    }

    buildTypes {
        /*
         * A separate applicationId so a debug build and an installed release build can coexist.
         *
         * The two are signed with different keys, and Android refuses to replace an APK with one
         * whose signature differs — the only ways out are uninstalling the release copy first or
         * giving the debug build an id of its own. This is the second.
         *
         * Safe to suffix here because nothing pins the package name: the one manifest authority is
         * written as `${applicationId}`, `buildConfig` is off so no code reads APPLICATION_ID, and
         * AGP derives the instrumentation test id from this value too. The launcher name follows in
         * `src/debug/res/values/strings.xml`, so the two are also told apart on the home screen.
         */
        debug {
            applicationIdSuffix = ".debug"
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }

        /*
         * The release build's R8 and resource shrinking under a key CI owns.
         *
         * This is the variant the `:smoke` test module instruments: `release` itself cannot be — CI
         * has no release keystore and an unsigned APK does not install — and `debug` proves nothing
         * about minification, which is where serialization and reflection break at runtime rather
         * than at build time. Debug-signed, so the emulator installs it; everything else is the
         * release configuration, inherited rather than restated so the two cannot drift apart.
         *
         * No `applicationIdSuffix`: the debug suffix exists so a debug install can sit beside a
         * release one on a person's phone, and nothing installs this build on a person's phone.
         */
        create("minified") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            // Room and Compose tests run on Robolectric so CI needs no emulator.
            isIncludeAndroidResources = true
        }
    }

    lint {
        // Stated rather than left to AGP's default search: relying on an implicit walk up to the root
        // for `lint.xml` is the kind of thing that silently stops applying.
        lintConfig = rootProject.file("lint.xml")

        // A lint regression must fail the build, not scroll past in a log.
        warningsAsErrors = true
        abortOnError = true

        // One `lintDebug` on the application covers every module it depends on, which keeps CI to a
        // single lint invocation and a single report directory.
        checkDependencies = true

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
