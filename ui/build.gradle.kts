// The custom-hierarchy overload of `applyDefaultHierarchyTemplate` is the only way to declare a
// source set between `commonMain` and two targets, and it is still marked experimental.
@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)

import com.android.build.api.withAndroid

plugins {
    id("plaza.kmp.library")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)

    // The navigation keys are `@Serializable`: `rememberNavBackStack` saves the stack across process
    // death by serializing them. The annotation arrives through `:shared`, but the plugin that turns
    // it into a serializer does not — without this line every key compiles and none of them restores,
    // which is a runtime failure `PostDetailKeySavedStateTest` is what catches.
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // The same desktop JVM `:designsys` and `:shared` carry, and for the same reason: it is the
    // cheapest place to find out whether a screen still needs Android under it.
    //
    jvm()

    // The Apple targets, which step D1 could not have: a screen is built out of `:designsys`
    // components, and that module declared android and jvm only, so an `iosArm64` here would have been
    // a variant that does not resolve. It declares both of these now, for the same reason this module
    // does: macOS is answered by the JVM above, iOS is not answered by anything else.
    //
    // D3a stopped at compiling: with no shell to launch it from, what the device arch bought was a
    // compiler checking that 40,000 lines of screen name nothing Android-only. D3b adds the simulator
    // arch, which is the one the shell is actually run on.
    iosArm64()
    iosSimulatorArm64()

    applyDefaultHierarchyTemplate {
        common {
            group("jvmCommon") {
                withAndroid()
                withJvm()
            }
        }
    }

    android {
        namespace = "io.github.nodyssey.ui"

        // Off by default in a KMP Android library. Compose Resources packages its `.cvr` files as
        // *assets* and asks the variant for `sources.assets`, which is null while this is false —
        // see the same block in `designsys/build.gradle.kts` for the shape of that failure.
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // The components and the theme. `api` because a screen's own signature names them —
            // `PlazaTheme`, `RichContent`, the app bars — and because everything Compose arrives
            // through it: that module already puts `runtime`, `foundation`, `ui`, `material3`,
            // `ui-tooling-preview` and the icons on its `api` face.
            api(project(":designsys"))

            // The repositories, the domain model and the database. `api` for the same reason: a
            // `ViewModel` constructor takes a repository and its state holds domain types.
            api(project(":shared"))

            // `Res` and the generated accessors. `api` rather than `implementation`: `:app` still
            // holds the Android shell — the notification worker, the activity — and those read the
            // same strings the screens do.
            api(libs.compose.components.resources)

            // `ViewModel`, `viewModelScope` and `viewModel()`. `api` because 37 screens name their
            // own `ViewModel` subclass in a public signature.
            api(libs.androidx.lifecycle.viewmodel.compose)

            // `collectAsStateWithLifecycle`, which is how every one of those screens reads its state.
            api(libs.androidx.lifecycle.runtime.compose)

            // Navigation 3, and only half of it is what `docs/cmp-ui-decision.md` §2.4 said it was:
            // `navigation3-runtime` is genuinely multiplatform androidx, but `navigation3-ui` — the
            // one that has `NavDisplay` in it — publishes android plus a jvm *stub*. That is the
            // shape B4 warned about, and the desktop compilation here is what surfaced it. So the
            // runtime is androidx and the UI half is the mirror; the constraint at the bottom of
            // this file is what stops the mirror's older pointer from deciding the version.
            api(libs.androidx.navigation3.runtime)
            api(libs.compose.navigation3.ui)
            api(libs.androidx.lifecycle.viewmodel.navigation3)

            // The adaptive half, on the multiplatform coordinates: androidx publishes these two with
            // an Android variant and a stub and nothing else.
            api(libs.compose.material3.adaptive.navigation.suite)
            api(libs.compose.material3.adaptive.navigation3)

            // The feed is a `PagingData` all the way to the `LazyColumn`. `paging-common` arrives
            // through `:shared`; this is the Compose collector.
            api(libs.androidx.paging.compose)

            // Avatars, stickers and attachments. `:designsys` already puts `coil-compose` on its
            // implementation face; a screen names `ImageRequest` and `AsyncImage` itself.
            implementation(libs.coil.compose)

            // `BackHandler` in `commonMain` — the same line `:designsys` carries, and for the same
            // reason: the androidx one ships inside `activity-compose`.
            implementation(libs.compose.ui.backhandler)
        }

        androidHostTest.dependencies {
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.test)

            // The androidx Compose test artifacts, and there is no multiplatform substitute for the
            // second one: `ui-test-manifest` contributes the activity `createComposeRule` launches,
            // which is an Android manifest and therefore an Android artifact. Same two lines, same
            // reason, as `designsys/build.gradle.kts`.
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.compose.ui.test.manifest)

            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.ext.junit)

            // The feed is a `PagingData` and the screens that draw one are tested against it.
            implementation(libs.androidx.paging.testing)

            // `inMemoryDatabase` in the shared test doubles below opens a real Room database.
            implementation(libs.androidx.room.testing)
        }

        androidMain.dependencies {
            // The photo picker and `rememberLauncherForActivityResult`. Eight screens reach for it,
            // and there is no neutral seat for it yet — see the `expect` in `commonMain` for which
            // ones, and `docs/kmp-migration-plan.md` §5 for why that is D3's problem rather than
            // this step's.
            implementation(libs.androidx.activity.compose)

            // `androidx.core.net.toUri` and `androidx.core.graphics.scale`, in the Android files
            // beside it.
            implementation(libs.androidx.core.ktx)
        }
    }
}

