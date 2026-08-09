/*
 * `build-logic` is an included build, not a subproject, so it configures its own repositories.
 *
 * The root `settings.gradle.kts` sets `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, but that governs the
 * main build only — an included build gets none of it and would otherwise have nowhere to resolve the
 * Android Gradle Plugin from.
 */
pluginManagement {
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

    // The same catalog the main build reads, so a plugin version is still declared exactly once.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
