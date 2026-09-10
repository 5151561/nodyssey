package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable
import io.github.nodyssey.core.Site

/**
 * The desktop target is the component gallery, not an app anybody signs into — it has no store to
 * remember a site in and no shell to restart. Switching does nothing rather than half of it.
 *
 * Kept as a real actual rather than left to fail the build, because the gallery's job is to compose
 * every screen: the home bar draws its switcher there like everything else, and a menu that opens
 * and closes is a truer preview than a module that will not link.
 */
@Composable
actual fun rememberSiteSwitch(): (Site) -> Unit = {}

/** Nothing restarts here — see [rememberSiteSwitch]. */
actual val siteSwitchRestartsApp: Boolean = false