/*
 * The androidx version underneath the multiplatform pointer above.
 *
 * `org.jetbrains.androidx.navigation3:navigation3-ui` 1.1.0 asks for `androidx.navigation3` 1.1.0,
 * and this repository runs 1.1.4. Unstated, naming the mirror would have been a silent downgrade of
 * navigation for every module below `:app` — the same trap `app/build.gradle.kts` records for
 * material3 and the adaptive libraries in step B4.
 *
 * A constraint rather than a dependency, so what this module *names* is still the multiplatform
 * coordinate. One line covers both artifacts: androidx publishes constraints on its siblings.
 */
dependencies {
    constraints {
        "commonMainApi"(libs.androidx.navigation3.ui)
    }
}

/*
 * The repository's test doubles, compiled into this module's tests rather than depended on.
 *
 * `TestDoubles.kt` lives with the repository tests in `:shared`, and a test source set is not
 * published — so a screen test that needs `FakePostRemoteDataSource` or `inMemoryDatabase` has two
 * options: a second copy, or the same file compiled twice. The same file, for the reason
 * `app/build.gradle.kts` gives about the captured pages: two copies drift, and the one that drifts
 * is whichever the failing test is not reading. `internal` resolves per compilation, so each module
 * gets its own — which is also why this is a source directory and not a dependency.
 */
kotlin.sourceSets.getByName("androidHostTest").kotlin.srcDir(
    project(":shared").file("src/androidHostTest/kotlin/io/github/nodyssey/data/doubles"),
)

// The captured pages, read as a classpath resource — see `Fixtures` in the test sources. Pointed at
// rather than copied, the same way `:app` points at them.
kotlin.sourceSets.getByName("androidHostTest").resources.srcDir(
    project(":shared").file("src/commonTest/resources"),
)

compose.resources {
    // Named for the module rather than left to the default `io.github.nodyssey.ui.generated.resources`,
    // which puts a package called `generated` in the middle of an import that a person reads. Same
    // convention as `:designsys`.
    packageOfResClass = "io.github.nodyssey.ui.resources"

    // `:app` reads these too — `MainActivity` and the notification worker are Android shells around
    // screens that live here — so the generated class cannot be internal to this module.
    publicResClass = true
}
