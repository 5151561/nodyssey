package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable

/**
 * Keeps the platform's system bar icons legible against the theme the app actually resolved.
 *
 * The app's dark theme is a *setting*, not the OS's night mode — 深色 with the system on light is a
 * supported combination — and a shell that styles its bars once at startup styles them for the OS's
 * answer only. So the shell does the first paint and this effect, called from inside the theme with
 * the resolved value, does every one after: same split as the Custom Tab colours, which already
 * follow `LocalPlazaDarkTheme` for the same reason.
 *
 * An `expect` because only Android has bars the app styles: iOS derives the status bar from the
 * view controller, and the desktop window has no system bars at all.
 */
@Composable
internal expect fun SystemBarsMatchTheme(darkTheme: Boolean)
