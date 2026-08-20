package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Null, which is the answer the type reserves for "the question does not arise".
 *
 * iOS has Universal Links and no switch for them. Whether this app opens a `nodeseek.com` link is
 * decided by an `apple-app-site-association` file served from nodeseek.com — the same file this app
 * can never have, for the same reason the Android actual explains at length: the domain belongs to
 * the forum. The difference is that iOS gives the user nothing to override it with, so unlike Android
 * there is no row to draw and nothing to send them to.
 */
@Composable
actual fun rememberAppLinkHandlingEnabled(): Boolean? = null

/** A no-op, because the row that would call it is hidden — see [rememberAppLinkHandlingEnabled]. */
@Composable
actual fun rememberAppLinkSettingsLauncher(): () -> Unit = remember { {} }
