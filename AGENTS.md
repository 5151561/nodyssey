# Repository Guidelines

## Project Structure & Module Organization

One application module, three library modules, and a desktop probe. `:app` is the NodeSeek application (Nodyssey): production Kotlin lives under `app/src/main/java/io/github/nodyssey/`, where `data/` owns repositories and Room persistence, `platform/` holds the Android shells behind `data`'s interfaces, and `ui/` contains Compose screens, view models, and navigation. The site's URL vocabulary, its CSS selectors, its parsers and the domain model are no longer here — they moved to `:shared`, under those same package names.

This repository briefly held a second application, a multi-instance client for the bbs1org forum software. It now lives in [Plaza](https://github.com/5151561/plaza) with a **copy** of `:designsys` and `:core` rather than a dependency on the ones here — the sharing was one-directional and stable, and a publish-and-bump cycle would have cost more than the occasional manual port. Nothing you change in the library modules reaches that app. If a fix matters to both, apply it on both sides by hand.

`:designsys` and `:core` hold what is not specific to any one forum, and **neither can see an application module** — the dependency runs one way only, so anything site-specific belongs in the app that knows it:

- `:designsys` (`designsys/src/commonMain/kotlin/io/github/plaza/designsys/`) — a Kotlin Multiplatform module since step B3, building for Android and the desktop JVM. Its Compose dependencies are `org.jetbrains.compose` rather than androidx, which changes the build file and not the source: the package names are androidx's either way, and on Android those coordinates resolve to the androidx artifacts. The four platform-named files are in `androidMain`, the two JVM ones (`java.net` exception classification, `java.text.BreakIterator`) in `jvmCommonMain`, and its 44 strings are Compose resources in `commonMain/composeResources` — not `res/values`, so nothing in `:app` can override them.

  It holds the Compose theme, the shared components, the Markdown editor, the emoji panel and `richtext/` — the post renderer. Components take their copy and their colours as parameters: `StatusView` is told what the state says, `TonalTag` and `BadgeChip` are told which tone to wear, `EmojiPanel` is handed its groups and a slot that fetches a sticker, and `RichContent` is handed slots for the two nodes it cannot draw alone — a live poll, and whatever a particular forum does with a code block. In this app that wrapper is `PostRichContent`, and every screen goes through it.
- `:core` (`core/src/main/java/io/github/plaza/core/`) — clocks and dispatchers, HTTP against a scraped forum, the cookie store the WebView and OkHttp share, ANSI decoding, the image data-usage policy, the GitHub update check, and `richtext/` — the `RichNode`/`InlineNode` tree a post body is rendered from, plus the small Markdown parser that produces one. **The `@SerialName` on every node is stored inside cached rows; moving those types between packages is safe, renaming a discriminator is not** (`PostDetailCacheTest.every node discriminator still decodes` is the guard). Everything a particular site knows about itself reaches it as a `SiteConfig` value, never as an import; `NodeSeekSite.CONFIG` is the one this app passes. Its platform-neutral half — `SiteConfig`, `SiteError`, `WebUrl`, `TerminalColumns`, ANSI decoding and `richtext/` — now lives in `:shared` and reaches consumers through `api(project(":shared"))`, so the package names did not change and the import sites did not either.
- `:shared` (`shared/src/commonMain/kotlin/`) — the business core: the domain model (`io.github.nodyssey.model`), the site's URL vocabulary and its parsers (`io.github.nodyssey.core`, written against Ksoup rather than jsoup), and the neutral types listed above. **Nothing in here may know it is on Android.** It builds for Android, the desktop JVM, `iosArm64` and `macosArm64`; the Apple targets only build on a Mac, so `./gradlew :shared:macosArm64Test` is a local gate CI cannot run. See `docs/kmp-migration-plan.md`.

`:gallery` (`gallery/src/jvmMain/kotlin/`) is not part of that boundary and not part of the app: it is `:designsys` in a desktop window and nothing else. `./gradlew :gallery:run` opens it; `:gallery:jvmTest` composes the same content headlessly, which is what CI runs. It exists so that "the design system draws with no Android under it" fails the build rather than waiting for somebody to remember to check. Nothing ships it and nothing depends on it.

Shared Android configuration lives in `build-logic/` as five convention plugins (`plaza.android.application`, `plaza.android.library`, `plaza.android.compose`, `plaza.kmp.library`, `plaza.dependency-locking`); a module's own build file should carry only what genuinely differs. **Which targets a KMP module declares is not one of those things** — it is a decision about that module, so `iosArm64()` / `macosArm64()` are in `shared/build.gradle.kts` and `jvm()` is in `designsys/build.gradle.kts`. Android resources are in each module's `src/main/res`. JVM and Robolectric tests mirror production packages under `src/test/java`; the KMP modules use `src/commonTest/kotlin` instead, plus `src/androidHostTest/kotlin` for the tests that need a JVM class or an Android manifest on purpose. Captured HTML lives in `shared/src/commonTest/resources/fixtures` — read as generated Kotlin constants by the common tests, and as ordinary resources by the `:app` tests, which take that directory as a resource root. Instrumented tests belong in `app/src/androidTest`. Room schemas are versioned in each module's `schemas/` — only `:app` has one today; architecture and design context lives in `docs/`.

## Build, Test, and Development Commands

The project requires JDK 21 and Android SDK 37. Use the checked-in Gradle wrapper:

- `./gradlew :app:assembleDebug` builds the debug APK.
- `./gradlew testDebugUnitTest testAndroidHostTest jvmTest` runs JVM, Robolectric, Room, Paging, and Compose tests. Unqualified on purpose — `:app:testDebugUnitTest` compiles the library modules but runs none of their tests. The second name is the KMP modules': they have no build types, so `testDebugUnitTest` does not exist there and their tests would be silently skipped. The third is a different platform rather than another name for the same one — it runs the common tests again on the desktop JVM, and it is what executes `:gallery`.
- `./gradlew :gallery:run` opens the design system in a desktop window. Not a gate; it is how a change to `:designsys` gets looked at without an emulator.
- `./gradlew :shared:macosArm64Test` runs the same common tests on Kotlin/Native. Mac only, and not part of CI — it is what catches common code that only the JVM accepts. It needs a full Xcode rather than the Command Line Tools: if `xcode-select -p` answers `/Library/Developer/CommandLineTools`, the link step fails inside `CurrentXcode.xcrun`, and the fix is to prefix the command with `DEVELOPER_DIR=` pointing at whichever Xcode is installed — not to change `xcode-select` globally. Do not hardcode `/Applications/Xcode.app`: a `DEVELOPER_DIR` naming a path that does not exist overrides a working default and produces the same failure it was meant to avoid.
- `./gradlew :app:lintDebug` runs Android lint with warnings treated as errors. `checkDependencies` is on, so this one invocation also covers the libraries the app depends on.
- `./gradlew spotlessCheck` verifies formatting; `./gradlew spotlessApply` fixes it.
- `./gradlew resolveAndLockAll --write-locks` refreshes every module's `gradle.lockfile` after dependency changes. Commit all of them.

Before submitting, run the same gates as `.github/workflows/ci.yml`. After builds, run `./gradlew --stop` so Gradle daemons do not remain in memory.

## Dependencies & Official APIs

`gradle/libs.versions.toml` is the single source of truth for every version, and it is kept current
on purpose: AGP 9.3.1 / Kotlin 2.4.10 / Compose BOM 2026.06.00 / Material 3 1.5.0-alpha24 /
Compose Multiplatform 1.12.0-rc01 (its Material 3 is a separate version line, 1.12.0-alpha03) /
Navigation 3 / OkHttp 5 / Coil 3 / Room 2.8 / Paging 3.5 / coroutines 1.11, on JDK 21, compileSdk 37,
minSdk 26, targetSdk 36. Never inline a version in a build file, and run
`./gradlew resolveAndLockAll --write-locks` after any dependency change. The SDK levels and the JDK
now live in the `build-logic` convention plugins rather than in a module's build file.

Before writing code against any of these libraries, find out what the **pinned** version actually
offers — read its release notes or the resolved sources, do not code from memory of an older
release. Recalled API shapes go stale faster than this catalog does. Prefer the newest supported
API the pinned version provides over the older one that still compiles, and delete the workarounds a
version bump makes obsolete instead of leaving them beside the new API.

Implement each feature with the method the official component already provides; do not route around
it with a hand-rolled equivalent. In practice:

- Theming and motion come from `MaterialExpressiveTheme` with `MotionScheme.expressive()` in
  `ui/theme/Theme.kt`. Take animation specs from `MaterialTheme.motionScheme`; do not hand-write
  `tween`/`spring` at call sites.
- Use Material 3 components and their slot/parameter APIs rather than re-implementing a component
  out of `Box`/`Row`. Same rule for Navigation 3 back stacks, Paging 3 sources and mediators, Room
  queries and migrations, DataStore, WorkManager, and Coil loaders.
- Only hand-roll when the official API genuinely cannot express the requirement. When that happens,
  say so in a comment at the site: which API was tried, what it could not do, and the condition
  under which the workaround should be removed.

Material 3 stays on a 1.5 alpha because that is where these APIs are public, and the adaptive
libraries are on rc. Treat every bump of those as behavioural: run the full UI, lint, and release
gates before trusting it.

## Coding Style & Naming Conventions

Use four-space indentation, LF endings, UTF-8, and trailing commas in multiline Kotlin. Spotless with ktlint is authoritative; rule overrides are in the root `build.gradle.kts`. Name classes and Composables in `PascalCase`, functions and properties in `camelCase`, and tests as readable backtick sentences. Preserve the repository’s UDF flow: Repository → ViewModel → immutable `UiState` → Compose. Keep CSS selectors centralized in `core/html/Selectors.kt`, inject dispatchers and clocks, and never swallow coroutine cancellation.

## Testing Guidelines

Name test files `*Test.kt` and place them beside the corresponding package. Parser tests must use committed fixtures, never the live NodeSeek site. Add regression tests for bug fixes and update committed Room schemas whenever entities or migrations change. No numeric coverage target exists; changed behavior should have focused tests.

## Commit & Pull Request Guidelines

Follow the history’s short, imperative summaries, in Chinese or English, such as `修复正文图片排版` or `Add offline cache`. Keep commits focused. Pull requests should explain the problem and solution, link relevant issues, list verification commands, and include before/after screenshots for UI changes. Call out schema, dependency-lock, session, or scraping-selector changes explicitly.

## Security & Configuration

Never commit cookies, credentials, local SDK paths, or captured authenticated data. Keep requests conservative, share session state only through the existing WebView/OkHttp cookie bridge, and treat changes to allowed WebView domains as security-sensitive.
