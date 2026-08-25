plugins {
    id("plaza.android.application")
    id("plaza.android.compose")
    alias(libs.plugins.kotlin.serialization)
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

    // Room's schema JSON moved to `:shared` with the `@Database` class in step A6, and
    // `NodeSeekDatabaseMigrationTest` stayed here — it is a test about the file on an installed
    // Android device, and Robolectric plus Room's Android `MigrationTestHelper` are what read it.
    // The helper looks the schemas up through the debug `AssetManager`, so this points at the
    // directory rather than keeping a second copy of it, for the same reason as the line below.
    sourceSets["debug"].assets.directories.add(project(":shared").projectDir.resolve("schemas").path)

    // The captured pages moved to `:shared` with the parsers that read them, and a handful of tests
    // left here — the challenge detector, two repositories, the reply composer — still read the same
    // captures. Pointed at rather than copied: two copies of a 100KB capture drift, and the one that
    // drifts is whichever the failing test is not reading.
    sourceSets["test"].resources.directories.add(project(":shared").projectDir.resolve("src/commonTest/resources").path)

    // The repository test doubles, for the same reason and by the same means — see the note in
    // `ui/build.gradle.kts`, which points at the same directory. What is left here that needs them
    // is the handful of tests whose subject is an Android shell: the offline file store, the image
    // cache, the six image-host clients.
    sourceSets["test"].kotlin.directories.add(
        project(":shared").projectDir.resolve("src/androidHostTest/kotlin/io/github/nodyssey/data/doubles").path,
    )
}

