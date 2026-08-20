package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable

/**
 * Whether the system currently lets this app open `nodeseek.com` links, or null where the question
 * does not arise — which includes every platform that has no such setting.
 *
 * Re-read every time the screen comes back to the front: the user leaves for a system page to change
 * it and returns, and nothing tells us that happened.
 *
 * See the Android actual for why this app can never pass App Links verification and the switch has
 * to be thrown by hand.
 */
@Composable
expect fun rememberAppLinkHandlingEnabled(): Boolean?

/**
 * Opens the system page holding that switch, for this app.
 *
 * A no-op where [rememberAppLinkHandlingEnabled] answers null: the settings row that leads here is
 * hidden in that case, so there is nowhere to go and nothing to say about it.
 */
@Composable
expect fun rememberAppLinkSettingsLauncher(): () -> Unit
