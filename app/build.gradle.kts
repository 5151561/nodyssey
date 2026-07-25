plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

/*
 * Pins transitive dependency versions in `gradle.lockfile`.
 *
 * The version catalog already pins what this project asks for directly; locking pins what those
 * dependencies in turn drag in. Without it, this commit built today and the same commit built in six
 * months can resolve different transitive versions, and a CI failure nobody can reproduce locally is
 * the usual way that gets discovered.
 *
 * After changing any dependency, regenerate with:
 *
 *     ./gradlew resolveAndLockAll --write-locks
 *
 * and commit `app/gradle.lockfile`. Until then the build fails rather than resolving something else.
 */
val lockedConfigurations =
    setOf(
        // What ships.
        "debugCompileClasspath",
        "debugRuntimeClasspath",
        "releaseCompileClasspath",
        "releaseRuntimeClasspath",
        // What the tests run against, since a test-only dependency drifting is just as confusing.
        "debugUnitTestCompileClasspath",
        "debugUnitTestRuntimeClasspath",
    )

dependencyLocking {
    // STRICT so that a dependency with *no* lock state fails too, not just one whose version moved.
    // The default mode happily resolves an unlocked module — checked by adding one and watching the
    // build pass — which would have made this gate far weaker than it looks.
    lockMode.set(LockMode.STRICT)
}

// Named configurations rather than `lockAllConfigurations()`: AGP registers internal configurations
// such as `androidApis` that are lockable but never produce lock state, and under STRICT every one of
// those fails the build. These six are the ones whose contents actually determine what ships and what
// the tests execute.
configurations.configureEach {
    if (name in lockedConfigurations) {
        resolutionStrategy.activateDependencyLocking()
    }
}

/*
 * Resolves the locked configurations in one pass so `--write-locks` can write a complete lockfile.
 *
 * `--write-locks` only records configurations that actually got resolved during the build, so without
 * a task that touches all of them the lockfile would silently cover only part of the graph.
 * Deliberately guarded: it must never run except to write locks.
 */
tasks.register("resolveAndLockAll") {
    // Resolving configurations by hand needs the project at execution time, which the configuration
    // cache forbids. Opting out is safe precisely because this task is not part of any normal build —
    // it runs only when a human regenerates the lockfile.
    notCompatibleWithConfigurationCache("resolves configurations to regenerate gradle.lockfile")

    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks) {
            "resolveAndLockAll exists only to regenerate locks: run it with --write-locks"
        }
    }
    doLast {
        configurations
            .filter { it.name in lockedConfigurations && it.isCanBeResolved }
            .forEach { configuration ->
                // The *graph* is what gets locked, so resolve only that. Asking for artifacts as well
                // (`resolve()`, or a file collection) makes AGP's own `debugApiElements` variants
                // ambiguous and fails before any lock state is written.
                configuration.incoming.resolutionResult.root
            }
    }
}

android {
    namespace = "io.github.nsreader"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.nsreader"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
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

// Schemas are checked in: a diff here is the review signal that a migration is needed.
room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Arch Components
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    // Tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Instrumented tests
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Local tests: jUnit and coroutines.
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Robolectric runs the Room, Paging and Compose tests on the JVM, so CI needs no emulator.
    // `ui-test-manifest` is already a debugImplementation above, which is what these need.
    testImplementation(composeBom)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.paging.testing)
    testImplementation(libs.androidx.compose.ui.test.junit4)

    // Instrumented tests: jUnit rules and runners
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)

    // Navigation
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    // Networking: OkHttp shares its cookie jar with the WebView used for login.
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // NodeSeek has no public API for lists/details, so pages are scraped.
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)

    // Settings SSOT
    implementation(libs.androidx.datastore.preferences)

    // Offline-first SSOT: Room owns posts/comments/boards, the network only writes into it.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    // Paging drives the list straight off the database via a RemoteMediator.
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // Images: avatars, inline stickers and post attachments.
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)
}
