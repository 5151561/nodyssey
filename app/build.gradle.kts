plugins {
    id("plaza.android.application")
    id("plaza.android.compose")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
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
    defaultConfig {
        applicationId = "io.github.nodyssey"
        // Declared in `gradle.properties`; see the note there for why not here. Read through
        // `providers` so the configuration cache records the property as an input.
        versionCode = providers.gradleProperty("nodyssey.versionCode").get().toInt()
        versionName = providers.gradleProperty("nodyssey.versionName").get()
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
        release {
            // Null without the environment above, which leaves the release APK unsigned — the state
            // this project shipped 1.0.0 and 1.0.1 from. Assigned here rather than in
            // `plaza.android.application` because the config it names is declared just above, which
            // is after the convention plugin has already run.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // Robolectric's MigrationTestHelper reads schemas through the debug AssetManager.
    sourceSets["debug"].assets.directories.add("$projectDir/schemas")
}

// Schemas are checked in: a diff here is the review signal that a migration is needed.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // The Compose theme and the components that carry no NodeSeek knowledge. The dependency only
    // goes this way: `:designsys` cannot see this module, which is what keeps site-specific types out
    // of it by compilation rather than by review.
    implementation(project(":designsys"))

    // The same rule for the non-visual half: HTTP, the WebView cookie bridge, the update check. What
    // `:core` needs to know about nodeseek.com reaches it as `NodeSeekSite.CONFIG`, never as an
    // import.
    implementation(project(":core"))

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
    implementation(libs.coil.network.cache.control)
    // The site's generated default avatars are SVG served from a `.png` path.
    implementation(libs.coil.svg)
}
