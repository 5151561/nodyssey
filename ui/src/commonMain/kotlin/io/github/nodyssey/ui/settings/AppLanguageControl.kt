package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable
import io.github.nodyssey.data.settings.AppLanguage

/**
 * Tells the *platform* about 语言, for the things Compose does not draw.
 *
 * The screen is not one of them — that is `ProvideAppLanguage`, which answers every
 * `stringResource` out of composition state and needs nothing from here. What is left is everything
 * the platform renders on the app's behalf and reads its own locale for: the notification channel
 * names and bodies `:app` posts out of `res/values-…`, the language a WebView announces,
 * `Accept-Language`, and the separators a grouped number is written with. None of those is on
 * screen, and none of them is worth restarting an activity over — so no actual does.
 *
 * Called from `NodysseyRoot` with the settings SSOT's value, so it runs at every launch and again
 * the moment the setting changes. It is idempotent by contract: an implementation is handed the
 * language on every composition of the root, and applying one that is already in force must do
 * nothing at all.
 *
 * [language] is null while the settings store has not answered yet, and an actual must do nothing
 * at all with that. It is not the same as [AppLanguage.SYSTEM] and must never be read as it: the
 * store is read asynchronously, so the root composes once before it has said anything, and an
 * actual that took that first composition for 跟随系统 would undo a stored choice at every launch.
 */
@Composable
expect fun ApplyAppLanguage(language: AppLanguage?)
