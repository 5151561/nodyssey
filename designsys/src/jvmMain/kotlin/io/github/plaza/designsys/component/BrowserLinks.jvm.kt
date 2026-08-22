package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler

/**
 * The platform's own handler, unchanged, and nothing to prefetch through.
 *
 * A desktop has one browser and no notion of "in this task", so there is nothing for
 * [shouldUseCustomTab] to select between — the parameter is accepted and ignored rather than removed,
 * because what it answers is an app setting the caller reads either way. Nor is there anything to
 * warm: the desktop browser is a separate program with no service to speak to, and it is already
 * running.
 */
@Composable
actual fun rememberBrowserLinks(shouldUseCustomTab: (String) -> Boolean): BrowserLinks {
    val uriHandler = LocalUriHandler.current
    return remember(uriHandler) { BrowserLinks(uriHandler = uriHandler, prefetcher = {}) }
}
