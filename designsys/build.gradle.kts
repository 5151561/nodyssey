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
    // The desktop JVM, and the reason it is here is that it is the cheapest possible proof: this
    // module is 6,600 lines of Compose that used to be unable to compile for anything but Android,
    // and a target needing no Mac, no simulator and no toolchain beyond the JDK already in use is
    // enough to find out whether that is still true. See `docs/kmp-migration-plan.md` §5, step B3.
    jvm()

    // `java.net.SocketTimeoutException` and `java.text.BreakIterator` are not the Android part of
    // Android — they are the JVM part, and both targets answer for them identically. Without a
    // source set between `commonMain` and the two of them, the two `actual`s below would be the same
    // file written twice.
    //
    // Through the hierarchy template rather than `dependsOn` by hand: doing it by hand switches the
    // default template off for the whole module, which would take `androidHostTest` and `jvmTest`
    // with it.
    applyDefaultHierarchyTemplate {
        common {
            group("jvmCommon") {
                withAndroid()
                withJvm()
            }
        }
    }

    android {
        namespace = "io.github.plaza.designsys"

        // Off by default in a KMP Android library, and the failure it causes is not a message about
        // resources: Compose Resources packages its `.cvr` files as *assets*, and it does that by
        // asking the variant for `sources.assets`, which is null while this is false. The symptom is
        // `copyAndroidMainComposeResourcesToAndroidAssets` failing configuration validation with
        // "property 'outputDirectory' doesn't have a configured value" — a task nobody ever gave an
        // output because nothing accepted its output.
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api` because `rememberTerminalText` takes `AnsiSpan` in its signature and `RichContent`
            // takes a `RichNode`: a consumer building either has to be able to name the type.
            api(project(":shared"))

            // Compose Multiplatform rather than androidx, which is the change that made this module
            // buildable off Android at all. The package names are androidx's either way — no import
            // in this module names `org.jetbrains.compose` except the two below, which have no
            // androidx counterpart — and on Android these coordinates resolve to the androidx
            // artifacts, so `:app` links against exactly what it did before.
            //
            // `api`, not `implementation`, for the same reason the androidx lines said so: a consumer
            // writes Compose against these types in its own source.
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            api(libs.compose.material3)
            api(libs.compose.ui.tooling.preview)
            api(libs.compose.material.icons.core)

            // `BackHandler` in `commonMain`. `implementation` because nothing leaves: the module's
            // own `PlazaBackHandler` is what a consumer names — see that file for why it still
            // exists now that the function it wraps is multiplatform.
            implementation(libs.compose.ui.backhandler)

            // `Res` and the generated string accessors. The 44 strings this module owns were
            // `src/main/res/values/strings.xml` and are now `commonMain/composeResources/values`:
            // same file, same names, read through a generated Kotlin object instead of a generated
            // `R`. `:app`'s 1,059 are a separate step (D2) — nothing here reaches them.
            implementation(libs.compose.components.resources)

            // The seed-colour scheme generator. `implementation`, not `api`: a caller hands
            // `PlazaTheme` a `Color` and gets a `ColorScheme` back, and never names an Hct or a
            // DynamicScheme itself.
            implementation(libs.material.color.utilities)

            // Avatars load over the network. Only `coil-compose` — the GIF and SVG decoders are a
            // decision about a particular site's content, so they stay with the app that needs them.
            implementation(libs.coil.compose)

            // `api`, not `implementation`: `AllowMeteredImage` is an `Extras.Key` and
            // `allowMeteredImage` extends `ImageRequest.Builder`, so both names are on this module's
            // surface. It arrives through `coil-compose` anyway; saying so is what keeps that an
            // accident rather than the reason.
            api(libs.coil.core)

            // `implementation`: reading why a fetch failed means naming Coil's `HttpException`, but
            // that type stops here — [ImageLoadFailure] is what leaves, and a consumer matches on
            // that.
            implementation(libs.coil.network.core)
        }

        androidMain.dependencies {
            // Custom Tabs: a thread is mostly other people's links, and handing each one to the
            // system browser puts a task switch between the reader and the thread they were in.
            // Reached from `AndroidCustomTabUriHandler.kt` and nowhere else; what the rest of the
            // module knows about opening a link is Compose's own `LocalUriHandler`.
            implementation(libs.androidx.browser)

            // `androidx.core.net.toUri`, in the same file. Named rather than inherited: it used to
            // arrive through the androidx Compose artifacts, and those are no longer what this
            // module asks for.
            implementation(libs.androidx.core.ktx)
        }

        androidHostTest.dependencies {
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.test)

            // The androidx Compose test artifacts, and there is no multiplatform substitute for the
            // second one: `ui-test-manifest` contributes the activity `createComposeRule` launches,
            // which is an Android manifest and therefore an Android artifact. The BOM comes with it
            // because neither line carries a version of its own.
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.compose.ui.test.manifest)

            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.ext.junit)

            // The fake Coil engine: image-size-dependent layout — a badge kept at natural size, two
            // badges sharing a row — cannot be asserted against an image that never arrives.
            implementation(libs.coil.test)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)

            // `TestMonotonicFrameClock`, which the one-hand app bar's nested-scroll tests drive by
            // hand. The Compose *test* artifacts are the multiplatform ones here — unlike in
            // `androidHostTest`, where the manifest one has no counterpart.
            implementation(libs.compose.ui.test)
        }

        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

compose.resources {
    // Named for the module rather than left to the default `io.github.plaza.designsys.generated.resources`,
    // which puts a package called `generated` in the middle of an import that a person reads.
    packageOfResClass = "io.github.plaza.designsys.resources"
}
