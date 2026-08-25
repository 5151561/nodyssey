package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable
import io.github.nodyssey.data.settings.AppLanguage

/**
 * Nothing to provide here.
 *
 * Compose Resources reads the locale through an environment this app cannot replace, and this
 * platform has no equivalent of Android's configuration to invalidate it with — see the expect and
 * the Android actual. The language a composition draws in is the one the process started under, so
 * `ApplyAppLanguage` records the choice and the next launch reads it.
 */
@Composable
actual fun ProvideAppLanguage(
    language: AppLanguage,
    content: @Composable () -> Unit,
) = content()

actual val appLanguageAppliesOnRestart: Boolean = true
