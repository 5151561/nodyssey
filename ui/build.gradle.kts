// The custom-hierarchy overload of `applyDefaultHierarchyTemplate` is the only way to declare a
// source set between `commonMain` and two targets, and it is still marked experimental.
@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)

import com.android.build.api.withAndroid

plugins {
    id("plaza.kmp.library")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    // The same desktop JVM `:designsys` and `:shared` carry, and for the same reason: it is the
    // cheapest place to find out whether a screen still needs Android under it.
    //
    // No Apple target here, and the reason is `:designsys`: a screen is built out of its components,
    // and that module declares android and jvm only. A target a dependency does not have is a variant
    // that does not resolve, so `iosArm64` here would have to be preceded by `iosArm64` there — which
    // is step D3, where the WKWebView bridge and the IME are also answered for. What this module has
    // to show for D1 is that 40,000 lines of screen compile with no Android under them, and the
    // desktop JVM asks that question for the price of a JDK.
    jvm()

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

            // Navigation 3. androidx rather than a mirror: navigation3 is published as a
            // multiplatform library by androidx itself — see `docs/cmp-ui-decision.md` §2.4.
            api(libs.androidx.navigation3.runtime)
            api(libs.androidx.navigation3.ui)
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

compose.resources {
    // Named for the module rather than left to the default `io.github.nodyssey.ui.generated.resources`,
    // which puts a package called `generated` in the middle of an import that a person reads. Same
    // convention as `:designsys`.
    packageOfResClass = "io.github.nodyssey.ui.resources"

    // `:app` reads these too — `MainActivity` and the notification worker are Android shells around
    // screens that live here — so the generated class cannot be internal to this module.
    publicResClass = true
}
