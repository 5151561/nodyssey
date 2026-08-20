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

// Compose theme and components with no knowledge of any particular forum. Kept as its own module so
// the compiler, not a review convention, is what stops site-specific types leaking into it.
//
// `:designsys` and `:core` were extracted when a second application — the bbs1org client — shared
// this repository. That app is now https://github.com/5151561/plaza, carrying a *copy* of both
// modules rather than a dependency on these; edits here do not reach it. The boundary is still
// worth keeping on its own terms: it is what makes the site-specific half of this app visible as a
// thing with edges.
include(":designsys")

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
