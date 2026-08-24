package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable
import io.github.nodyssey.data.settings.AppLanguage

/**
 * Puts 语言 into force, wherever the platform keeps that answer.
 *
 * Called once from `NodysseyRoot` with the settings SSOT's value, so it runs at every launch and
 * again the moment the setting changes — the same shape the theme reads its own value in. It is
 * idempotent by contract: an implementation is handed the language on every composition of the
 * root, and applying one that is already in force must do nothing at all. The Android actual
 * recreates the activity, and a recreate that fires on each launch would be a visible flash.
 *
 * There is no return value and nothing reads the applied locale back. What a resource lookup
 * resolves against is `androidx.compose.ui.text.intl.Locale.current`, which is the platform's
 * process-wide answer rather than anything this app can hand it — so these actuals change *that*,
 * and Compose Resources follows on its own.
 */
@Composable
expect fun ApplyAppLanguage(language: AppLanguage)

/**
 * Whether a change to 语言 only shows up the next time the app is started.
 *
 * True where the platform's idea of the current locale is fixed for the life of the process — iOS
 * reads `AppleLanguages` once, and the desktop JVM has no configuration change to recompose on.
 * Android is the one that can do it live, by recreating the activity underneath the new locale. The
 * settings screen draws a line of explanation where this is true and stays quiet where it is not,
 * because a control that appears to do nothing is worse than one that says when it will.
 */
expect val appLanguageAppliesOnRestart: Boolean
