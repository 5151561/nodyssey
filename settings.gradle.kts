pluginManagement {
    // The convention plugins that carry the Android configuration every module shares.
    includeBuild("build-logic")

    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Nodyssey"
include(":app")

// The R8 smoke: a `com.android.test` module that installs `:app`'s minified build on the CI
// emulator, launches it, and checks it draws. Until it existed the optimized build was compiled on
// every commit and executed by nobody, so a stripped serializer or a shrunk resource was found by
// whichever user installed the release first.
//
// A separate module rather than pointing `:app`'s own androidTest at the minified variant, because
// the existing journey test is a Compose-rule test: it shares classes with the app under test, and
// R8's renaming breaks that sharing unless everything the test touches is kept — keep rules that
// would defeat the point of testing minification. This module's UIAutomator test drives the app
// from outside the process, the same shape a Macrobenchmark module uses, so the app stays exactly
// as shipped.
include(":smoke")

// Compose theme and components with no knowledge of any particular forum. Kept as its own module so
// the compiler, not a review convention, is what stops site-specific types leaking into it.
//
// `:designsys` and `:core` were extracted when a second application — the bbs1org client — shared
// this repository. That app is now https://github.com/5151561/plaza, carrying a *copy* of both
// modules rather than a dependency on these; edits here do not reach it. The boundary is still
// worth keeping on its own terms: it is what makes the site-specific half of this app visible as a
// thing with edges.
include(":designsys")

// Rich text, and nothing else: the `RichNode` tree a post body is parsed into, the Markdown reader
// that produces one, and the ANSI decoder the terminal blocks use.
//
// Its own module because `:designsys` needs exactly this and nothing else from below it. Until step
// D1 it took the whole of `:shared` to get it, which meant a component gallery resolving SQLite,
// Paging and a network layer to draw a paragraph — see `docs/kmp-migration-plan.md`, the second of
// the two debts A5–A7 left. Four files and 993 lines, with no dependency of their own beyond
// kotlinx-serialization, which is what made the cut worth making rather than arguing about.
include(":richtext")

// The platform-neutral business core: the domain model and the parsers that encode what this
// particular forum's HTML means. Kotlin Multiplatform rather than an Android library because nothing
// in here is allowed to know it is running on Android — see `docs/kmp-migration-plan.md`.
//
// It used to sit below `:core`, the Android shell that held OkHttp and the WebView cookie bridge.
// Step A5 moved that shell into this module's `androidMain` and `:core` stopped existing: the
// contract everything above the network is written against — `HttpTransport` — is in `commonMain`,
// and OkHttp is one of its two implementations. The other is `NSURLSession`.
include(":shared")

// `:designsys` with no Android under it, in a window.
//
// The module exists to be run by hand — `./gradlew :gallery:run` — and it is the whole evidence for
// step B3 of `docs/kmp-migration-plan.md`: that the design system compiles, links and *draws* on a
// platform that is not Android. Nothing ships it, nothing depends on it, and CI only compiles it.
//
// A separate module rather than a `main` inside `:designsys` on purpose: a consumer is what a
// library's multiplatform variants are for, and this one resolves them exactly the way a future
// Apple app would.
include(":gallery")

// The screens: everything that used to be `app/src/main/.../ui`, plus the strings and drawables they
// draw. A module of its own rather than a source set inside `:shared` because `:designsys` sits
// *below* `:shared` — a screen depends on both, so the two cannot be the same module without a cycle.
//
// Step D1 of `docs/kmp-migration-plan.md`. What stays in `:app` is the Android shell: the activity,
// the notification worker, the image pickers — the parts that name a platform rather than a screen.
include(":ui")

// The iOS shell: the entry point, the dependency graph, and the platform pieces that name UIKit or
// Foundation — what `:app` is for Android, on the other platform. Step D3b of
// `docs/kmp-migration-plan.md`.
//
// It holds the Xcode project as well as the Kotlin: the two are one deliverable, and a framework
// nobody links is not a shell. Nothing depends on this module; it depends on everything.
include(":iosapp")
