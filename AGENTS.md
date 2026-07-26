# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Android application. Production Kotlin lives under `app/src/main/java/io/github/nsreader/`: `core/` contains networking, parsing, and shared utilities; `data/` owns repositories and Room persistence; `model/` defines domain types; and `ui/` contains Compose screens, view models, navigation, and theme code. Android resources are in `app/src/main/res`. JVM and Robolectric tests mirror production packages under `app/src/test/java`, with captured HTML in `app/src/test/resources/fixtures`. Instrumented tests belong in `app/src/androidTest`. Room schemas are versioned in `app/schemas`; architecture and design context lives in `docs/`.

## Build, Test, and Development Commands

The project requires JDK 21 and Android SDK 36. Use the checked-in Gradle wrapper:

- `./gradlew :app:assembleDebug` builds the debug APK.
- `./gradlew :app:testDebugUnitTest` runs JVM, Robolectric, Room, Paging, and Compose tests.
- `./gradlew :app:lintDebug` runs Android lint with warnings treated as errors.
- `./gradlew spotlessCheck` verifies formatting; `./gradlew spotlessApply` fixes it.
- `./gradlew resolveAndLockAll --write-locks` refreshes `app/gradle.lockfile` after dependency changes.

Before submitting, run the same gates as `.github/workflows/ci.yml`. After builds, run `./gradlew --stop` so Gradle daemons do not remain in memory.

## Coding Style & Naming Conventions

Use four-space indentation, LF endings, UTF-8, and trailing commas in multiline Kotlin. Spotless with ktlint is authoritative; rule overrides are in the root `build.gradle.kts`. Name classes and Composables in `PascalCase`, functions and properties in `camelCase`, and tests as readable backtick sentences. Preserve the repository’s UDF flow: Repository → ViewModel → immutable `UiState` → Compose. Keep CSS selectors centralized in `core/html/Selectors.kt`, inject dispatchers and clocks, and never swallow coroutine cancellation.

## Testing Guidelines

Name test files `*Test.kt` and place them beside the corresponding package. Parser tests must use committed fixtures, never the live NodeSeek site. Add regression tests for bug fixes and update committed Room schemas whenever entities or migrations change. No numeric coverage target exists; changed behavior should have focused tests.

## Commit & Pull Request Guidelines

Follow the history’s short, imperative summaries, in Chinese or English, such as `修复正文图片排版` or `Add offline cache`. Keep commits focused. Pull requests should explain the problem and solution, link relevant issues, list verification commands, and include before/after screenshots for UI changes. Call out schema, dependency-lock, session, or scraping-selector changes explicitly.

## Security & Configuration

Never commit cookies, credentials, local SDK paths, or captured authenticated data. Keep requests conservative, share session state only through the existing WebView/OkHttp cookie bridge, and treat changes to allowed WebView domains as security-sensitive.
