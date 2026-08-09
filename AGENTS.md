# Repository Guidelines

## Project Structure & Module Organization

Three modules. `:app` is the NodeSeek application: production Kotlin lives under `app/src/main/java/io/github/nodyssey/`, where `core/` contains the site's URL vocabulary, its CSS selectors and its parsers; `data/` owns repositories and Room persistence; `model/` defines domain types; and `ui/` contains Compose screens, view models, and navigation.

Two library modules under `io.github.plaza.*` hold what a second forum app would reuse, and **neither can see `:app`** — the dependency runs one way only, so anything site-specific belongs in `:app`:

- `:designsys` (`designsys/src/main/java/io/github/plaza/designsys/`) — the Compose theme, the shared components, the Markdown editor, the emoji panel and `richtext/` — the post renderer. Components take their copy and their colours as parameters: `StatusView` is told what the state says, `TonalTag` and `BadgeChip` are told which tone to wear, `EmojiPanel` is handed its groups and a slot that fetches a sticker, and `RichContent` is handed slots for the two nodes it cannot draw alone — a live poll, and whatever a particular forum does with a code block. In this app that wrapper is `PostRichContent`, and every screen goes through it.
- `:core` (`core/src/main/java/io/github/plaza/core/`) — clocks and dispatchers, HTTP against a scraped forum, the cookie store the WebView and OkHttp share, ANSI decoding, the image data-usage policy, the GitHub update check, and `richtext/` — the `RichNode`/`InlineNode` tree a post body is rendered from, plus the small Markdown parser that produces one. **The `@SerialName` on every node is stored inside cached rows; moving those types between packages is safe, renaming a discriminator is not** (`PostDetailCacheTest.every node discriminator still decodes` is the guard). Everything a particular site knows about itself reaches it as a `SiteConfig` value, never as an import; `NodeSeekSite.CONFIG` is the one this app passes.

Shared Android configuration lives in `build-logic/` as three convention plugins (`plaza.android.application`, `plaza.android.library`, `plaza.android.compose`); a module's own build file should carry only what genuinely differs. Android resources are in each module's `src/main/res`. JVM and Robolectric tests mirror production packages under `src/test/java`, with captured HTML in `app/src/test/resources/fixtures`. Instrumented tests belong in `app/src/androidTest`. Room schemas are versioned in each module's `schemas/` — only `:app` has one today; architecture and design context lives in `docs/`.

## Build, Test, and Development Commands

The project requires JDK 21 and Android SDK 37. Use the checked-in Gradle wrapper:

- `./gradlew :app:assembleDebug` builds the debug APK.
- `./gradlew testDebugUnitTest` runs JVM, Robolectric, Room, Paging, and Compose tests. Unqualified on purpose — `:app:testDebugUnitTest` compiles the library modules but runs none of their tests.
- `./gradlew :app:lintDebug` runs Android lint with warnings treated as errors. `checkDependencies` is on, so this one invocation covers every module.
- `./gradlew spotlessCheck` verifies formatting; `./gradlew spotlessApply` fixes it.
- `./gradlew resolveAndLockAll --write-locks` refreshes every module's `gradle.lockfile` after dependency changes. Commit all of them.

Before submitting, run the same gates as `.github/workflows/ci.yml`. After builds, run `./gradlew --stop` so Gradle daemons do not remain in memory.

## Dependencies & Official APIs

`gradle/libs.versions.toml` is the single source of truth for every version, and it is kept current
on purpose: AGP 9.2.1 / Kotlin 2.4.10 / Compose BOM 2026.06.00 / Material 3 1.5.0-alpha24 /
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