dependencies {
    // The screens. Everything under `io.github.nodyssey.ui` lives here since step D1, and so do the
    // 1,056 strings and the five preset avatars they draw — as Compose Resources rather than
    // `app/src/main/res`, which is why this module's `R` now holds only what the *platform* reads.
    implementation(project(":ui"))

    // The Compose theme and the components that carry no NodeSeek knowledge. The dependency only
    // goes this way: `:designsys` cannot see this module, which is what keeps site-specific types out
    // of it by compilation rather than by review.
    implementation(project(":designsys"))

    // The business core: the domain model, the parsers, and — since step A5 — the network layer
    // this app talks to nodeseek.com through. `HtmlSource`, `JsonApi` and `HttpTransport` are
    // `commonMain`; the `OkHttpClient` assembled below and the `OkHttpTransport` it is wrapped in are
    // that module's `androidMain`. What it needs to know about nodeseek.com reaches it as
    // `NodeSeekSite.CONFIG`, never as an import.
    implementation(project(":shared"))

    // The androidx Compose BOM, and it no longer governs what ships: everything below that used to
    // take its version from here is named as `org.jetbrains.compose` now, and the androidx artifacts
    // underneath those come with versions of their own. What is left for it are the two test
    // artifacts that are still androidx because they have no multiplatform counterpart, so it is
    // applied to the configurations those are declared on and nowhere else.
    val composeBom = platform(libs.androidx.compose.bom)
    debugImplementation(composeBom)
    androidTestImplementation(composeBom)

    // What compiles `src/main/baseline-prof.txt` on devices that never see Play: a sideloaded
    // install gets no cloud profile, so without this the committed profile would only help the
    // benchmark that measures it. See `:benchmark` for how the file is produced.
    implementation(libs.androidx.profileinstaller)

    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.core)

    // Arch Components
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose, as `org.jetbrains.compose` — the swap `:designsys` made in B3, arriving here in B4.
    // The package names are androidx's either way, so not one import in this module changes; what
    // changes is that no module in the repository still asks for Compose by an Android-only
    // coordinate, which was the precondition for `ui/` moving into `commonMain` in step D1.
    //
    // Most of these are now named here only because this module *ships* them: what composes against
    // them is `:ui`, and it names its own. They are kept rather than trimmed because this
    // `constraints` block at the bottom is what pins three androidx versions above what the
    // multiplatform pointers ask for, and a constraint governs the configuration it is declared on.
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    // NavigationSuiteScaffold: the bar becomes a rail once the window is wide enough, which
    // targetSdk 36 makes unavoidable — large screens can no longer be told to stay phone-shaped.
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.compose.material.icons.core)
    // Tooling, and the one Compose line that stayed androidx. `org.jetbrains.compose.ui:ui-tooling`
    // is the same empty pointer as the rest, except that its aar also carries an AndroidManifest
    // declaring `org.jetbrains.androidx.compose.ui.tooling.PreviewActivity` — a class that ships in
    // none of its artifacts. Swapping this line put an `exported` activity into the merged debug
    // manifest naming a class not present in any dex (checked by grepping the built APK). Nothing
    // launches it, but it buys nothing either: the classes come from androidx underneath either way.
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Instrumented tests
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    // Installs itself through a ContentProvider; no code names it. Debug-only by construction —
    // `debugImplementation` keeps it off every release classpath, which the lockfile can prove.
    debugImplementation(libs.leakcanary)

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
    // `TestListenableWorkerBuilder`, which is how the worker tests run `doWork` against fakes
    // without a WorkManager instance behind them.
    testImplementation(libs.androidx.work.testing)

    // Instrumented tests: jUnit rules and runners
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)

    // Navigation
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    // The list-detail scene strategy, on the multiplatform coordinate for the same reason as the
    // Compose block above. Navigation 3 itself stays androidx, and step D1 confirmed it can:
    // `navigation3-runtime` publishes a real desktop variant. `navigation3-ui` does not — it is
    // android plus a jvm *stub* — so `:ui` takes the `org.jetbrains.androidx.navigation3` mirror for
    // that half, which the adaptive line below was already dragging in.
    implementation(libs.compose.material3.adaptive.navigation3)

    // Networking: OkHttp shares its cookie jar with the WebView used for login.
    implementation(libs.okhttp)
    // 加密 DNS: the `Dns` implementation behind `AppDns`. See `core/net/AppDns.kt`.
    implementation(libs.okhttp.dnsoverhttps)

    implementation(libs.kotlinx.serialization.json)

    // Settings SSOT
    implementation(libs.androidx.datastore.preferences)

    // Background notification polling: the site has no push, so a periodic worker checks unread.
    implementation(libs.androidx.work.runtime)

    // Paging drives the list straight off the database via a RemoteMediator. Room, the entities and
    // the DAOs are `:shared` since step A6, and `paging-common` arrives with them; these two are the
    // Android half — the `PagingDataAdapter` machinery and the Compose collector.
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

    /*
     * The androidx versions underneath the multiplatform pointers.
     *
     * An `org.jetbrains.compose` artifact's Android variant is a 6KB aar whose `classes.jar` is 273
     * bytes of nothing and whose only content is a dependency on the androidx artifact of the same
     * name. So the swap above changes no class this app compiles against — but it does hand the
     * choice of *which* androidx version to the pointer, and for three of them the pointer is behind
     * where this repository runs: material3 asks for 1.5.0-alpha22 against the catalog's alpha24,
     * adaptive for 1.3.0-beta02 against rc01, and the icons mirror for 1.7.6 against the 1.7.8
     * androidx stopped at. Unstated, B4 would have been a silent downgrade of three libraries.
     *
     * Constraints rather than dependencies, so what this module *names* is still the multiplatform
     * coordinate. One line per group is enough: androidx publishes constraints on its siblings inside
     * every module, so material3 alpha24 brings `material3-adaptive-navigation-suite` with it and
     * adaptive-navigation3 rc01 brings `adaptive`, `adaptive-layout` and `adaptive-navigation`.
     */
    constraints {
        implementation(libs.androidx.compose.material3)
        implementation(libs.androidx.material3.adaptive.navigation3)
        implementation(libs.androidx.compose.material.icons.core)
    }
}
