package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable

/** No desktop equivalent of App Links, so the settings row stays hidden. */
@Composable
actual fun rememberAppLinkHandlingEnabled(): Boolean? = null

@Composable
actual fun rememberAppLinkSettingsLauncher(): () -> Unit = {}
