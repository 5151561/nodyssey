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

// The second application in this repository: the multi-instance bbs1org client. A separate app with
// its own applicationId and its own release tags, sharing `:core` and `:designsys` and nothing else —
// the two forums' domain models are too different to share a Site abstraction, so each app owns its
// model/data/ui outright.
include(":bbs1")

// Compose theme and components with no knowledge of any particular forum. Kept as its own module so
// the compiler, not a review convention, is what stops site-specific types leaking into it.
include(":designsys")

// The non-visual half of the same boundary: HTTP against a scraped forum, the cookie bridge the
// WebView and OkHttp share, and the in-app update check. Everything a particular site knows about
// itself arrives as `SiteConfig` rather than as a constant in here.
include(":core")
