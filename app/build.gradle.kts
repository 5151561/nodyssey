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

/*
 * Release signing, supplied by the environment.
 *
 * The keystore is not in the repository and neither is its password, so a clone without them still
 * builds: with nothing set, `assembleRelease` produces the unsigned APK it always did and signing
 * stays a manual apksigner step. `.github/workflows/release.yml` is what fills these in — it decodes
 * the keystore from a secret into a file on the runner and points this at it.
 *
 * Read through `providers` rather than `System.getenv` so the configuration cache records them as
 * inputs instead of baking today's values into a cache entry that outlives them.
 */
val keystoreFile = providers.environmentVariable("NODYSSEY_KEYSTORE_FILE").orNull?.let(::File)?.takeIf { it.isFile }
val keystorePassword = providers.environmentVariable("NODYSSEY_KEYSTORE_PASSWORD").orNull
val keystoreKeyAlias = providers.environmentVariable("NODYSSEY_KEY_ALIAS").orNull
val keystoreKeyPassword = providers.environmentVariable("NODYSSEY_KEY_PASSWORD").orNull
val hasReleaseSigning =
    keystoreFile != null && keystorePassword != null && keystoreKeyAlias != null && keystoreKeyPassword != null

android {
    namespace = "io.github.nodyssey"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.nodyssey"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = keystoreKeyAlias
                keyPassword = keystoreKeyPassword
                // Stated rather than inherited, so the shipped APK carries the same schemes as every
                // version before it: minSdk 26 has no use for v1 JAR signing, v2 and v3 are what the
                // v1.0.0 and v1.0.1 downloads verify with.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
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
            // Null without the environment above, which leaves the release APK unsigned — the state
            // this project shipped 1.0.0 and 1.0.1 from.
            signingConfig = signingConfigs.findByName("release")
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

    // Robolectric's MigrationTestHelper reads schemas through the debug AssetManager.
    sourceSets["debug"].assets.directories.add("$projectDir/schemas")

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
    implementation(libs.kotlinx.coroutines.core)

    // Arch Components
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // NavigationSuiteScaffold: the bar becomes a rail once the window is wide enough, which
    // targetSdk 36 makes unavoidable — large screens can no longer be told to stay phone-shaped.
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
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
    implementation(libs.androidx.material3.adaptive.navigation3)

    // Networking: OkHttp shares its cookie jar with the WebView used for login.
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // NodeSeek has no public API for lists/details, so pages are scraped.
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)

    // Settings SSOT
    implementation(libs.androidx.datastore.preferences)

    // Background notification polling: the site has no push, so a periodic worker checks unread.
    implementation(libs.androidx.work.runtime)

    // Offline-first SSOT: Room owns posts/comments/boards, the network only writes into it.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    // Paging drives the list straight off the database via a RemoteMediator.
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // Custom Tabs: forum posts are full of outbound links, and handing each one to the system
    // browser puts a task switch between the reader and the thread they were in.
    implementation(libs.androidx.browser)

    // Images: avatars, inline stickers and post attachments.
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)
    // The site's generated default avatars are SVG served from a `.png` path.
    implementation(libs.coil.svg)
}
