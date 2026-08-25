package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable
import io.github.nodyssey.data.settings.AppLanguage

/**
 * Draws [content] in [language], redrawing it in another when that value changes — where the
 * platform allows it.
 *
 * Compose Resources picks a bundle through `LocalComposeEnvironment`, which is `internal`: there is
 * no providing an environment of our own, and no supported way to tell it a language. Its default
 * implementation reads `androidx.compose.ui.text.intl.Locale.current` — on Android
 * `android.os.LocaleList.getDefault()`, a process-wide value that is not snapshot state, so setting
 * it recomposes nothing on its own. That, and not any limit of Compose, is why changing a language
 * used to need the activity torn down.
 *
 * Android has a lever anyway, and the Android actual explains it. iOS and the desktop JVM do not,
 * which is what [appLanguageAppliesOnRestart] says out loud.
 *
 * Null means the settings store has not answered yet, and the honest move is to change nothing:
 * on Android the shell's `attachBaseContext` has already put the *stored* choice into the
 * configuration this composition starts from, so the first frames are right as they stand.
 * Substituting [AppLanguage.SYSTEM] for "no answer yet" — which is what a placeholder
 * `UserSettings()` would do — reset that to the device's language for a frame or two on every
 * cold start, and a reader whose chosen language differs from the device's watched the whole
 * screen flash through the wrong one. The same null-guard `ApplyAppLanguage` documents, for the
 * same reason.
 */
@Composable
expect fun ProvideAppLanguage(
    language: AppLanguage?,
    content: @Composable () -> Unit,
)

/**
 * Whether a change to 语言 only shows up the next time the app is started.
 *
 * True where nothing can make Compose Resources re-read the locale inside a running process, which
 * is every platform but Android — see [ProvideAppLanguage]. The settings screen draws a line of
 * explanation where this is true and stays quiet where it is not, because a control that appears to
 * do nothing is worse than one that says when it will.
 */
expect val appLanguageAppliesOnRestart: Boolean
